package com.ossimulator.scheduling;

import java.util.List;

import com.ossimulator.process.Proceso;

public class RoundRobin implements SchedulingAlgorithm {
    private int quantum;

    public RoundRobin(int quantum) {
        this.quantum = quantum;
    }

    public int getQuantum() {
        return quantum;
    }

    public void setQuantum(int quantum) {
        this.quantum = quantum;
    }

    @Override
    public Proceso selectNextProcess(List<Proceso> readyQueue) {
        if (readyQueue.isEmpty()) {
            return null;
        }
        return readyQueue.get(0);
    }

    @Override
    public String getName() {
        return "Round Robin (Quantum: " + quantum + ")";
    }

    @Override
    public void reset() {
    }
}
