package com.ossimulator.simulator;

import com.ossimulator.process.Proceso;
import java.util.ArrayList;
import java.util.List;

/**
 * SystemMetrics
 *
 * Recolecta métricas básicas de la ejecución: procesos completados, tiempos y
 * cambios de contexto. Proporciona getters para uso en la UI.
 */
public class SystemMetrics {
    private final List<Proceso> completedProcesses;
    private int totalCPUTime;
    private int totalIdleTime;
    private int contextSwitches;

    /**
     * Construye un SystemMetrics vacío.
     */
    public SystemMetrics() {
        this.completedProcesses = new ArrayList<>();
        this.totalCPUTime = 0;
        this.totalIdleTime = 0;
        this.contextSwitches = 0;
    }

    /**
     * Añade un proceso completado a la lista de métricas.
     *
     * @param process proceso completado
     */
    public void addCompletedProcess(Proceso process) {
        completedProcesses.add(process);
    }

    /**
     * Calcula el tiempo medio de espera entre los procesos completados.
     *
     * @return average waiting time o 0 si no hay procesos completados
     */
    public double getAverageWaitingTime() {
        if (completedProcesses.isEmpty()) {
            return 0;
        }
        double totalWait = 0;
        for (Proceso p : completedProcesses) {
            totalWait += p.getWaitingTime();
        }
        return totalWait / completedProcesses.size();
    }

    /**
     * Calcula el turnaround medio entre los procesos completados.
     *
     * @return average turnaround time o 0 si no hay procesos completados
     */
    public double getAverageTurnaroundTime() {
        if (completedProcesses.isEmpty()) {
            return 0;
        }
        double totalTurnaround = 0;
        for (Proceso p : completedProcesses) {
            totalTurnaround += p.getTurnaroundTime();
        }
        return totalTurnaround / completedProcesses.size();
    }

    /**
     * Calcula la utilización de CPU (porcentaje).
     *
     * @return porcentaje de utilización (0-100)
     */
    public double getCPUUtilization() {
        if (totalCPUTime + totalIdleTime == 0) {
            return 0;
        }
        return (double) totalCPUTime / (totalCPUTime + totalIdleTime) * 100;
    }

    /**
     * Devuelve el número de cambios de contexto acumulados.
     *
     * @return context switches
     */
    public int getContextSwitches() {
        return contextSwitches;
    }

    /**
     * Establece el total de tiempo CPU usado (sumado entre procesos).
     *
     * @param time total CPU time
     */
    public void setTotalCPUTime(int time) {
        this.totalCPUTime = time;
    }

    /**
     * Establece el tiempo total en el que la CPU estuvo idle.
     *
     * @param time total idle time
     */
    public void setTotalIdleTime(int time) {
        this.totalIdleTime = time;
    }

    /**
     * Establece el número de cambios de contexto.
     *
     * @param switches cantidad de cambios de contexto
     */
    public void setContextSwitches(int switches) {
        this.contextSwitches = switches;
    }

    /**
     * Devuelve una copia de la lista de procesos completados para uso externo.
     *
     * @return lista de procesos completados
     */
    public List<Proceso> getCompletedProcesses() {
        return new ArrayList<>(completedProcesses);
    }
}
