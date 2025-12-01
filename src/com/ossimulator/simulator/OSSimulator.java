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

public class OSSimulator {
    private List<Proceso> allProcesses;
    private Queue<Proceso> readyQueue;
    private Queue<Proceso> ioQueue;
    private Proceso runningProcess;
    private SchedulingAlgorithm scheduler;
    private MemoryManager memoryManager;
    private EventLogger eventLogger;
    private SystemMetrics metrics;

    private volatile boolean isRunning = false;
    private int currentTime = 0;
    private int contextSwitches = 0;
    private int timeSlice = 0;
    private int timeSliceRemaining = 0;

    private Queue<Proceso> memoryBlockedQueue;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition simEvent = lock.newCondition();
    private SimulationUpdateListener updateListener;

    public interface SimulationUpdateListener {
        void onUpdate();

        void onComplete();
    }

    public OSSimulator(List<Proceso> processes, SchedulingAlgorithm scheduler,
            MemoryManager memoryManager, int quantum) {
        // copia defensiva de la lista de procesos (la clonación de objetos debe hacerse
        // antes)
        this.allProcesses = new ArrayList<>(processes);
        this.readyQueue = new ConcurrentLinkedQueue<>();
        this.ioQueue = new ConcurrentLinkedQueue<>();
        this.memoryBlockedQueue = new ConcurrentLinkedQueue<>();
        this.scheduler = scheduler;
        this.memoryManager = memoryManager;
        this.timeSlice = Math.max(1, quantum);
        this.eventLogger = new EventLogger();
        this.metrics = new SystemMetrics();
    }

    public void start() {
        if (isRunning)
            return;
        isRunning = true;
        // asegurar que procesos comienzan en estado NEW o reiniciados
        for (Proceso p : allProcesses) {
            p.reset();
        }
        Thread simulatorThread = new Thread(this::simulate, "OSSimulator-Thread");
        simulatorThread.start();
    }

    public void stop() {
        isRunning = false;
    }

