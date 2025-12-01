package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * FIFO: mantenemos una cola con la orden de llegada de frames.
 * selectFrameToReplace recorre la cola buscando el primer frame que aún
 * esté asignado (no hace poll indiscriminado para evitar perder referencias).
 */
public class FIFO implements PageReplacementAlgorithm {
    private final Queue<Integer> frameQueue;

    public FIFO() {
        this.frameQueue = new LinkedList<>();
    }

    @Override
    public void pageAccessed(int frame, Proceso process, int pageNumber, int currentTime) {
        // FIFO no necesita actualizar nada en acceso
    }

    @Override
    public int selectFrameToReplace(Map<Integer, Proceso> frameToProcess,
            Map<Integer, Integer> frameToPage, int currentTime) {

        // recorrer la cola hasta encontrar el primer frame todavía asignado
        Iterator<Integer> it = frameQueue.iterator();
        while (it.hasNext()) {
            Integer f = it.next();
            if (f != null && frameToProcess.containsKey(f)) {
                // removemos esa entrada de la cola y la devolvemos
                it.remove();
                return f;
            } else {
                // si el frame ya no está asignado lo quitamos de la cola
                it.remove();
            }
        }
        return -1;
    }

    @Override
    public void frameAllocated(int frame, Proceso process, int pageNumber) {
        // cuando ocupamos un frame lo añadimos al final de la cola FIFO
        frameQueue.offer(frame);
    }

    @Override
    public void frameFreed(int frame) {
        // si se libera un frame lo quitamos de la cola (si está)
        frameQueue.remove(frame);
    }

    @Override
    public String getName() {
        return "FIFO (First In, First Out)";
    }

    @Override
    public void reset() {
        frameQueue.clear();
    }
}
