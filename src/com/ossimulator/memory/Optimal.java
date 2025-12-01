package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import java.util.Map;

/**
 * Optimal (heuristic fallback).
 * - No intentamos predecir el futuro (requiere traza de acceso).
 * - Comportamiento determinista de fallback: selecciona el frame cuyo índice
 * sea mayor (o el primero disponible) para tener reproducibilidad.
 */
public class Optimal implements PageReplacementAlgorithm {

    public Optimal(java.util.List<Proceso> allProcesses) {
        // firma conservada por compatibilidad
    }

    @Override
    public void pageAccessed(int frame, Proceso process, int pageNumber, int currentTime) {
        // no-op: algoritmo óptimo requiere conocimiento del futuro (no disponible)
    }

    @Override
    public int selectFrameToReplace(Map<Integer, Proceso> frameToProcess, Map<Integer, Integer> frameToPage,
            int currentTime) {
        if (frameToProcess.isEmpty())
            return -1;

        // fallback determinista: elegir el frame con índice máximo
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
