package com.ossimulator.simulator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * EventLogger
 *
 * Registro de eventos thread-safe. Mantiene una lista interna de entradas y
 * permite registrar listeners que serán notificados cada vez que se añada
 * una nueva entrada.
 */
public class EventLogger {
    private final List<String> events;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<EventListener> listeners;

    /**
     * Interfaz usada por listeners que desean recibir notificaciones de nuevos
     * eventos.
     */
    public interface EventListener {
        /**
         * Invocado cuando se registra un nuevo evento.
         *
         * @param event entrada de log ya formateada con timestamp
         */
        void eventLogged(String event);
    }

    /**
     * Construye un EventLogger vacío.
     */
    public EventLogger() {
        this.events = new ArrayList<>();
        this.listeners = new ArrayList<>();
    }

    /**
     * Registra un mensaje con timestamp en la lista interna y notifica listeners.
     *
     * @param message mensaje a registrar
     */
    public void log(String message) {
        lock.lock();
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            String logEntry = "[" + timestamp + "] " + message;
            events.add(logEntry);
            for (EventListener listener : listeners) {
                try {
                    listener.eventLogged(logEntry);
                } catch (Throwable ignored) {
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Registra un listener que será notificado en cada nuevo evento.
     *
     * @param listener listener a añadir
     */
    public void addListener(EventListener listener) {
        lock.lock();
        try {
            listeners.add(listener);
        } finally {
            lock.unlock();
        }
    }
}
