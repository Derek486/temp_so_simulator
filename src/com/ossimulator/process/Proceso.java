package com.ossimulator.process;

import java.util.*;
import com.ossimulator.util.Semaphore;;

/**
 * Proceso
 *
 * Modelo de proceso usado por el simulador. Mantiene:
 * - Identificador (pid) y tiempo de llegada.
 * - Lista de ráfagas (Burst).
 * - Estado de ejecución y métricas (start/end, CPU used, context switches).
 * - Registros de intervalos para Gantt (CPU e I/O).
 *
 * Los getters que exponen colecciones devuelven vistas inmutables para
 * seguridad
 * en lectura desde la UI.
 */
public class Proceso implements Comparable<Proceso> {
    private final String pid;
    private final int arrivalTime;
    private final List<Burst> bursts;
    private final int priority;
    private int pageCount = 4;
    private ProcessState state;
    private int startTime = -1;
    private int endTime = -1;
    private int currentBurstIndex = 0;
    private int burstTimeRemaining = 0;
    private int totalCPUTimeNeeded = 0;
    private int cpuTimeUsed = 0;
    private int contextSwitches = 0;
    private int lastAccessTime = -1;

    private final Semaphore mutex;

    /**
     * Interval
     *
     * Representa un intervalo [start, end) en ticks para Gantt.
     */
    public static class Interval {
        public final int start;
        public final int end;

        /**
         * Construye un intervalo inmutable.
         *
         * @param s inicio (inclusive)
         * @param e fin (exclusive)
         */
        public Interval(int s, int e) {
            this.start = s;
            this.end = e;
        }
    }

    private final List<Interval> cpuIntervals = new LinkedList<>();
    private final List<Interval> ioIntervals = new LinkedList<>();
    private int cpuIntervalStart = -1;
    private int ioIntervalStart = -1;

    /**
     * Construye un proceso.
     *
     * @param pid         identificador
     * @param arrivalTime tiempo de llegada
     * @param bursts      lista de ráfagas (se copia internamente)
     * @param priority    prioridad (menor = más importante si aplica)
     * @param pageCount   número de páginas del proceso
     */
    public Proceso(String pid, int arrivalTime, List<Burst> bursts, int priority, int pageCount) {
        this.pid = pid;
        this.arrivalTime = arrivalTime;
        this.bursts = new ArrayList<>(bursts != null ? bursts : new ArrayList<>());
        this.priority = priority;
        this.pageCount = pageCount;
        this.state = ProcessState.NEW;
        this.mutex = new Semaphore(1);

        for (Burst b : bursts) {
            if (b.getType() == BurstType.CPU) {
                totalCPUTimeNeeded += b.getDuration();
            }
        }

        if (!bursts.isEmpty()) {
            burstTimeRemaining = bursts.get(0).getDuration();
        } else {
            burstTimeRemaining = 0;
        }
    }

    /**
     * Resetea el proceso a su estado inicial (útil para re-ejecutar la simulación).
     */
    public void reset() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            this.state = ProcessState.NEW;
            this.startTime = -1;
            this.endTime = -1;
            this.currentBurstIndex = 0;
            this.cpuTimeUsed = 0;
            this.contextSwitches = 0;
            this.lastAccessTime = -1;
            if (!bursts.isEmpty()) {
                burstTimeRemaining = bursts.get(0).getDuration();
            } else {
                burstTimeRemaining = 0;
            }
            cpuIntervals.clear();
            ioIntervals.clear();
            cpuIntervalStart = -1;
            ioIntervalStart = -1;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Inicia un intervalo de CPU si no hay uno abierto.
     *
     * @param time tick actual
     */
    public void startCpuInterval(int time) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            if (cpuIntervalStart == -1) {
                cpuIntervalStart = time;
            }
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Cierra el intervalo de CPU abierto (si existe) y lo registra.
     *
     * @param time tick actual
     */
    public void endCpuInterval(int time) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            if (cpuIntervalStart != -1) {
                cpuIntervals.add(new Interval(cpuIntervalStart, time + 1));
                cpuIntervalStart = -1;
            }
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Inicia un intervalo de I/O si no hay uno abierto.
     *
     * @param time tick actual
     */
    public void startIoInterval(int time) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            if (ioIntervalStart == -1) {
                ioIntervalStart = time;
            }
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Cierra el intervalo de I/O abierto (si existe) y lo registra.
     *
     * @param time tick actual
     */
    public void endIoInterval(int time) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            if (ioIntervalStart != -1) {
                ioIntervals.add(new Interval(ioIntervalStart, time + 1));
                ioIntervalStart = -1;
            }
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve una vista inmutable de los intervalos de CPU.
     *
     * @return lista inmutable de intervalos de CPU
     */
    public List<Interval> getCpuIntervals() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return new ArrayList<>(cpuIntervals);
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve una vista inmutable de los intervalos de I/O.
     *
     * @return lista inmutable de intervalos de I/O
     */
    public List<Interval> getIoIntervals() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return new ArrayList<>(ioIntervals);
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Cierra intervalos abiertos al terminar el proceso.
     *
     * @param time tick de terminación
     */
    public void closeOpenIntervalsAtTermination(int time) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            if (cpuIntervalStart != -1) {
                cpuIntervals.add(new Interval(cpuIntervalStart, time + 1));
                cpuIntervalStart = -1;
            }
            if (ioIntervalStart != -1) {
                ioIntervals.add(new Interval(ioIntervalStart, time + 1));
                ioIntervalStart = -1;
            }
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Establece startTime la primera vez que se invoca.
     *
     * @param time tick actual
     */
    public void setStartTimeIfUnset(int time) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            if (this.startTime == -1) {
                this.startTime = time;
            }
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Establece el número de páginas del proceso.
     *
     * @param count número de páginas
     */
    public void setPageCount(int count) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            this.pageCount = count;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve el tiempo CPU usado por el proceso.
     *
     * @return ticks de CPU consumidos
     */
    public int getCpuTimeUsed() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return cpuTimeUsed;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Incrementa en uno el contador de CPU usado.
     */
    public void addCpuTick() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            cpuTimeUsed++;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve el identificador del proceso.
     *
     * @return pid
     */
    public String getPid() {
        return pid;
    }

