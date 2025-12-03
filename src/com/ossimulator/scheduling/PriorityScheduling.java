package com.ossimulator.scheduling;

import com.ossimulator.process.Proceso;
import java.util.List;

/**
 * PriorityScheduling
 *
 * Selecciona el proceso con la prioridad más alta (valor numérico menor =
 * mayor prioridad). En caso de empate se mantiene el orden de llegada.
 */
public class PriorityScheduling implements SchedulingAlgorithm {

    /**
     * Recorre la ready queue y devuelve el proceso con prioridad mínima.
     *
     * @param readyQueue lista de procesos READY
     * @return proceso con mayor prioridad o {@code null} si la lista está vacía
     */
    @Override
    public Proceso selectNextProcess(List<Proceso> readyQueue) {
        if (readyQueue == null || readyQueue.isEmpty()) {
            return null;
        }

        Proceso highest = readyQueue.get(0);
        for (Proceso p : readyQueue) {
            if (p.getPriority() < highest.getPriority()) {
                highest = p;
            }
        }
        return highest;
    }

    /**
     * Nombre legible del algoritmo.
     *
     * @return nombre del algoritmo
     */
    @Override
    public String getName() {
        return "Priority Scheduling";
    }
}
