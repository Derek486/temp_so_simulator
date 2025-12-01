package com.ossimulator.process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Proceso implements Comparable<Proceso> {
    private String pid;
    private int arrivalTime;
    private List<Burst> bursts;
    private int priority;
    private int pageCount;
    private ProcessState state;
    private int startTime = -1;
    private int endTime = -1;
    private int currentBurstIndex = 0;
    private int burstTimeRemaining = 0;
    private int totalCPUTimeNeeded = 0;
    private int cpuTimeUsed = 0;
    private int contextSwitches = 0;
    private int lastAccessTime = -1;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition stateChanged = lock.newCondition();

    public static class Interval {
        public final int start;
        public final int end;

        public Interval(int s, int e) {
            this.start = s;
            this.end = e;
        }
    }

    private final List<Interval> cpuIntervals = new LinkedList<>();
    private final List<Interval> ioIntervals = new LinkedList<>();
    private int cpuIntervalStart = -1;
    private int ioIntervalStart = -1;

    public Proceso(String pid, int arrivalTime, List<Burst> bursts, int priority, int pageCount) {
        this.pid = pid;
        this.arrivalTime = arrivalTime;
        this.bursts = new ArrayList<>(bursts);
        this.priority = priority;
        this.pageCount = pageCount;
        this.state = ProcessState.NEW;

        for (Burst b : bursts) {
            if (b.getType() == BurstType.CPU) {
                totalCPUTimeNeeded += b.getDuration();
            }
        }

        if (!bursts.isEmpty()) {
            burstTimeRemaining = bursts.get(0).getDuration();
        }
    }

    public void reset() {
        this.state = ProcessState.NEW;
        this.startTime = -1;
        this.endTime = -1;
        this.currentBurstIndex = 0;
        this.cpuTimeUsed = 0;
        this.contextSwitches = 0;
        this.lastAccessTime = -1;
        if (!bursts.isEmpty()) {
            burstTimeRemaining = bursts.get(0).getDuration();
        } else {
            burstTimeRemaining = 0;
        }
        // limpiar intervalos
        cpuIntervals.clear();
        ioIntervals.clear();
        cpuIntervalStart = -1;
        ioIntervalStart = -1;
    }

    public void startCpuInterval(int time) {
        if (cpuIntervalStart == -1) {
            cpuIntervalStart = time;
        }
    }

    public void endCpuInterval(int time) {
        if (cpuIntervalStart != -1) {
            cpuIntervals.add(new Interval(cpuIntervalStart, time));
            cpuIntervalStart = -1;
        }
    }

    public void startIoInterval(int time) {
        if (ioIntervalStart == -1) {
            ioIntervalStart = time;
        }
    }

    public void endIoInterval(int time) {
        if (ioIntervalStart != -1) {
            ioIntervals.add(new Interval(ioIntervalStart, time));
            ioIntervalStart = -1;
        }
    }

    // getters inmutables para que el UI los lea
    public List<Interval> getCpuIntervals() {
        return Collections.unmodifiableList(cpuIntervals);
    }

    public List<Interval> getIoIntervals() {
        return Collections.unmodifiableList(ioIntervals);
    }

    // si el proceso termina estando en medio de un intervalo, cerrarlo
    public void closeOpenIntervalsAtTermination(int time) {
        if (cpuIntervalStart != -1) {
            endCpuInterval(time);
        }
        if (ioIntervalStart != -1) {
            endIoInterval(time);
        }
    }

    public String getPid() {
        return pid;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public List<Burst> getBursts() {
        return new ArrayList<>(bursts);
    }

    public int getPriority() {
        return priority;
    }

    public int getPageCount() {
        return pageCount;
    }

    public ProcessState getState() {
        lock.lock();
        try {
            return state;
        } finally {
            lock.unlock();
        }
    }

    public void setState(ProcessState newState) {
        lock.lock();
        try {
            this.state = newState;
            stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public int getStartTime() {
        return startTime;
    }

    public void setStartTime(int time) {
        if (this.startTime == -1) {
            this.startTime = time;
        }
    }

    public int getEndTime() {
        return endTime;
    }

    public void setEndTime(int time) {
        this.endTime = time;
    }

    public int getCurrentBurstIndex() {
        return currentBurstIndex;
    }

    public Burst getCurrentBurst() {
        if (currentBurstIndex < bursts.size()) {
            return bursts.get(currentBurstIndex);
        }
        return null;
    }

    public int getBurstTimeRemaining() {
        return burstTimeRemaining;
    }

    public void setBurstTimeRemaining(int time) {
        this.burstTimeRemaining = time;
    }

    // Nuevo: decrementa tiempo de la ráfaga actual, y opcionalmente cuenta como CPU
    public void decrementCurrentBurstTime(int amount, boolean isCpu) {
        this.burstTimeRemaining -= amount;
        if (isCpu) {
            this.cpuTimeUsed += amount;
        }
    }

    public boolean moveToNextBurst() {
        currentBurstIndex++;
        if (currentBurstIndex < bursts.size()) {
            burstTimeRemaining = bursts.get(currentBurstIndex).getDuration();
            return true;
        }
        return false;
    }

    public int getTotalCPUTimeNeeded() {
        return totalCPUTimeNeeded;
    }

    public int getCPUTimeUsed() {
        return cpuTimeUsed;
    }

    public int getWaitingTime() {
        if (startTime == -1 || endTime == -1) {
            return 0;
        }
        return (endTime - startTime) - cpuTimeUsed;
    }

    public int getTurnaroundTime() {
        if (startTime == -1 || endTime == -1) {
            return 0;
        }
        return endTime - startTime;
    }

    public int getContextSwitches() {
        return contextSwitches;
    }

    public void incrementContextSwitches() {
        contextSwitches++;
    }

    public int getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(int time) {
        this.lastAccessTime = time;
    }

    public boolean isComplete() {
        return state == ProcessState.TERMINATED;
    }

    public void waitForStateChange() throws InterruptedException {
        lock.lock();
        try {
            stateChanged.await();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int compareTo(Proceso other) {
        if (this.priority != other.priority) {
            return Integer.compare(this.priority, other.priority);
        }
        return Integer.compare(this.arrivalTime, other.arrivalTime);
    }

    @Override
    public String toString() {
        return pid + " [" + state.getDisplayName() + "]";
    }
}
