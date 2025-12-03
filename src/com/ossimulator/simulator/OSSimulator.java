package com.ossimulator.simulator;

import com.ossimulator.memory.MemoryManager;
import com.ossimulator.process.Burst;
import com.ossimulator.process.BurstType;
import com.ossimulator.process.Proceso;
import com.ossimulator.process.ProcessState;
import com.ossimulator.scheduling.RoundRobin;
import com.ossimulator.scheduling.SchedulingAlgorithm;
import com.ossimulator.util.Semaphore;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * OSSimulator
 *
 * Simulador orientado a ticks que coordina:
 * - llegada y encolado de procesos,
 * - planificación (vía SchedulingAlgorithm),
 * - ejecución de ráfagas CPU y E/S,
 * - integración con MemoryManager para modelar page faults y cargas.
 *
 * Mejoras aplicadas en esta versión:
 * - tickDelayMillis configurable (velocidad de simulación).
 * - encapsulado el sleep en sleepTick() para manejo claro de interrupciones.
 * - métodos pequeños y con javadoc para facilitar mantenimiento.
 */
public class OSSimulator {
    private final List<Proceso> allProcesses;
    private final Queue<Proceso> readyQueue = new LinkedList<>();
    private final Queue<Proceso> ioQueue = new LinkedList<>();
    private final Queue<Proceso> memoryBlockedQueue = new LinkedList<>();
    private final Queue<Proceso> readyNextTick = new LinkedList<>();

    private final Semaphore mutex;
    private final Semaphore avaliableProcesses;

    private final SchedulingAlgorithm scheduler;
    private final MemoryManager memoryManager;
    private final EventLogger eventLogger;
    private final SystemMetrics metrics;

    private volatile boolean isRunning = false;
    private int currentTime = 0;
    private int contextSwitches = 0;
    private final int timeSlice;
    private int timeSliceRemaining = 0;

    private Proceso runningProcess = null;

    private SimulationUpdateListener updateListener;

    /**
     * Delay en milisegundos entre ticks de simulación. Configurable para acelerar/
     * desacelerar la UI sin tocar la lógica.
     */
    private long tickDelayMillis = 100;

    /**
     * Interfaz para callbacks de actualización de la UI / controlador.
     */
    public interface SimulationUpdateListener {
        void onUpdate();

        void onComplete();
    }

