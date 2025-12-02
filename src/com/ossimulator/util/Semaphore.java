package com.ossimulator.util;

public class Semaphore {
    private int permits;

    public Semaphore(int initialPermits) {
        this.permits = initialPermits;
    }

    //method to acquire a permit
    public synchronized void waitSemaphore() throws InterruptedException {
        while (permits <= 0) {
            wait(); //native java method to block a thread
        }
        permits--;
    }

    //method to release a permit
    public synchronized void signalSemaphore() {
        permits++;
        notify(); //native java method to wake up a blocked thread
    }

    //optional method to get current permits
    public synchronized int getPermits() {
        return permits;
    }
}
