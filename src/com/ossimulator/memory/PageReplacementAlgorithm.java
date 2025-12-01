package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import java.util.Map;

public interface PageReplacementAlgorithm {
    /**
     * Notifica al algoritmo que una página ha sido accedida.
     * 
     * @param frame       el índice del frame donde está la página (o -1 si aún no
     *                    está en memoria)
     * @param process     proceso propietario
     * @param pageNumber  número de página dentro del proceso
     * @param currentTime tiempo actual (tick)
     */
    void pageAccessed(int frame, Proceso process, int pageNumber, int currentTime);

    /**
     * Selecciona un frame a expulsar (índice de frame).
     * Recibe el estado actual de frames (frame->process y frame->page).
     * Debe devolver -1 si no puede elegir ninguno.
     */
    int selectFrameToReplace(Map<Integer, Proceso> frameToProcess, Map<Integer, Integer> frameToPage, int currentTime);

    /**
     * Hook: notificar que un frame ha sido asignado (frame libre ocupado).
     */
    void frameAllocated(int frame, Proceso process, int pageNumber);

    /**
     * Hook: notificar que un frame ha sido liberado.
     */
    void frameFreed(int frame);

    String getName();

    void reset();
}
