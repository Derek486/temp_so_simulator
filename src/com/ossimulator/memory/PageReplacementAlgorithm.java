package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import java.util.Map;

/**
 * Interfaz para algoritmos de reemplazo de páginas.
 *
 * Define hooks para notificaciones de acceso, asignación y liberación, y el
 * método que debe devolver un índice de frame candidato a reemplazar.
 */
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
     *
     * @param frameToProcess estado actual frame->process
     * @param frameToPage    estado actual frame->page
     * @param currentTime    tiempo actual (tick)
     * @return índice del frame a reemplazar o -1 si no puede elegir ninguno
     */
    int selectFrameToReplace(Map<Integer, Proceso> frameToProcess, Map<Integer, Integer> frameToPage, int currentTime);

    /**
     * Hook: notificar que un frame ha sido asignado (frame libre ocupado).
     *
     * @param frame      índice del frame asignado
     * @param process    proceso propietario
     * @param pageNumber número de página asignada
     */
    void frameAllocated(int frame, Proceso process, int pageNumber);

    /**
     * Hook: notificar que un frame ha sido liberado.
     *
     * @param frame índice del frame liberado
     */
    void frameFreed(int frame);

    /**
     * Nombre descriptivo del algoritmo.
     *
     * @return nombre del algoritmo
     */
    String getName();
}
