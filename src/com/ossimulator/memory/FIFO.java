package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * FIFO (First In, First Out) - Algoritmo simple de reemplazo de páginas.
 *
 * Mantiene una cola con el orden de llegada de frames. Al seleccionar un frame
 * para reemplazar, recorre la cola hasta encontrar el primer frame que aún
 * esté asignado. Esto evita "poll" indiscriminado que podría perder referencias
 * si la cola contiene frames ya liberados.
 */
public class FIFO implements PageReplacementAlgorithm {
    private final Queue<Integer> frameQueue;

    /**
     * Construye una instancia de FIFO.
     */
    public FIFO() {
        this.frameQueue = new LinkedList<>();
    }

    /**
     * Notifica al algoritmo que una página ha sido accedida.
     *
     * @param frame       índice del frame (puede ser -1 si no está en memoria)
     * @param process     proceso propietario
     * @param pageNumber  número de página
     * @param currentTime tick actual de simulación
     */
    @Override
    public void pageAccessed(int frame, Proceso process, int pageNumber, int currentTime) {
        // FIFO no necesita mantener estado por acceso
    }

    /**
     * Selecciona el primer frame candidato almacenado en la cola FIFO que siga
     * asignado en el mapa frameToProcess.
     *
     * @param frameToProcess mapa frame->process
     * @param frameToPage    mapa frame->page
     * @param currentTime    tick actual de simulación
     * @return índice del frame a reemplazar o -1 si no hay ninguno
     */
    @Override
    public int selectFrameToReplace(Map<Integer, Proceso> frameToProcess,
            Map<Integer, Integer> frameToPage, int currentTime) {
        Iterator<Integer> it = frameQueue.iterator();
        while (it.hasNext()) {
            Integer f = it.next();
            if (f != null && frameToProcess.containsKey(f)) {
                it.remove();
                return f;
            } else {
                it.remove();
            }
        }
        return -1;
    }

    /**
     * Notifica al algoritmo que un frame ha sido asignado.
     *
     * @param frame      índice del frame asignado
     * @param process    proceso propietario
     * @param pageNumber número de página asignada
     */
    @Override
    public void frameAllocated(int frame, Proceso process, int pageNumber) {
        frameQueue.offer(frame);
    }

    /**
     * Notifica al algoritmo que un frame ha sido liberado.
     *
     * @param frame índice del frame liberado
     */
    @Override
    public void frameFreed(int frame) {
        frameQueue.remove(frame);
    }

    /**
     * Nombre descriptivo del algoritmo.
     *
     * @return nombre del algoritmo
     */
    @Override
    public String getName() {
        return "FIFO (First In, First Out)";
    }
}
