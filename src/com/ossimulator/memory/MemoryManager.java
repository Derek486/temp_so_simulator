package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import com.ossimulator.simulator.EventLogger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MemoryManager {
    private int totalFrames;
    private Map<Integer, Proceso> frameToProcess;
    private Map<Integer, Integer> frameToPage; // page number stored in that frame
    private Map<Proceso, Set<Integer>> processPages;
    private PageReplacementAlgorithm algorithm;
    private EventLogger eventLogger; // ahora inyectable
    private int totalPageFaults = 0;
    private int totalReplacements = 0;
    private int currentTime = 0;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition memoryAvailable = lock.newCondition();

    public MemoryManager(int totalFrames, PageReplacementAlgorithm algorithm) {
        this.totalFrames = totalFrames;
        this.algorithm = algorithm;
        this.frameToProcess = new HashMap<>();
        this.frameToPage = new HashMap<>();
        // NO crear EventLogger aquí — será inyectado desde OSSimulator
        this.processPages = new HashMap<>();
    }

    // Permitir inyectar el logger del simulador para que todos los logs salgan al
    // mismo sitio
    public void setEventLogger(EventLogger logger) {
        this.eventLogger = logger;
    }

    public boolean loadProcessPages(Proceso process) {
        lock.lock();
        try {
            Set<Integer> pages = new HashSet<>();
            for (int i = 0; i < process.getPageCount(); i++) {
                pages.add(i);
            }

            // si no hay suficientes frames, intentar reemplazos hasta conseguirlos (o
            // esperar)
            while (!canAllocatePages(process, pages)) {
                // intentar liberar por reemplazo
                boolean freed = tryEvictOneFrame();
                if (!freed) {
                    // esperar un poco si no se pudo liberar (por alguna razón)
                    try {
                        memoryAvailable.await(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
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

    public boolean tryLoadProcessPages(Proceso process) {
        lock.lock();
        try {
            Set<Integer> pages = new HashSet<>();
            for (int i = 0; i < process.getPageCount(); i++) {
                pages.add(i);
            }

            int needed = pages.size();
            int free = totalFrames - frameToProcess.size();
            int attempts = 0;
            while (free < needed && attempts < totalFrames) {
                boolean freed = tryEvictOneFrame();
                if (!freed)
                    break;
                free = totalFrames - frameToProcess.size();
                attempts++;
            }

            if ((totalFrames - frameToProcess.size()) < needed) {
                if (eventLogger != null) {
                    eventLogger.log(process.getPid() + " cannot allocate pages now (freeFrames=" + getFreeFrames()
                            + ", needed=" + pages.size() + ")");
                }
                return false;
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

    private boolean tryEvictOneFrame() {
        if (frameToProcess.isEmpty())
            return false;
        int frameToReplace = algorithm.selectFrameToReplace(frameToProcess, frameToPage, currentTime);
        if (frameToReplace < 0)
            return false;

        Proceso victim = frameToProcess.get(frameToReplace);
        Integer victimPage = frameToPage.get(frameToReplace);
        if (victim != null && victimPage != null) {
            Set<Integer> pages = processPages.get(victim);
            if (pages != null)
                pages.remove(victimPage);
            frameToProcess.remove(frameToReplace);
            frameToPage.remove(frameToReplace);

            algorithm.frameFreed(frameToReplace);
            totalReplacements++;
            if (eventLogger != null) {
                eventLogger.log("Evicted frame " + frameToReplace + " (process="
                        + (victim != null ? victim.getPid() : "null") + ", page=" + victimPage + ")");
            }
            memoryAvailable.signalAll();
            return true;
        } else {
            frameToProcess.remove(frameToReplace);
            frameToPage.remove(frameToReplace);
            algorithm.frameFreed(frameToReplace);
            totalReplacements++;
            memoryAvailable.signalAll();
            return true;
        }
    }

    public void accessPage(Proceso process, int pageNumber) {
        lock.lock();
        try {
            if (!processPages.containsKey(process)) {
                processPages.put(process, new HashSet<>());
            }

            Set<Integer> pages = processPages.get(process);
            Integer presentFrame = null;
            for (Map.Entry<Integer, Integer> e : frameToPage.entrySet()) {
                if (e.getValue() == pageNumber && frameToProcess.get(e.getKey()) == process) {
                    presentFrame = e.getKey();
                    break;
                }
            }

            if (presentFrame == null) {
                // page fault
                totalPageFaults++;

                if (frameToProcess.size() >= totalFrames) {
                    boolean freed = tryEvictOneFrame();
                    if (!freed) {
                        // fallback: no se pudo asignar — devolver (no ideal)
                        return;
                    }
                }

                // allocate a frame for this page (allocatePage already calls
                // algorithm.frameAllocated)
                int frame = allocatePage(process, pageNumber);
                if (frame >= 0) {
                    pages.add(pageNumber);
                    // NO llamar algorithm.frameAllocated de nuevo (allocatePage ya lo hizo)
                }
            } else {
                // page already present: notify algorithm of access
                algorithm.pageAccessed(presentFrame, process, pageNumber, currentTime);
            }

            // notificar acceso final (si presentFrame == null la función anterior ya hizo
            // frameAllocated)
            algorithm.pageAccessed(presentFrame == null ? -1 : presentFrame, process, pageNumber, currentTime);
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
                Iterator<Map.Entry<Integer, Proceso>> it = frameToProcess.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, Proceso> entry = it.next();
                    if (entry.getValue() == process) {
                        int frame = entry.getKey();
                        it.remove();
                        frameToPage.remove(frame);
                        algorithm.frameFreed(frame);
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

    private int allocatePage(Proceso process, int pageNumber) {
        for (int frame = 0; frame < totalFrames; frame++) {
            if (!frameToProcess.containsKey(frame)) {
                frameToProcess.put(frame, process);
                frameToPage.put(frame, pageNumber);
                algorithm.frameAllocated(frame, process, pageNumber); // una sola vez aquí
                return frame;
            }
        }
        return -1;
    }

    private void evictPage(int frame) {
        Proceso p = frameToProcess.remove(frame);
        frameToPage.remove(frame);
        algorithm.frameFreed(frame);
    }
}
