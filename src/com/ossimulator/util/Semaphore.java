package com.ossimulator.util;

/**
 * Semaphore
 *
 * Implementación mínima de semáforo binario/contable basada en wait/notifyAll.
 * Mantiene la API usada en el proyecto: waitSemaphore() y signalSemaphore().
 *
 * Los métodos están sincronizados; waitSemaphore() espera hasta que haya
 * permisos disponibles o hasta que el hilo sea interrumpido.
 */
public class Semaphore {
    private int permits;

    /**
     * Construye un semáforo con un número inicial de permisos.
     *
     * @param initialPermits número inicial de permisos (>= 0)
     */
    public Semaphore(int initialPermits) {
        if (initialPermits < 0) {
            throw new IllegalArgumentException("initialPermits must be >= 0");
        }
        this.permits = initialPermits;
    }

    /**
     * Adquiere un permiso, bloqueando hasta que esté disponible o hasta que el
     * hilo sea interrumpido.
     *
     * @throws InterruptedException si el hilo es interrumpido mientras espera
     */
    public synchronized void waitSemaphore() throws InterruptedException {
        while (permits <= 0) {
            wait();
        }
        permits--;
    }

    /**
     * Libera un permiso y notifica a hilos en espera.
     */
    public synchronized void signalSemaphore() {
        permits++;
        notifyAll();
    }

    /**
     * Devuelve el número actual de permisos (útil para depuración).
     *
     * @return número de permisos disponibles
     */
    public synchronized int getPermits() {
        return permits;
    }

    /**
     * Resetea el contador de permisos a cero.
     * Útil para reiniciar la simulación.
     */
    public synchronized void reset() {
        this.permits = 0;
    }
}
