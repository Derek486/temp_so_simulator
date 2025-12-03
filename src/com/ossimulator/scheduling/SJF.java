package com.ossimulator.scheduling;

import com.ossimulator.process.Proceso;
import java.util.List;

/**
 * SJF (Shortest Job First)
 *
 * Selecciona el proceso cuyo tiempo CPU total requerido (suma de ráfagas CPU)
 * sea menor. Implementación no-preemptive por simplicidad (la preempción debe
 * manejarla el scheduler si se requiere).
 */
public class SJF implements SchedulingAlgorithm {

    /**
     * Recorre la ready queue y devuelve el proceso con menor total CPU requerido.
     *
     * @param readyQueue lista de procesos READY
     * @return proceso con menor job length o {@code null} si la lista está vacía
     */
    @Override
    public Proceso selectNextProcess(List<Proceso> readyQueue) {
        if (readyQueue == null || readyQueue.isEmpty()) {
            return null;
        }

        Proceso shortest = readyQueue.get(0);
        for (Proceso p : readyQueue) {
            if (p.getTotalCPUTimeNeeded() < shortest.getTotalCPUTimeNeeded()) {
                shortest = p;
            }
        }
        return shortest;
    }

    /**
     * Nombre legible del algoritmo.
     *
     * @return nombre descriptivo
     */
    @Override
    public String getName() {
        return "SJF (Shortest Job First)";
    }
}
