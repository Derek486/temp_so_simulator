package com.ossimulator.simulator;

import com.ossimulator.memory.MemoryManager;
import com.ossimulator.process.Burst;
import com.ossimulator.process.BurstType;
import com.ossimulator.process.Proceso;
import com.ossimulator.process.ProcessState;
import com.ossimulator.scheduling.RoundRobin;
import com.ossimulator.scheduling.SchedulingAlgorithm;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * OSSimulator (refactor)
 *
 * - Mantiene sólo la coordinación entre módulos (scheduler, memory manager,
 * procesos).
 * - Mejora legibilidad: métodos pequeños, nombres expresivos.
 * - Integración explícita con MemoryManager: intento de asignar páginas al
 * llegar,
 * reintento desde memoryBlockedQueue y verificación antes de ejecutar en CPU.
 */
public class OSSimulator {
    private final List<Proceso> allProcesses;
    private final Queue<Proceso> readyQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Proceso> ioQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Proceso> memoryBlockedQueue = new ConcurrentLinkedQueue<>();

    private final SchedulingAlgorithm scheduler;
    private final MemoryManager memoryManager;
    private final EventLogger eventLogger;
    private final SystemMetrics metrics;

    private volatile boolean isRunning = false;
    private int currentTime = 0;
    private int contextSwitches = 0;
    private final int timeSlice; // quantum
    private int timeSliceRemaining = 0;

    private Proceso runningProcess = null;

    // Lock y condition disponibles si más adelante quieres pausar/reanudar la
    // simulación
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition simEvent = lock.newCondition();

    private SimulationUpdateListener updateListener;

    public interface SimulationUpdateListener {
        void onUpdate();

        void onComplete();
    }

    /**
     * Constructor.
     *
     * @param processes     procesos (la lista se copia internamente)
     * @param scheduler     algoritmo de scheduling
     * @param memoryManager manager de memoria (puede ser null si no usas memoria)
     * @param quantum       quantum para RR (si scheduler es RoundRobin)
     */
    public OSSimulator(List<Proceso> processes,
            SchedulingAlgorithm scheduler,
            MemoryManager memoryManager,
            int quantum) {
        this.allProcesses = new ArrayList<>(processes != null ? processes : new ArrayList<>());
        this.scheduler = scheduler;
        this.memoryManager = memoryManager;
        this.timeSlice = Math.max(1, quantum);
        this.eventLogger = new EventLogger();
        if (this.memoryManager != null) {
            this.memoryManager.setEventLogger(this.eventLogger);
        }
        this.metrics = new SystemMetrics();
    }

    // ---------- Control de simulación ----------
    public void start() {
        if (isRunning)
            return;
        resetProcesses();
        isRunning = true;
        Thread t = new Thread(this::simulate, "OSSimulator-Thread");
        t.start();
    }

    public void stop() {
        isRunning = false;
    }