    /**
     * Construye un OSSimulator.
     *
     * @param processes     lista de procesos (se copia internamente)
     * @param scheduler     algoritmo de planificación
     * @param memoryManager gestor de memoria (puede ser null)
     * @param quantum       quantum a usar si el scheduler es RoundRobin
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
        this.mutex = new Semaphore(1);
        this.avaliableProcesses = new Semaphore(0);
    }

    /**
     * Establece la latencia (delay) entre ticks en milisegundos.
     *
     * @param millis milisegundos por tick (>= 0). 0 = ejecución lo más rápido
     *               posible.
     */
    public void setTickDelayMillis(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("tickDelayMillis must be >= 0");
        }
        this.tickDelayMillis = millis;
    }

    /**
     * Arranca la simulación en un hilo separado.
     */
    public void start() {
        if (isRunning) {
            return;
        }
        resetProcesses();
        isRunning = true;
        Thread t = new Thread(this::simulate, "OSSimulator-Thread");
        t.start();
    }

    /**
     * Señala la detención de la simulación.
     */
    public void stop() {
        isRunning = false;
        if (avaliableProcesses != null) {
            avaliableProcesses.signalSemaphore();
        }
    }

    /**
     * Bucle principal de la simulación (tick-based). Se encarga de:
     * - actualizar MemoryManager con el tiempo,
     * - procesar arribos, E/S y memoria bloqueada,
     * - planificar y ejecutar CPU por tick,
     * - notificar listeners/UI.
     *
     * El método sleepTick() maneja las interrupciones de forma explícita para que
     * la simulación termine de forma limpia si el hilo es interrumpido.
     */
    private void simulate() {
        eventLogger.log("Simulator started");
        while (isRunning) {
            try {
                if (memoryManager != null) {
                    memoryManager.setCurrentTime(currentTime);
                }

                handleArrivals();

                mutex.waitSemaphore();
                try {
                    eventLogger.log(String.format("[T=%d] Ready=%d IO=%d MemBlocked=%d Running=%s",
                            currentTime,
                            readyQueue.size(),
                            ioQueue.size(),
                            memoryBlockedQueue.size(),
                            runningProcess == null ? "idle" : runningProcess.getPid()));
                } finally {
                    mutex.signalSemaphore();
                }

                handleIoTick();
                handleMemoryBlockedQueue();
                scheduleIfIdle();
                executeCpuTick();

                flushReadyNextTick();

                if (updateListener != null) {
                    updateListener.onUpdate();
                }

                if (allProcessesTerminated()) {
                    break;
                }

                currentTime++;

                try {
                    sleepTick();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        finalizeSimulation();
        if (updateListener != null) {
            updateListener.onComplete();
        }
    }

    /**
     * Duerme el hilo el número de milisegundos configurado por tickDelayMillis.
     * Si tickDelayMillis == 0 no duerme (ejecución rápida).
     *
     * @throws InterruptedException si el hilo es interrumpido mientras duerme
     */
    private void sleepTick() throws InterruptedException {
        if (tickDelayMillis <= 0) {
            return;
        }
        TimeUnit.MILLISECONDS.sleep(tickDelayMillis);
    }

    /**
     * Reinicia el estado de las estructuras internas y procesos antes de arrancar.
     */
    private void resetProcesses() {
        for (Proceso p : allProcesses) {
            p.reset();
        }
        readyQueue.clear();
        ioQueue.clear();
        memoryBlockedQueue.clear();
        readyNextTick.clear();
        runningProcess = null;
        currentTime = 0;
        contextSwitches = 0;
        timeSliceRemaining = 0;
    }

    /**
     * Maneja arribos de procesos cuyo arrivalTime coincide con currentTime.
     *
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    private void handleArrivals() throws InterruptedException {
        for (Proceso p : allProcesses) {
            if (p.getArrivalTime() == currentTime && p.getState() == ProcessState.NEW) {
                boolean pagesLoaded = tryLoadPagesOnArrival(p);

                mutex.waitSemaphore();
                try {
                    if (pagesLoaded) {
                        p.setState(ProcessState.READY);
                        readyQueue.add(p);
                        eventLogger.log(p.getPid() + " arrived and moved to Ready queue");
                        avaliableProcesses.signalSemaphore();
                    } else {
                        p.setState(ProcessState.BLOCKED_MEMORY);
                        memoryBlockedQueue.add(p);
                        eventLogger.log(p.getPid() + " arrived but memory not available -> MemBlocked");
                    }
                } finally {
                    mutex.signalSemaphore();
                }
            }
        }
    }

    /**
     * Intenta cargar páginas para un proceso al llegar. Si no existe MemoryManager
     * devuelve true.
     *
     * @param p proceso
     * @return true si las páginas fueron cargadas o no se modela memoria
     * @throws InterruptedException si ocurre la espera
     */
    private boolean tryLoadPagesOnArrival(Proceso p) throws InterruptedException {
        if (memoryManager == null) {
            return true;
        }
        return memoryManager.tryLoadProcessPages(p);
    }

    /**
     * Procesa un tick de E/S para todos los procesos en ioQueue.
     *
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    private void handleIoTick() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            if (ioQueue.isEmpty()) {
                return;
            }

            Iterator<Proceso> iterator = ioQueue.iterator();

            while (iterator.hasNext()) {
                Proceso p = iterator.next();

                if (p.getState() != ProcessState.BLOCKED_IO) {
                    iterator.remove();
                    continue;
                }

                p.decrementCurrentBurstTime(1, false);

                if (p.getBurstTimeRemaining() <= 0) {
                    if (p.moveToNextBurst()) {
                        p.endIoInterval(currentTime);
                        p.setState(ProcessState.READY);
                        iterator.remove();
                        if (!readyNextTick.contains(p)) {
                            readyNextTick.add(p);
                        }
                        eventLogger.log(p.getPid() + " I/O completed, scheduled to move to Ready next tick");
                    } else {
                        p.setState(ProcessState.TERMINATED);
                        p.setEndTime(currentTime);
                        metrics.addCompletedProcess(p);
                        iterator.remove();
                        eventLogger.log(p.getPid() + " terminated in IO");
                        if (memoryManager != null) {
                            memoryManager.unloadProcessPages(p);
                        }
                    }
                }
            }
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Reintenta cargar páginas para procesos bloqueados por memoria y mueve los que
     * se carguen a readyQueue.
     *
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    private void handleMemoryBlockedQueue() throws InterruptedException {
        if (memoryManager == null) {
            return;
        }

        mutex.waitSemaphore();
        try {
            if (memoryBlockedQueue.isEmpty()) {
                return;
            }
        } finally {
            mutex.signalSemaphore();
        }

        List<Proceso> toReady = new ArrayList<>();

        for (Proceso p : new ArrayList<>(memoryBlockedQueue)) {
            if (memoryManager.tryLoadProcessPages(p)) {
                p.setState(ProcessState.READY);
                toReady.add(p);
                eventLogger.log(p.getPid() + " memory loaded, moved to Ready queue");
            } else {
                eventLogger.log("[T=" + currentTime + "] " + p.getPid() + " cannot allocate pages now (freeFrames="
                        + memoryManager.getFreeFrames() + ", needed=" + p.getPageCount() + ")");
            }
        }

        if (!toReady.isEmpty()) {
            mutex.waitSemaphore();
            try {
                for (Proceso p : toReady) {
                    if (memoryBlockedQueue.contains(p)) {
                        memoryBlockedQueue.remove(p);
                        if (!readyQueue.contains(p)) {
                            readyQueue.add(p);
                            avaliableProcesses.signalSemaphore();
                        }
                    }
                }
            } finally {
                mutex.signalSemaphore();
            }
        }
    }

    /**
     * Si la CPU está libre, invoca al scheduler para asignar siguiente proceso.
     *
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    private void scheduleIfIdle() throws InterruptedException {
        if (runningProcess != null && runningProcess.getState() == ProcessState.RUNNING) {
            return;
        }

        mutex.waitSemaphore();

        Proceso candidate = null;

        try {
            if (!readyQueue.isEmpty()) {
                candidate = scheduler.selectNextProcess(new ArrayList<>(readyQueue));
                if (candidate != null) {
                    readyQueue.remove(candidate);
                    avaliableProcesses.waitSemaphore();
                }
            }
        } finally {
            mutex.signalSemaphore();
        }

        if (candidate == null) {
            return;
        }

        if (memoryManager != null && !memoryManager.tryLoadProcessPages(candidate)) {
            mutex.waitSemaphore();
            try {
                candidate.setState(ProcessState.BLOCKED_MEMORY);
                memoryBlockedQueue.add(candidate);
                eventLogger.log(candidate.getPid() + " blocked by memory while scheduling -> MemBlocked");
            } finally {
                mutex.signalSemaphore();
            }
            return;
        }

        runningProcess = candidate;
        runningProcess.setStartTimeIfUnset(currentTime);
        runningProcess.setState(ProcessState.RUNNING);
        runningProcess.startCpuInterval(currentTime);
        contextSwitches++;
        runningProcess.incrementContextSwitches();

        if (scheduler instanceof RoundRobin) {
            timeSliceRemaining = Math.max(1, timeSlice);
        } else {
            Burst b = runningProcess.getCurrentBurst();
            timeSliceRemaining = (b != null) ? b.getDuration() : 1;
        }

        eventLogger.log(runningProcess.getPid() + " started running (Burst: " + timeSliceRemaining + " units)");
    }

    /**
     * Ejecuta un tick de CPU para el proceso en ejecución.
     *
     * @throws InterruptedException si la espera del mutex o memoryManager es
     *                              interrumpida
     */
    private void executeCpuTick() throws InterruptedException {
        if (runningProcess == null || runningProcess.getState() != ProcessState.RUNNING) {
            return;
        }

        if (runningProcess.getPageCount() > 0 && memoryManager != null) {
            int pageToAccess = runningProcess.getCpuTimeUsed() % runningProcess.getPageCount();
            memoryManager.accessPage(runningProcess, pageToAccess);
        }

        runningProcess.decrementCurrentBurstTime(1, true);
        runningProcess.addCpuTick();
        timeSliceRemaining = Math.max(0, timeSliceRemaining - 1);

        if (runningProcess.getBurstTimeRemaining() <= 0) {
            handleCpuBurstCompletion();
            return;
        }

        if (scheduler instanceof RoundRobin && timeSliceRemaining <= 0) {
            preemptForQuantumExpiry();
        }
    }

    /**
     * Maneja la finalización de una ráfaga CPU.
     *
     * @throws InterruptedException si la espera del mutex o memoryManager es
     *                              interrumpida
     */
    private void handleCpuBurstCompletion() throws InterruptedException {
        Burst currentBurst = runningProcess.getCurrentBurst();
        if (currentBurst == null || currentBurst.getType() != BurstType.CPU) {
            runningProcess.endCpuInterval(currentTime);
            runningProcess = null;
            return;
        }

        boolean hasNext = runningProcess.moveToNextBurst();
        runningProcess.endCpuInterval(currentTime);

        if (!hasNext) {
            runningProcess.setEndTime(currentTime);
            runningProcess.closeOpenIntervalsAtTermination(currentTime);
            runningProcess.setState(ProcessState.TERMINATED);
            metrics.addCompletedProcess(runningProcess);
            eventLogger.log(runningProcess.getPid() + " terminated");
            if (memoryManager != null) {
                memoryManager.unloadProcessPages(runningProcess);
            }
            runningProcess = null;
            return;
        }

        Burst next = runningProcess.getCurrentBurst();
        if (next != null && next.getType() == BurstType.IO) {
            runningProcess.setBurstTimeRemaining(next.getDuration());
            runningProcess.startIoInterval(currentTime + 1);
            runningProcess.setState(ProcessState.BLOCKED_IO);

            mutex.waitSemaphore();
            try {
                if (!ioQueue.contains(runningProcess)) {
                    ioQueue.add(runningProcess);
                }
            } finally {
                mutex.signalSemaphore();
            }

            eventLogger.log(runningProcess.getPid() + " blocked for I/O (duration=" + next.getDuration() + ")");
            runningProcess = null;
            return;
        }

        runningProcess.setState(ProcessState.READY);
        mutex.waitSemaphore();
        try {
            if (!readyQueue.contains(runningProcess)) {
                readyQueue.add(runningProcess);
                avaliableProcesses.signalSemaphore();
            }
        } finally {
            mutex.signalSemaphore();
        }
        runningProcess = null;
    }

    /**
     * Preempción por expiración de quantum.
     *
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    private void preemptForQuantumExpiry() throws InterruptedException {
        runningProcess.endCpuInterval(currentTime);
        runningProcess.setState(ProcessState.READY);

        mutex.waitSemaphore();
        try {
            if (!readyQueue.contains(runningProcess)) {
                readyQueue.add(runningProcess);
                eventLogger.log(runningProcess.getPid() + " quantum expired, moved to Ready queue");
                avaliableProcesses.signalSemaphore();
            }
        } finally {
            mutex.signalSemaphore();
        }
        runningProcess = null;
    }

    /**
     * Vuelca la cola readyNextTick hacia readyQueue (usada para que los procesos
     * que terminan I/O en t sean elegibles en t+1).
     *
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    private void flushReadyNextTick() throws InterruptedException {
        if (readyNextTick.isEmpty()) {
            return;
        }
        mutex.waitSemaphore();
        try {
            int moved = 0;
            for (Proceso p : new ArrayList<>(readyNextTick)) {
                if (!readyQueue.contains(p)) {
                    readyQueue.add(p);
                    moved++;
                }
            }
            readyNextTick.clear();
            for (int s = 0; s < moved; s++) {
                avaliableProcesses.signalSemaphore();
            }
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Cálculos finales y registro de métricas al terminar la simulación.
     */
    private void finalizeSimulation() {
        int totalCpu = allProcesses.stream().mapToInt(Proceso::getCPUTimeUsed).sum();
        int totalTime = currentTime;
        int totalIdle = Math.max(0, totalTime - totalCpu);
        metrics.setTotalCPUTime(totalCpu);
        metrics.setTotalIdleTime(totalIdle);
        metrics.setContextSwitches(contextSwitches);

        if (memoryManager != null) {
            try {
                eventLogger.log(String.format("Memory stats: PageFaults=%d Replacements=%d FreeFrames=%d",
                        memoryManager.getTotalPageFaults(),
                        memoryManager.getTotalReplacements(),
                        memoryManager.getFreeFrames()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        eventLogger.log("Simulation complete");
    }

    /**
     * Devuelve el tiempo de simulación actual.
     *
     * @return tick actual
     */
    public int getCurrentTime() {
        return currentTime;
    }

    /**
     * Devuelve el proceso actualmente en ejecución (o null).
     *
     * @return proceso en ejecución
     */
    public Proceso getRunningProcess() {
        return runningProcess;
    }

    /**
     * Devuelve la ready queue.
     *
     * @return cola de listos
     */
    public Queue<Proceso> getReadyQueue() {
        return readyQueue;
    }

    /**
     * Devuelve la cola de I/O.
     *
     * @return cola de I/O
     */
    public Queue<Proceso> getIOQueue() {
        return ioQueue;
    }

    /**
     * Devuelve una copia de la lista de todos los procesos.
     *
     * @return lista de procesos
     */
    public List<Proceso> getAllProcesses() {
        return new ArrayList<>(allProcesses);
    }

    /**
     * Devuelve el EventLogger usado internamente.
     *
     * @return EventLogger
     */
    public EventLogger getEventLogger() {
        return eventLogger;
    }

    /**
     * Devuelve las métricas del sistema.
     *
     * @return SystemMetrics
     */
    public SystemMetrics getMetrics() {
        return metrics;
    }

    /**
     * Devuelve el MemoryManager (puede ser null).
     *
     * @return MemoryManager o null
     */
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    /**
     * Registra un listener para recibir actualizaciones del simulador.
     *
     * @param listener implementación de SimulationUpdateListener
     */
    public void setUpdateListener(SimulationUpdateListener listener) {
        this.updateListener = listener;
    }

    /**
     * Comprueba si todos los procesos han terminado.
     *
     * @return true si todos los procesos están en estado TERMINATED
     */
    private boolean allProcessesTerminated() {
        return allProcesses.stream().allMatch(Proceso::isComplete);
    }
}
