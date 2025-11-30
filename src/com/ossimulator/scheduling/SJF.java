package com.ossimulator.scheduling;

import java.util.List;

import com.ossimulator.process.Proceso;

public class SJF implements SchedulingAlgorithm {
    @Override
    public Proceso selectNextProcess(List<Proceso> readyQueue) {
        if (readyQueue.isEmpty()) {
            return null;
        }

        Proceso shortest = readyQueue.get(0);
        for (Proceso p : readyQueue) {
            if (p.getTotalCPUTimeNeeded() < shortest.getTotalCPUTimeNeeded()) {
                shortest = p;
            }
        }
        return shortest;
    }

    @Override
    public String getName() {
        return "SJF (Shortest Job First)";
    }

    @Override
    public void reset() {
    }
}
