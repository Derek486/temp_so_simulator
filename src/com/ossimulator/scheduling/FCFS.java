package com.ossimulator.scheduling;

import com.ossimulator.process.Proceso;
import java.util.List;

/**
 * FCFS (First Come, First Served)
 *
 * Selecciona siempre el primer proceso en la cola de listos.
 */
public class FCFS implements SchedulingAlgorithm {

    /**
     * Devuelve el primer proceso de la ready queue.
     *
     * @param readyQueue lista de procesos READY
     * @return primer proceso o {@code null} si la lista está vacía
     */
    @Override
    public Proceso selectNextProcess(List<Proceso> readyQueue) {
        if (readyQueue == null || readyQueue.isEmpty()) {
            return null;
        }
        return readyQueue.get(0);
    }

    /**
     * Nombre legible del algoritmo.
     *
     * @return nombre del algoritmo
     */
    @Override
    public String getName() {
        return "FCFS (First Come, First Served)";
    }
}
