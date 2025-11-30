package com.ossimulator.simulator;

import java.util.ArrayList;
import java.util.List;

import com.ossimulator.process.Proceso;

public class SystemMetrics {
    private List<Proceso> completedProcesses;
    private int totalCPUTime;
    private int totalIdleTime;
    private int contextSwitches;

    public SystemMetrics() {
        this.completedProcesses = new ArrayList<>();
        this.totalCPUTime = 0;
        this.totalIdleTime = 0;
        this.contextSwitches = 0;
    }

    public void addCompletedProcess(Proceso process) {
        completedProcesses.add(process);
    }

    public double getAverageWaitingTime() {
        if (completedProcesses.isEmpty()) {
            return 0;
        }
        double totalWait = 0;
        for (Proceso p : completedProcesses) {
            totalWait += p.getWaitingTime();
        }
        return totalWait / completedProcesses.size();
    }

    public double getAverageTurnaroundTime() {
        if (completedProcesses.isEmpty()) {
            return 0;
        }
        double totalTurnaround = 0;
        for (Proceso p : completedProcesses) {
            totalTurnaround += p.getTurnaroundTime();
        }
        return totalTurnaround / completedProcesses.size();
    }

    public double getCPUUtilization() {
        if (totalCPUTime + totalIdleTime == 0) {
            return 0;
        }
        return (double) totalCPUTime / (totalCPUTime + totalIdleTime) * 100;
    }

    public int getContextSwitches() {
        return contextSwitches;
    }

    public void setTotalCPUTime(int time) {
        this.totalCPUTime = time;
    }

    public void setTotalIdleTime(int time) {
        this.totalIdleTime = time;
    }

    public void setContextSwitches(int switches) {
        this.contextSwitches = switches;
    }

    public List<Proceso> getCompletedProcesses() {
        return new ArrayList<>(completedProcesses);
    }
}
