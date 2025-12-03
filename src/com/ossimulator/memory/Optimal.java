package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import java.util.Map;

/**
 * Optimal (heuristic fallback).
 *
 * Implementación de soporte que actúa como fallback determinista. No realiza
 * predicción del futuro; en su lugar devuelve un candidato reproducible.
 */
public class Optimal implements PageReplacementAlgorithm {

    /**
     * Constructor con firma compatible; no utiliza la lista de procesos.
     *
     * @param allProcesses lista de procesos (no usada)
     */
    public Optimal(java.util.List<Proceso> allProcesses) {
        // Constructor preservado por compatibilidad
    }

    /**
     * Notificación de acceso de página; no se mantiene estado para esta heurística.
     *
     * @param frame       índice del frame
     * @param process     proceso propietario
     * @param pageNumber  número de página
     * @param currentTime tick actual
     */
    @Override
    public void pageAccessed(int frame, Proceso process, int pageNumber, int currentTime) {
        // No-op
    }

    /**
     * Selecciona el frame con índice máximo como fallback determinista.
     *
     * @param frameToProcess mapa frame->process
     * @param frameToPage    mapa frame->page
     * @param currentTime    tick actual
     * @return índice del frame seleccionado o -1 si no hay frames
     */
    @Override
    public int selectFrameToReplace(Map<Integer, Proceso> frameToProcess, Map<Integer, Integer> frameToPage,
            int currentTime) {
        if (frameToProcess.isEmpty())
            return -1;

        int chosen = -1;
        for (int f : frameToProcess.keySet()) {
            if (f > chosen)
                chosen = f;
        }
        return chosen;
    }

    /**
     * Hook de frame asignado; no se mantiene estado.
     *
     * @param frame      índice del frame asignado
     * @param process    proceso propietario
     * @param pageNumber número de página asignada
     */
    @Override
    public void frameAllocated(int frame, Proceso process, int pageNumber) {
        // No-op
    }

    /**
     * Hook de frame liberado; no se mantiene estado.
     *
     * @param frame índice del frame liberado
     */
    @Override
    public void frameFreed(int frame) {
        // No-op
    }

    /**
     * Nombre descriptivo del algoritmo.
     *
     * @return nombre del algoritmo
     */
    @Override
    public String getName() {
        return "Optimal (heuristic fallback)";
    }
}
