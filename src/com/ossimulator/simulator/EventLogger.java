package com.ossimulator.simulator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class EventLogger {
    private List<String> events;
    private final ReentrantLock lock = new ReentrantLock();
    private List<EventListener> listeners;

    public interface EventListener {
        void eventLogged(String event);
    }

    public EventLogger() {
        this.events = new ArrayList<>();
        this.listeners = new ArrayList<>();
    }

    public void log(String message) {
        lock.lock();
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            String logEntry = "[" + timestamp + "] " + message;
            events.add(logEntry);

            for (EventListener listener : listeners) {
                listener.eventLogged(logEntry);
            }
        } finally {
            lock.unlock();
        }
    }

    public List<String> getEvents() {
        lock.lock();
        try {
            return new ArrayList<>(events);
        } finally {
            lock.unlock();
        }
    }

    public void addListener(EventListener listener) {
        lock.lock();
        try {
            listeners.add(listener);
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            events.clear();
        } finally {
            lock.unlock();
        }
    }
}
