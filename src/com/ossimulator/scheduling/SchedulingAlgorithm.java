package com.ossimulator.scheduling;

import com.ossimulator.process.Proceso;
import java.util.List;

/**
 * SchedulingAlgorithm
 *
 * Interfaz que deben implementar los algoritmos de planificación. Proporciona
 * un único método para seleccionar el siguiente proceso de la ready queue y
 * un método para devolver un nombre descriptivo del algoritmo.
 */
public interface SchedulingAlgorithm {
    /**
     * Selecciona el siguiente proceso a ejecutar a partir de la cola de listos.
     *
     * @param readyQueue lista de procesos en estado READY (orden que mantiene el
     *                   scheduler)
     * @return proceso seleccionado o {@code null} si la cola está vacía
     */
    Proceso selectNextProcess(List<Proceso> readyQueue);

    /**
     * Devuelve un nombre descriptivo del algoritmo (usado en UI/configuración).
     *
     * @return nombre del algoritmo
     */
    String getName();
}