    // ---------- Main loop (tick-based) ----------
    private void simulate() {
        eventLogger.log("Simulator started");

        while (isRunning) {
            // actualizar tiempo en memory manager (para algoritmos que dependan de time)
            if (memoryManager != null) {
                memoryManager.setCurrentTime(currentTime);
            }

            // 1) Arribos
            handleArrivals();

            // Log diagnóstico por tick
            eventLogger.log(String.format("[T=%d] Ready=%d IO=%d MemBlocked=%d Running=%s",
                    currentTime,
                    readyQueue.size(),
                    ioQueue.size(),
                    memoryBlockedQueue.size(),
                    runningProcess == null ? "idle" : runningProcess.getPid()));

            // 2) Procesar IO y memoria (no consumen CPU)
            handleIoTick();
            handleMemoryBlockedQueue();

            // 3) Scheduler: asignar proceso si CPU libre
            scheduleIfIdle();

            // 4) Ejecutar 1 tick de CPU (si hay running)
            executeCpuTick();

            // Callback UI / listeners
            if (updateListener != null)
                updateListener.onUpdate();

            // 5) Verificar finalización
            if (allProcessesTerminated())
                break;

            // avanzar tiempo
            currentTime++;

            // velocidad de simulación (puedes parametrizar o quitar)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        finalizeSimulation();
        if (updateListener != null)
            updateListener.onComplete();
    }

    // ---------- Helpers / pasos del bucle ----------

    /** Reinicia estado de procesos antes de empezar la simulación. */
    private void resetProcesses() {
        for (Proceso p : allProcesses)
            p.reset();
        readyQueue.clear();
        ioQueue.clear();
        memoryBlockedQueue.clear();
        runningProcess = null;
        currentTime = 0;
        contextSwitches = 0;
        timeSliceRemaining = 0;
    }

    /** Mueve procesos cuya arrivalTime == currentTime al flujo de memoria/ready. */
    private void handleArrivals() {
        for (Proceso p : allProcesses) {
            if (p.getArrivalTime() == currentTime && p.getState() == ProcessState.NEW) {
                // intentar cargar sus páginas inmediatamente si hay MemoryManager
                boolean pagesLoaded = tryLoadPagesOnArrival(p);
                if (pagesLoaded) {
                    p.setState(ProcessState.READY);
                    readyQueue.add(p);
                    eventLogger.log(p.getPid() + " arrived and moved to Ready queue");
                } else {
                    p.setState(ProcessState.BLOCKED_MEMORY);
                    memoryBlockedQueue.add(p);
                    eventLogger.log(p.getPid() + " arrived but memory not available -> MemBlocked");
                }
            }
        }
    }

    /**
     * Intenta cargar las páginas de un proceso en memoria (no bloqueante).
     * Si memoryManager es null devolvemos true (no simulamos VM).
     */
    private boolean tryLoadPagesOnArrival(Proceso p) {
        if (memoryManager == null)
            return true;
        // tryLoadProcessPages es no-bloqueante según tu implementación
        return memoryManager.tryLoadProcessPages(p);
    }

    /**
     * Decrementa 1 tick de E/S para cada proceso en ioQueue y mueve a ready si
     * corresponde.
     */
    private void handleIoTick() {
        List<Proceso> snapshot = new ArrayList<>(ioQueue);
        List<Proceso> toMove = new ArrayList<>();

        for (Proceso p : snapshot) {
            if (p.getState() != ProcessState.BLOCKED_IO) {
                // proceso inconsistente en la cola -> remover
                toMove.add(p);
                continue;
            }

            // E/S consume 1 tick (no CPU)
            p.decrementCurrentBurstTime(1, false);
            eventLogger.log(String.format("%s I/O remaining=%d", p.getPid(), p.getBurstTimeRemaining()));

            if (p.getBurstTimeRemaining() <= 0) {
                // finalizar ráfaga IO
                if (p.moveToNextBurst()) {
                    p.endIoInterval(currentTime);
                    p.setState(ProcessState.READY);
                    toMove.add(p);
                    eventLogger.log(p.getPid() + " I/O completed, moved to Ready queue");
                } else {
                    // terminó el proceso
                    p.endIoInterval(currentTime);
                    p.setState(ProcessState.TERMINATED);
                    p.setEndTime(currentTime);
                    metrics.addCompletedProcess(p);
                    toMove.add(p);
                    eventLogger.log(p.getPid() + " terminated");
                    if (memoryManager != null)
                        memoryManager.unloadProcessPages(p);
                }
            }
        }

        // mover/remover de la cola I/O
        for (Proceso p : toMove) {
            ioQueue.remove(p);
            if (p.getState() == ProcessState.READY && !readyQueue.contains(p)) {
                // Si tiene páginas, deberá estar en ready; si no, scheduleMemoryBlocked lo
                // manejará
                readyQueue.add(p);
            }
        }
    }

    /**
     * Intentamos cargar páginas para procesos bloqueados por memoria.
     * Si lo logramos, pasan a READY.
     */
    private void handleMemoryBlockedQueue() {
        if (memoryManager == null || memoryBlockedQueue.isEmpty())
            return;

        List<Proceso> toReady = new ArrayList<>();
        for (Proceso p : new ArrayList<>(memoryBlockedQueue)) {
            if (memoryManager.tryLoadProcessPages(p)) {
                p.setState(ProcessState.READY);
                toReady.add(p);
                eventLogger.log(p.getPid() + " memory loaded, moved to Ready queue");
            } else {
                // si no se pudo, lo dejamos en memoryBlockedQueue para reintentar luego
                eventLogger.log("[T=" + currentTime + "] " + p.getPid() + " cannot allocate pages now (freeFrames="
                        + memoryManager.getFreeFrames() + ", needed=" + p.getPageCount() + ")");
            }
        }

        // mover a ready y quitar de memoriaBlockedQueue
        for (Proceso p : toReady) {
            memoryBlockedQueue.remove(p);
            if (!readyQueue.contains(p))
                readyQueue.add(p);
        }
    }

    /** Si la CPU está libre, invoca al scheduler para asignar siguiente proceso. */
    private void scheduleIfIdle() {
        if (runningProcess != null && runningProcess.getState() == ProcessState.RUNNING)
            return;
        if (readyQueue.isEmpty())
            return;

        // seleccionamos del readyQueue (pasamos snapshot para preservar orden)
        Proceso candidate = scheduler.selectNextProcess(new ArrayList<>(readyQueue));
        if (candidate == null)
            return;

        // removerlo de ready
        readyQueue.remove(candidate);

        // intentar cargar páginas justo antes de correr (puede haber sido expulsado
        // anteriormente)
        if (memoryManager != null && !memoryManager.tryLoadProcessPages(candidate)) {
            candidate.setState(ProcessState.BLOCKED_MEMORY);
            memoryBlockedQueue.add(candidate);
            eventLogger.log(candidate.getPid() + " blocked by memory while scheduling -> MemBlocked");
            return;
        }

        // asignar CPU
        runningProcess = candidate;
        runningProcess.setStartTimeIfUnset(currentTime);
        runningProcess.setState(ProcessState.RUNNING);
        runningProcess.startCpuInterval(currentTime);
        contextSwitches++;
        runningProcess.incrementContextSwitches();

        // configurar quantum o duración de ráfaga
        if (scheduler instanceof RoundRobin) {
            timeSliceRemaining = Math.max(1, timeSlice);
        } else {
            Burst b = runningProcess.getCurrentBurst();
            timeSliceRemaining = (b != null) ? b.getDuration() : 1;
        }

        eventLogger.log(runningProcess.getPid() + " started running (Burst: " + timeSliceRemaining + " units)");
    }

    /** Ejecuta 1 tick de CPU para runningProcess (si existe). */
    private void executeCpuTick() {
        if (runningProcess == null || runningProcess.getState() != ProcessState.RUNNING)
            return;

        // pre-access: simulamos que cada tick la CPU accede alguna página (si aplica)
        if (runningProcess.getPageCount() > 0 && memoryManager != null) {
            int pageToAccess = runningProcess.getCpuTimeUsed() % runningProcess.getPageCount();
            memoryManager.accessPage(runningProcess, pageToAccess);
        }

        // consumir 1 unidad de CPU
        runningProcess.decrementCurrentBurstTime(1, true);
        runningProcess.addCpuTick();
        timeSliceRemaining = Math.max(0, timeSliceRemaining - 1);

        // ¿terminó la ráfaga CPU?
        if (runningProcess.getBurstTimeRemaining() <= 0) {
            handleCpuBurstCompletion();
            return;
        }

        // quantum expirado (solo RR)
        if (scheduler instanceof RoundRobin && timeSliceRemaining <= 0) {
            preemptForQuantumExpiry();
        }
    }

    /**
     * Maneja la finalización de una ráfaga CPU (transición a IO, siguiente CPU o
     * terminación).
     */
    private void handleCpuBurstCompletion() {
        Burst currentBurst = runningProcess.getCurrentBurst();
        if (currentBurst == null || currentBurst.getType() != BurstType.CPU) {
            // caso extraño: no hay burst CPU actual; cerramos intervalo y liberamos CPU
            runningProcess.endCpuInterval(currentTime);
            runningProcess = null;
            return;
        }

        // avanzamos a la siguiente ráfaga
        boolean hasNext = runningProcess.moveToNextBurst();

        // terminar intervalo CPU (se encarga Proceso de agregar intervalos)
        runningProcess.endCpuInterval(currentTime);

        if (!hasNext) {
            // proceso finalizó
            runningProcess.setEndTime(currentTime);
            runningProcess.closeOpenIntervalsAtTermination(currentTime);
            runningProcess.setState(ProcessState.TERMINATED);
            metrics.addCompletedProcess(runningProcess);
            eventLogger.log(runningProcess.getPid() + " terminated");
            if (memoryManager != null)
                memoryManager.unloadProcessPages(runningProcess);
            runningProcess = null;
            return;
        }

        // si siguiente ráfaga es IO -> mover a IO
        Burst next = runningProcess.getCurrentBurst();
        if (next != null && next.getType() == BurstType.IO) {
            // configurar IO (comienza en next tick para evitar solapamiento con CPU del
            // mismo tick)
            runningProcess.setBurstTimeRemaining(next.getDuration());
            // cerrar CPU ya hecho; iniciar IO intervalo
            runningProcess.startIoInterval(currentTime + 1);
            runningProcess.setState(ProcessState.BLOCKED_IO);
            if (!ioQueue.contains(runningProcess))
                ioQueue.add(runningProcess);
            eventLogger.log(runningProcess.getPid() + " blocked for I/O (duration=" + next.getDuration() + ")");
            runningProcess = null;
            return;
        }

        // siguiente ráfaga es CPU (raro) -> poner en ready
        runningProcess.setState(ProcessState.READY);
        if (!readyQueue.contains(runningProcess))
            readyQueue.add(runningProcess);
        runningProcess = null;
    }

    /** Preempción por expiración de quantum (Round Robin). */
    private void preemptForQuantumExpiry() {
        runningProcess.endCpuInterval(currentTime);
        runningProcess.setState(ProcessState.READY);
        if (!readyQueue.contains(runningProcess))
            readyQueue.add(runningProcess);
        eventLogger.log(runningProcess.getPid() + " quantum expired, moved to Ready queue");
        runningProcess = null;
    }

    /** Cálculos finales y métricas al terminar la simulación. */
    private void finalizeSimulation() {
        int totalCpu = allProcesses.stream().mapToInt(Proceso::getCPUTimeUsed).sum();
        int totalTime = currentTime;
        int totalIdle = Math.max(0, totalTime - totalCpu);
        metrics.setTotalCPUTime(totalCpu);
        metrics.setTotalIdleTime(totalIdle);
        metrics.setContextSwitches(contextSwitches);

        eventLogger.log("Simulation complete");
    }

    // ---------- Getters y setters (API pública) ----------

    public int getCurrentTime() {
        return currentTime;
    }

    public Proceso getRunningProcess() {
        return runningProcess;
    }

    public Queue<Proceso> getReadyQueue() {
        return readyQueue;
    }

    public Queue<Proceso> getIOQueue() {
        return ioQueue;
    }

    public List<Proceso> getAllProcesses() {
        return new ArrayList<>(allProcesses);
    }

    public EventLogger getEventLogger() {
        return eventLogger;
    }

    public SystemMetrics getMetrics() {
        return metrics;
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    public void setUpdateListener(SimulationUpdateListener listener) {
        this.updateListener = listener;
    }

    private boolean allProcessesTerminated() {
        return allProcesses.stream().allMatch(Proceso::isComplete);
    }
}
