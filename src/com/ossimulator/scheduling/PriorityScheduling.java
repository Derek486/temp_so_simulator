package com.ossimulator.scheduling;

import java.util.List;

import com.ossimulator.process.Proceso;

public class PriorityScheduling implements SchedulingAlgorithm {
    @Override
    public Proceso selectNextProcess(List<Proceso> readyQueue) {
        if (readyQueue.isEmpty()) {
            return null;
        }

        Proceso highest = readyQueue.get(0);
        for (Proceso p : readyQueue) {
            if (p.getPriority() < highest.getPriority()) {
                highest = p;
            }
        }
        return highest;
    }

    @Override
    public String getName() {
        return "Priority Scheduling";
    }

    @Override
    public void reset() {
    }
}