    /**
     * Devuelve el tiempo de llegada.
     *
     * @return arrivalTime
     */
    public int getArrivalTime() {
        return arrivalTime;
    }

    /**
     * Devuelve una copia de la lista de ráfagas.
     *
     * @return lista de ráfagas
     */
    public List<Burst> getBursts() {
        return new ArrayList<>(bursts);
    }

    /**
     * Devuelve la prioridad del proceso.
     *
     * @return prioridad
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Devuelve la cantidad de páginas del proceso.
     *
     * @return número de páginas
     */
    public int getPageCount() {
        return pageCount;
    }

    /**
     * Devuelve el estado actual del proceso de forma thread-safe.
     *
     * @return ProcessState actual
     */
    public ProcessState getState() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return state;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Establece el estado del proceso y notifica a hilos que esperan cambios.
     *
     * @param newState nuevo estado
     */
    public void setState(ProcessState newState) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            this.state = newState;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Establece el tiempo de finalización.
     *
     * @param time tick de finalización
     */
    public void setEndTime(int time) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            this.endTime = time;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve la ráfaga actual o null si no quedan ráfagas.
     *
     * @return Burst actual o null
     */
    public Burst getCurrentBurst() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            if (currentBurstIndex < bursts.size()) {
                return bursts.get(currentBurstIndex);
            }
            return null;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve la duración restante de la ráfaga actual.
     *
     * @return ticks restantes
     */
    public int getBurstTimeRemaining() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return burstTimeRemaining;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Establece la duración restante de la ráfaga actual.
     *
     * @param time ticks restantes
     */
    public void setBurstTimeRemaining(int time) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            this.burstTimeRemaining = time;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Decrementa el tiempo de la ráfaga actual y opcionalmente contabiliza CPU.
     *
     * @param amount cantidad a decrementar
     * @param isCpu  true si el decremento corresponde a CPU
     */
    public void decrementCurrentBurstTime(int amount, boolean isCpu) {
        this.burstTimeRemaining -= amount;
        if (isCpu) {
            this.cpuTimeUsed += amount;
        }
    }

    /**
     * Avanza a la siguiente ráfaga si existe.
     *
     * @return true si hay una siguiente ráfaga; false si ya no quedan ráfagas
     */
    public boolean moveToNextBurst() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            currentBurstIndex++;
            if (currentBurstIndex < bursts.size()) {
                burstTimeRemaining = bursts.get(currentBurstIndex).getDuration();
                return true;
            }
            return false;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve el total de tiempo CPU requerido por las ráfagas del proceso.
     *
     * @return tiempo CPU total necesario
     */
    public int getTotalCPUTimeNeeded() {
        return totalCPUTimeNeeded;
    }

    /**
     * Devuelve el tiempo CPU utilizado.
     *
     * @return tiempo CPU usado
     */
    public int getCPUTimeUsed() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return cpuTimeUsed;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Calcula el tiempo de espera (waiting time) si start y end están disponibles.
     *
     * @return waiting time o 0 si no está calculable
     */
    public int getWaitingTime() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            if (startTime == -1 || endTime == -1) {
                return 0;
            }
            int waiting = startTime;
            return Math.max(0, waiting);
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Calcula el turnaround time si start y end están disponibles.
     *
     * @return turnaround time o 0 si no está calculable
     */
    public int getTurnaroundTime() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            if (startTime == -1 || endTime == -1) {
                return 0;
            }
            int tr = (endTime + 1) - startTime;
            return Math.max(0, tr);
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve el número de cambios de contexto registrados.
     *
     * @return context switches
     */
    public int getContextSwitches() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return contextSwitches;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Incrementa el contador de cambios de contexto.
     */
    public void incrementContextSwitches() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            contextSwitches++;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve el tiempo del último acceso registrado.
     *
     * @return lastAccessTime
     */
    public int getLastAccessTime() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return lastAccessTime;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Establece el tiempo del último acceso.
     *
     * @param time tick del último acceso
     */
    public void setLastAccessTime(int time) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            this.lastAccessTime = time;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Indica si el proceso está en estado TERMINATED.
     *
     * @return true si terminado
     */
    public boolean isComplete() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return state == ProcessState.TERMINATED;
        } finally {
            mutex.signalSemaphore();
        }
    }

    @Override
    public int compareTo(Proceso other) {
        if (this.priority != other.priority) {
            return Integer.compare(this.priority, other.priority);
        }
        return Integer.compare(this.arrivalTime, other.arrivalTime);
    }

    @Override
    public String toString() {
        return pid + " [" + state.getDisplayName() + "]";
    }
}
