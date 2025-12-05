package com.ossimulator.scheduling;

import com.ossimulator.process.Proceso;
import java.util.List;

/**
 * RoundRobin
 *
 * Implementación mínima de Round-Robin. Este objeto contiene el quantum y
 * devuelve el siguiente proceso en cabeza de la cola; la lógica de rotación
 * (mover al final tras consumir su quantum) debe ser gestionada por el
 * scheduler que use esta política.
 */
public class RoundRobin implements SchedulingAlgorithm {
    private int quantum;

    /**
     * Construye un RoundRobin con el quantum especificado.
     *
     * @param quantum tamaño del quantum en ticks (debe ser >= 1)
     */
    public RoundRobin(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("quantum must be > 0");
        }
        this.quantum = quantum;
    }

    /**
     * Devuelve el quantum configurado.
     *
     * @return quantum en ticks
     */
    public int getQuantum() {
        return quantum;
    }

    /**
     * Establece un nuevo quantum.
     *
     * @param quantum nuevo quantum en ticks
     */
    public void setQuantum(int quantum) {
        if (quantum <= 0) {
            throw new IllegalArgumentException("quantum must be > 0");
        }
        this.quantum = quantum;
    }

    /**
     * Selecciona el primer proceso en la ready queue. La rotación y contabilización
     * del uso de quantum quedan a cargo del scheduler que orquesta la ejecución.
     *
     * @param readyQueue lista de procesos READY
     * @return proceso seleccionado o {@code null} si la lista está vacía
     */
    @Override
    public Proceso selectNextProcess(List<Proceso> readyQueue) {
        if (readyQueue == null || readyQueue.isEmpty()) {
            return null;
        }
        return readyQueue.get(0);
    }

    /**
     * Nombre legible del algoritmo, incluyendo el valor de quantum.
     *
     * @return nombre descriptivo
     */
    @Override
    public String getName() {
        return "Round Robin (Quantum: " + quantum + ")";
    }
}
