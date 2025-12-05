package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * LRU (Least Recently Used) - Algoritmo de reemplazo basado en tiempo de último
 * acceso.
 *
 * Mantiene un mapa frame -> lastAccessTime y elige el frame con el menor tiempo
 * de acceso como candidato a expulsar.
 */
public class LRU implements PageReplacementAlgorithm {
    private final Map<Integer, Integer> frameLastAccessTime;

    /**
     * Construye una instancia de LRU.
     */
    public LRU() {
        this.frameLastAccessTime = new HashMap<>();
    }

    /**
     * Registra el tiempo de acceso del frame.
     *
     * @param frame       índice del frame (puede ser -1 si no está en memoria)
     * @param process     proceso propietario
     * @param pageNumber  número de página
     * @param currentTime tick actual de simulación
     */
    @Override
    public void pageAccessed(int frame, Proceso process, int pageNumber, int currentTime) {
        if (frame >= 0) {
            frameLastAccessTime.put(frame, currentTime);
        }
    }

    /**
     * Selecciona el frame con menor tiempo de último acceso.
     *
     * @param frameToProcess mapa frame->process
     * @param frameToPage    mapa frame->page
     * @param currentTime    tick actual de simulación
     * @return frame elegido o -1 si no hay frames
     */
    @Override
    public int selectFrameToReplace(Map<Integer, Proceso> frameToProcess,
            Map<Integer, Integer> frameToPage, int currentTime) {
        if (frameToProcess.isEmpty())
            return -1;

        int lruFrame = -1;
        int minTime = Integer.MAX_VALUE;
        for (Entry<Integer, Proceso> e : frameToProcess.entrySet()) {
            int f = e.getKey();
            int t = frameLastAccessTime.getOrDefault(f, Integer.MIN_VALUE);
            if (t < minTime) {
                minTime = t;
                lruFrame = f;
            }
        }
        return lruFrame;
    }

    /**
     * Marca un frame recién asignado como 'no accedido aún' para que sea candidato.
     *
     * @param frame      índice del frame asignado
     * @param process    proceso propietario
     * @param pageNumber número de página asignada
     */
    @Override
    public void frameAllocated(int frame, Proceso process, int pageNumber) {
        frameLastAccessTime.put(frame, Integer.MIN_VALUE);
    }

    /**
     * Elimina el frame de la estructura interna cuando se libera.
     *
     * @param frame índice del frame liberado
     */
    @Override
    public void frameFreed(int frame) {
        frameLastAccessTime.remove(frame);
    }

    /**
     * Nombre descriptivo del algoritmo.
     *
     * @return nombre del algoritmo
     */
    @Override
    public String getName() {
        return "LRU (Least Recently Used)";
    }
}
