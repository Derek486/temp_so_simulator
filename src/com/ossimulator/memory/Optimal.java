package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import java.util.Map;

public class Optimal implements PageReplacementAlgorithm {

    public Optimal(java.util.List<Proceso> allProcesses) {
        // Actualmente no usamos la lista; mantuve la firma para compatibilidad
    }

    @Override
    public void pageAccessed(int frame, Proceso process, int pageNumber, int currentTime) {
        // no-op
    }

    @Override
    public int selectFrameToReplace(Map<Integer, Proceso> frameToProcess, Map<Integer, Integer> frameToPage,
            int currentTime) {
        // Fallback simple: seleccionar el frame con mayor índice (determinista)
        if (frameToProcess.isEmpty())
            return -1;
        int chosen = -1;
        for (int f : frameToProcess.keySet()) {
            if (f > chosen)
                chosen = f;
        }
        return chosen;
    }

    @Override
    public void frameAllocated(int frame, Proceso process, int pageNumber) {
        // no-op
    }

    @Override
    public void frameFreed(int frame) {
        // no-op
    }

    @Override
    public String getName() {
        return "Optimal (heuristic fallback)";
    }

    @Override
    public void reset() {
        // no-op
    }
}
