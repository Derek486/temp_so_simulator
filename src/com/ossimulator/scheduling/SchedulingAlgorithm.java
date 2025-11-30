package com.ossimulator.scheduling;

import java.util.List;

import com.ossimulator.process.Proceso;

public interface SchedulingAlgorithm {
    Proceso selectNextProcess(List<Proceso> readyQueue);
    String getName();
    void reset();
}