    private void simulate() {
        eventLogger.log("Simulator started");

        // Run until all processes terminated or stopped by user
        while (isRunning) {
            memoryManager.setCurrentTime(currentTime);

            addArrivingProcesses(currentTime);

            // debug por tick: muestra tamaño de colas para entender por qué se queda
            eventLogger.log(String.format("[T=%d] Ready=%d IO=%d MemBlocked=%d Running=%s",
                    currentTime,
                    readyQueue.size(),
                    ioQueue.size(),
                    memoryBlockedQueue.size(),
                    runningProcess == null ? "idle" : runningProcess.getPid()));

            processIOCompletions(); // decrementa IO restante por tick
            processMemoryCompletions();
            scheduleNextProcess();

            executeTimeSlice();

            if (updateListener != null) {
                updateListener.onUpdate();
            }

            // Si todos los procesos han terminado, salimos
            if (allProcessesTerminated()) {
                break;
            }

            currentTime++;

            try {
                Thread.sleep(100);
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

    private void addArrivingProcesses(int time) {
        for (Proceso p : allProcesses) {
            if (p.getArrivalTime() == time && p.getState() == ProcessState.NEW) {
                p.setState(ProcessState.READY);
                readyQueue.add(p);
                eventLogger.log(p.getPid() + " arrived and moved to Ready queue");
            }
        }
    }

    private void processMemoryCompletions() {
        eventLogger.log("[T=" + currentTime + "] Trying to load pages for memoryBlockedQueue size="
                + memoryBlockedQueue.size());
        List<Proceso> toMove = new ArrayList<>();
        for (Proceso p : memoryBlockedQueue) {
            if (memoryManager.tryLoadProcessPages(p)) {
                p.setState(ProcessState.READY);
                toMove.add(p);
                eventLogger.log(p.getPid() + " memory loaded, moved to Ready queue");
            }
        }
        memoryBlockedQueue.removeAll(toMove);
        readyQueue.addAll(toMove);
    }

    private void processIOCompletions() {
        List<Proceso> toMove = new ArrayList<>();
        // iterar sobre snapshot para evitar ConcurrentModification y procesar cada
        // proceso una vez
        List<Proceso> snapshot = new ArrayList<>(ioQueue);
        for (Proceso p : snapshot) {
            if (p.getState() == ProcessState.BLOCKED_IO) {
                // decrementar 1 unidad de E/S por tick (no contar como CPU)
                p.decrementCurrentBurstTime(1, false);

                eventLogger.log(String.format("%s I/O remaining=%d", p.getPid(), p.getBurstTimeRemaining()));

                if (p.getBurstTimeRemaining() <= 0) {
                    // completar la ráfaga de IO y mover a la siguiente ráfaga o terminar
                    if (p.moveToNextBurst()) {
                        p.endIoInterval(currentTime);
                        p.setState(ProcessState.READY);
                        toMove.add(p);
                        eventLogger.log(p.getPid() + " I/O completed, moved to Ready queue");
                    } else {
                        p.endIoInterval(currentTime);
                        p.setState(ProcessState.TERMINATED);
                        p.setEndTime(currentTime);
                        metrics.addCompletedProcess(p);
                        toMove.add(p);
                        eventLogger.log(p.getPid() + " terminated");
                        memoryManager.unloadProcessPages(p);
                    }
                }
            } else {
                // si por alguna razón el proceso en la cola no está en BLOCKED_IO, lo removemos
                toMove.add(p);
            }
        }
        // eliminar los movidos de la cola de IO y añadir a ready (evitar duplicados)
        for (Proceso p : toMove) {
            ioQueue.remove(p);
            if (p.getState() == ProcessState.READY && !readyQueue.contains(p)) {
                readyQueue.add(p);
            }
        }
    }

    private void scheduleNextProcess() {
        if (runningProcess != null && runningProcess.getState() != ProcessState.RUNNING) {
            runningProcess = null;
        }

        if (runningProcess == null && !readyQueue.isEmpty()) {
            runningProcess = scheduler.selectNextProcess(new ArrayList<>(readyQueue));
            if (runningProcess != null) {
                readyQueue.remove(runningProcess);

                // Intento no bloqueante de cargar páginas; si falla, lo colocamos en
                // memoryBlockedQueue y seguimos
                if (!memoryManager.tryLoadProcessPages(runningProcess)) {
                    runningProcess.setState(ProcessState.BLOCKED_MEMORY);
                    if (!memoryBlockedQueue.contains(runningProcess)) {
                        memoryBlockedQueue.add(runningProcess);
                    }
                    runningProcess = null;
                    return;
                }

                runningProcess.setStartTime(currentTime);
                runningProcess.setState(ProcessState.RUNNING);
                runningProcess.startCpuInterval(currentTime);
                contextSwitches++;
                runningProcess.incrementContextSwitches();

                if (scheduler instanceof RoundRobin) {
                    timeSliceRemaining = Math.max(1, timeSlice);
                } else {
                    Burst burst = runningProcess.getCurrentBurst();
                    timeSliceRemaining = (burst != null) ? burst.getDuration() : 1;
                }

                eventLogger.log(runningProcess.getPid() + " started running (Burst: " +
                        timeSliceRemaining + " units)");
            }
        }
    }

    private void executeTimeSlice() {
        if (runningProcess != null && runningProcess.getState() == ProcessState.RUNNING) {
            int executeTime = 1;
            if (timeSliceRemaining > 0) {
                executeTime = Math.min(1, timeSliceRemaining);
            }

            // decrementar ráfaga actual contando como CPU
            runningProcess.decrementCurrentBurstTime(executeTime, true);
            timeSliceRemaining -= executeTime;

            if (runningProcess.getBurstTimeRemaining() <= 0) {
                Burst currentBurst = runningProcess.getCurrentBurst();

                if (currentBurst != null && currentBurst.getType() == BurstType.CPU) {
                    if (runningProcess.moveToNextBurst()) {
                        Burst nextBurst = runningProcess.getCurrentBurst();

                        if (nextBurst != null && nextBurst.getType() == BurstType.IO) {

                            // terminar CPU
                            runningProcess.endCpuInterval(currentTime);

                            // configurar I/O correctamente
                            runningProcess.setBurstTimeRemaining(nextBurst.getDuration());
                            runningProcess.startIoInterval(currentTime);
                            runningProcess.setState(ProcessState.BLOCKED_IO);

                            // mover a IO queue SOLO UNA VEZ
                            if (!ioQueue.contains(runningProcess)) {
                                ioQueue.add(runningProcess);
                            }

                            eventLogger.log(runningProcess.getPid() + " blocked for I/O (duration="
                                    + nextBurst.getDuration() + ")");

                            runningProcess = null;
                            return;
                        } else {
                            // siguiente es CPU, solo regresarlo a ready
                            runningProcess.setState(ProcessState.READY);
                            if (!readyQueue.contains(runningProcess)) {
                                readyQueue.add(runningProcess);
                            }
                            runningProcess = null;
                            return;
                        }
                    } else {
                        // terminó completamente
                        runningProcess.setEndTime(currentTime);
                        runningProcess.endCpuInterval(currentTime);
                        runningProcess.closeOpenIntervalsAtTermination(currentTime);
                        runningProcess.setState(ProcessState.TERMINATED);
                        metrics.addCompletedProcess(runningProcess);
                        eventLogger.log(runningProcess.getPid() + " terminated");
                        memoryManager.unloadProcessPages(runningProcess);

                        runningProcess = null;
                        return;
                    }
                }
            } else if (scheduler instanceof RoundRobin && timeSliceRemaining <= 0) {
                runningProcess.endCpuInterval(currentTime);

                runningProcess.setState(ProcessState.READY);
                if (!readyQueue.contains(runningProcess)) {
                    readyQueue.add(runningProcess);
                }
                eventLogger.log(runningProcess.getPid() + " quantum expired, moved to Ready queue");
                runningProcess = null;
            }
        }

    }

    private void finalizeSimulation() {
        metrics.setTotalCPUTime(currentTime);
        metrics.setContextSwitches(contextSwitches);
        eventLogger.log("Simulation complete");
    }

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

    public void setUpdateListener(SimulationUpdateListener listener) {
        this.updateListener = listener;
    }

    private boolean allProcessesTerminated() {
        return allProcesses.stream().allMatch(Proceso::isComplete);
    }
}
