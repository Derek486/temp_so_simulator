package com.ossimulator.memory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.ossimulator.process.Proceso;

public class MemoryManager {
    private int totalFrames;
    private Map<Integer, Proceso> frameToProcess;
    private Map<Proceso, Set<Integer>> processPages;
    private PageReplacementAlgorithm algorithm;
    private int totalPageFaults = 0;
    private int totalReplacements = 0;
    private int currentTime = 0;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition memoryAvailable = lock.newCondition();

    public MemoryManager(int totalFrames, PageReplacementAlgorithm algorithm) {
        this.totalFrames = totalFrames;
        this.algorithm = algorithm;
        this.frameToProcess = new HashMap<>();
        this.processPages = new HashMap<>();
    }

    public boolean loadProcessPages(Proceso process) {
        lock.lock();
        try {
            Set<Integer> pages = new HashSet<>();
            for (int i = 0; i < process.getPageCount(); i++) {
                pages.add(i);
            }

            while (!canAllocatePages(process, pages)) {
                try {
                    memoryAvailable.await(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            for (int page : pages) {
                allocatePage(process, page);
            }

            processPages.put(process, pages);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void accessPage(Proceso process, int pageNumber) {
        lock.lock();
        try {
            if (!processPages.containsKey(process)) {
                processPages.put(process, new HashSet<>());
            }

            Set<Integer> pages = processPages.get(process);
            if (!pages.contains(pageNumber)) {
                totalPageFaults++;

                if (frameToProcess.size() >= totalFrames) {
                    int pageToReplace = algorithm.selectPageToReplace(currentTime);
                    if (pageToReplace != -1) {
                        evictPage(pageToReplace);
                        totalReplacements++;
                    }
                }

                allocatePage(process, pageNumber);
                pages.add(pageNumber);
            }

            algorithm.pageAccessed(process, pageNumber, currentTime);
            process.setLastAccessTime(currentTime);
        } finally {
            lock.unlock();
        }
    }

    public void unloadProcessPages(Proceso process) {
        lock.lock();
        try {
            Set<Integer> pages = processPages.get(process);
            if (pages != null) {
                for (int page : pages) {
                    for (int frame = 0; frame < totalFrames; frame++) {
                        if (frameToProcess.get(frame) == process) {
                            frameToProcess.remove(frame);
                        }
                    }
                }
                processPages.remove(process);
            }
            memoryAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public int getFreeFrames() {
        lock.lock();
        try {
            return totalFrames - frameToProcess.size();
        } finally {
            lock.unlock();
        }
    }

    public int getTotalPageFaults() {
        return totalPageFaults;
    }

    public int getTotalReplacements() {
        return totalReplacements;
    }

    public Map<Integer, Proceso> getFrameStatus() {
        lock.lock();
        try {
            return new HashMap<>(frameToProcess);
        } finally {
            lock.unlock();
        }
    }

    public void setCurrentTime(int time) {
        this.currentTime = time;
    }

    private boolean canAllocatePages(Proceso process, Set<Integer> pages) {
        int requiredFrames = pages.size();
        int usedFrames = frameToProcess.size();
        return (totalFrames - usedFrames) >= requiredFrames;
    }

    private void allocatePage(Proceso process, int pageNumber) {
        for (int frame = 0; frame < totalFrames; frame++) {
            if (!frameToProcess.containsKey(frame)) {
                frameToProcess.put(frame, process);
                return;
            }
        }
    }

    private void evictPage(int page) {
        Iterator<Map.Entry<Integer, Proceso>> iterator = frameToProcess.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Proceso> entry = iterator.next();
            if (entry.getValue() != null) {
                iterator.remove();
                return;
            }
        }
    }
}
