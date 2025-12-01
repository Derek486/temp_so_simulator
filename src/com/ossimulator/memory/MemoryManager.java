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
    private EventLogger eventLogger;
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
        this.eventLogger = new EventLogger();
        this.processPages = new HashMap<>();
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

    // Método NO bloqueante: intenta asignar todas las páginas del proceso
    // inmediatamente (pero realizará reemplazos si es necesario).
    // Devuelve true si pudo asignarlas; false si no es posible liberar/crear
    // espacio.
    public boolean tryLoadProcessPages(Proceso process) {
        lock.lock();
        try {
            Set<Integer> pages = new HashSet<>();
            for (int i = 0; i < process.getPageCount(); i++) {
                pages.add(i);
            }

            // if we already have enough free frames nothing to do
            // otherwise try evicting until we have enough
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
                eventLogger.log(process.getPid() + " cannot allocate pages now (freeFrames=" + getFreeFrames()
                        + ", needed=" + pages.size() + ")");
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

    /**
     * Intenta expulsar un frame usando el algoritmo; devuelve true si expulsó uno.
     */
    private boolean tryEvictOneFrame() {
        if (frameToProcess.isEmpty())
            return false;
        int frameToReplace = algorithm.selectFrameToReplace(frameToProcess, frameToPage, currentTime);
        if (frameToReplace < 0)
            return false;

        // evict chosen frame
        Proceso victim = frameToProcess.get(frameToReplace);
        Integer victimPage = frameToPage.get(frameToReplace);
        if (victim != null && victimPage != null) {
            // eliminar el page mapping del proceso
            Set<Integer> pages = processPages.get(victim);
            if (pages != null) {
                pages.remove(victimPage);
            }
            frameToProcess.remove(frameToReplace);
            frameToPage.remove(frameToReplace);

            algorithm.frameFreed(frameToReplace);
            totalReplacements++;
            eventLogger.log("Evicted frame " + frameToReplace + " (process="
                    + (victim != null ? victim.getPid() : "null") + ", page=" + victimPage + ")");
            memoryAvailable.signalAll();
            return true;
        } else {
            // si no hay victim válido, limpiar el frame de todas formas
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
            // buscar si esta página ya está cargada en algún frame
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
                    // evict frames until one becomes free
                    boolean freed = tryEvictOneFrame();
                    if (!freed) {
                        // fallback: cannot handle, just return (shouldn't happen)
                        return;
                    }
                }

                // allocate a frame for this page
                int frame = allocatePage(process, pageNumber);
                if (frame >= 0) {
                    pages.add(pageNumber);
                    algorithm.frameAllocated(frame, process, pageNumber);
                }
            } else {
                // page already present: notify algorithm of access
                algorithm.pageAccessed(presentFrame, process, pageNumber, currentTime);
            }

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
                // eliminar marcos ocupados por este proceso
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

    /**
     * Asigna un frame libre a la (process,pageNumber). Devuelve índice del frame
     * asignado o -1 si no hay espacio.
     */
    private int allocatePage(Proceso process, int pageNumber) {
        for (int frame = 0; frame < totalFrames; frame++) {
            if (!frameToProcess.containsKey(frame)) {
                frameToProcess.put(frame, process);
                frameToPage.put(frame, pageNumber);
                // actualizar estructura de algoritmo
                algorithm.frameAllocated(frame, process, pageNumber);
                return frame;
            }
        }
        return -1;
    }

    private void evictPage(int frame) {
        // eliminar el frame especificado si existe
        Proceso p = frameToProcess.remove(frame);
        frameToPage.remove(frame);
        algorithm.frameFreed(frame);
    }
}
