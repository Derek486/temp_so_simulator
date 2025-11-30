package com.ossimulator.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.ossimulator.memory.MemoryManager;
import com.ossimulator.process.Burst;
import com.ossimulator.process.BurstType;
import com.ossimulator.process.ProcessState;
import com.ossimulator.scheduling.RoundRobin;
import com.ossimulator.scheduling.SchedulingAlgorithm;

public class OSSimulator {
    private List<com.ossimulator.process.Proceso> allProcesses;
    private Queue<com.ossimulator.process.Proceso> readyQueue;
    private Queue<com.ossimulator.process.Proceso> ioQueue;
    private com.ossimulator.process.Proceso runningProcess;
    private SchedulingAlgorithm scheduler;
    private MemoryManager memoryManager;
    private EventLogger eventLogger;
    private SystemMetrics metrics;

    private boolean isRunning = false;
    private int currentTime = 0;
    private int contextSwitches = 0;
    private int timeSlice = 0;
    private int timeSliceRemaining = 0;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition simEvent = lock.newCondition();
    private SimulationUpdateListener updateListener;

    public interface SimulationUpdateListener {
        void onUpdate();
        void onComplete();
    }

    public OSSimulator(List<com.ossimulator.process.Proceso> processes, SchedulingAlgorithm scheduler,
                      MemoryManager memoryManager, int quantum) {
        this.allProcesses = new ArrayList<>(processes);
        this.readyQueue = new ConcurrentLinkedQueue<>();
        this.ioQueue = new ConcurrentLinkedQueue<>();
        this.scheduler = scheduler;
        this.memoryManager = memoryManager;
        this.timeSlice = quantum;
        this.eventLogger = new EventLogger();
        this.metrics = new SystemMetrics();
    }

    public void start() {
        isRunning = true;
        Thread simulatorThread = new Thread(this::simulate);
        simulatorThread.start();
    }

    public void stop() {
        isRunning = false;
    }

    private void simulate() {
        eventLogger.log("Simulator started");
        int maxTime = allProcesses.stream()
                .mapToInt(p -> p.getArrivalTime() + p.getTotalCPUTimeNeeded())
                .max()
                .orElse(100);

        for (currentTime = 0; currentTime <= maxTime && isRunning; currentTime++) {
            memoryManager.setCurrentTime(currentTime);

            addArrivingProcesses(currentTime);
            processIOCompletions();
            scheduleNextProcess();
            executeTimeSlice();

            if (updateListener != null) {
                updateListener.onUpdate();
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        finalizeSimulation();
        if (updateListener != null) {
            updateListener.onComplete();
        }
    }

    private void addArrivingProcesses(int time) {
        for (com.ossimulator.process.Proceso p : allProcesses) {
            if (p.getArrivalTime() == time && p.getState() == ProcessState.NEW) {
                p.setState(ProcessState.READY);
                readyQueue.add(p);
                eventLogger.log(p.getPid() + " arrived and moved to Ready queue");
            }
        }
    }

    private void processIOCompletions() {
        List<com.ossimulator.process.Proceso> toMove = new ArrayList<>();
        for (com.ossimulator.process.Proceso p : ioQueue) {
            if (p.getState() == ProcessState.BLOCKED_IO) {
                Burst currentBurst = p.getCurrentBurst();
                if (currentBurst != null && currentBurst.getType() == BurstType.IO) {
                    p.setBurstTimeRemaining(0);
                    if (p.moveToNextBurst()) {
                        p.setState(ProcessState.READY);
                        toMove.add(p);
                        eventLogger.log(p.getPid() + " I/O completed, moved to Ready queue");
                    } else {
                        p.setState(ProcessState.TERMINATED);
                        toMove.add(p);
                        eventLogger.log(p.getPid() + " terminated");
                    }
                }
            }
        }
        ioQueue.removeAll(toMove);
        readyQueue.addAll(toMove);
    }

    private void scheduleNextProcess() {
        if (runningProcess != null && runningProcess.getState() != ProcessState.RUNNING) {
            runningProcess = null;
        }

        if (runningProcess == null && !readyQueue.isEmpty()) {
            runningProcess = scheduler.selectNextProcess(new ArrayList<>(readyQueue));
            if (runningProcess != null) {
                readyQueue.remove(runningProcess);

                if (!memoryManager.loadProcessPages(runningProcess)) {
                    runningProcess.setState(ProcessState.BLOCKED_MEMORY);
                    readyQueue.add(runningProcess);
                    runningProcess = null;
                    return;
                }

                runningProcess.setStartTime(currentTime);
                runningProcess.setState(ProcessState.RUNNING);
                contextSwitches++;
                runningProcess.incrementContextSwitches();

                if (scheduler instanceof RoundRobin) {
                    timeSliceRemaining = timeSlice;
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
            int executeTime = Math.min(1, timeSliceRemaining);
            runningProcess.decrementBurstTime(executeTime);
            timeSliceRemaining -= executeTime;

            if (runningProcess.getBurstTimeRemaining() <= 0) {
                Burst currentBurst = runningProcess.getCurrentBurst();

                if (currentBurst != null && currentBurst.getType() == BurstType.CPU) {
                    if (runningProcess.moveToNextBurst()) {
                        Burst nextBurst = runningProcess.getCurrentBurst();
                        if (nextBurst != null && nextBurst.getType() == BurstType.IO) {
                            runningProcess.setState(ProcessState.BLOCKED_IO);
                            ioQueue.add(runningProcess);
                            eventLogger.log(runningProcess.getPid() + " blocked for I/O");
                            runningProcess = null;
                        }
                    } else {
                        runningProcess.setEndTime(currentTime);
                        runningProcess.setState(ProcessState.TERMINATED);
                        metrics.addCompletedProcess(runningProcess);
                        eventLogger.log(runningProcess.getPid() + " terminated");
                        memoryManager.unloadProcessPages(runningProcess);
                        runningProcess = null;
                    }
                }
            } else if (scheduler instanceof RoundRobin && timeSliceRemaining <= 0) {
                runningProcess.setState(ProcessState.READY);
                readyQueue.add(runningProcess);
                eventLogger.log(runningProcess.getPid() + " quantum expired, moved to Ready queue");
                runningProcess = null;
            }
        }
    }

    private void finalizeSimulation() {
        metrics.setTotalCPUTime(currentTime - metrics.getCompletedProcesses().stream()
                .mapToInt(com.ossimulator.process.Proceso::getWaitingTime).sum());
        metrics.setContextSwitches(contextSwitches);
        eventLogger.log("Simulation complete");
    }

    public int getCurrentTime() {
        return currentTime;
    }

    public com.ossimulator.process.Proceso getRunningProcess() {
        return runningProcess;
    }

    public Queue<com.ossimulator.process.Proceso> getReadyQueue() {
        return readyQueue;
    }

    public Queue<com.ossimulator.process.Proceso> getIOQueue() {
        return ioQueue;
    }

    public List<com.ossimulator.process.Proceso> getAllProcesses() {
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
}
