package com.ossimulator.scheduling;

import java.util.List;

import com.ossimulator.process.Proceso;

public class FCFS implements SchedulingAlgorithm {
    @Override
    public Proceso selectNextProcess(List<Proceso> readyQueue) {
        if (readyQueue.isEmpty()) {
            return null;
        }
        return readyQueue.get(0);
    }

    @Override
    public String getName() {
        return "FCFS (First Come, First Served)";
    }

    @Override
    public void reset() {
    }
}
