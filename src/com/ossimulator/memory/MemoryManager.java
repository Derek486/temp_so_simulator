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

/**
 * MemoryManager con notificaciones (MemoryUpdateListener) para UI.
 * - Notifica cada vez que cambia el estado de frames (allocate / evict /
 * unload).
 * - Mantiene mapas frame->process y frame->page.
 * - Provee snapshots thread-safe para la UI.
 */
public class MemoryManager {
    private final int totalFrames;
    private final Map<Integer, Proceso> frameToProcess; // frame -> proceso
    private final Map<Integer, Integer> frameToPage; // frame -> pageNumber
    private final Map<Proceso, Set<Integer>> processPages; // proceso -> set(pagenumbers loaded)

    private final PageReplacementAlgorithm algorithm;
    private EventLogger eventLogger;

    private int totalPageFaults = 0;
    private int totalReplacements = 0;
    private int currentTime = 0;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition memoryAvailable = lock.newCondition();

    public MemoryManager(int totalFrames, PageReplacementAlgorithm algorithm) {
        if (totalFrames <= 0)
            throw new IllegalArgumentException("totalFrames must be > 0");
        this.totalFrames = totalFrames;
        this.algorithm = algorithm;
        this.frameToProcess = new HashMap<>();
        this.frameToPage = new HashMap<>();
        this.processPages = new HashMap<>();
    }

    /** Inyecta el logger del simulador para centralizar trazas. */
    public void setEventLogger(EventLogger logger) {
        this.eventLogger = logger;
    }

    /** Actualiza tiempo (para algoritmos que lo requieran). */
    public void setCurrentTime(int time) {
        this.currentTime = time;
    }

    /**
     * Intenta asignar todas las páginas del proceso de manera no bloqueante.
     * Realiza expulsiones (invocando al algoritmo) si es necesario.
     * Devuelve true si pudo asignarlas todas, false si no hay forma de liberarlas.
     */
    public boolean tryLoadProcessPages(Proceso process) {
        if (process == null)
            return false;
        lock.lock();
        try {
            int needed = process.getPageCount();
            // if process already has pages loaded, treat as success
            Set<Integer> existing = processPages.get(process);
            if (existing != null && existing.size() >= needed) {
                return true;
            }

            int free = totalFrames - frameToProcess.size();
            int attempts = 0;
            // Evict frames until we have enough free frames or we tried enough times
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
                            + ", needed=" + needed + ")");
                }
                return false;
            }

            // allocate frames for all pages (simple: put pages into any free frames)
            Set<Integer> pagesSet = processPages.computeIfAbsent(process, k -> new HashSet<>());
            boolean allocatedAny = false;
            for (int page = 0; page < needed; page++) {
                if (!pagesSet.contains(page)) {
                    int frame = allocatePage(process, page);
                    if (frame >= 0) {
                        pagesSet.add(page);
                        allocatedAny = true;
                        if (eventLogger != null) {
                            eventLogger.log(
                                    "Loaded page for " + process.getPid() + " page=" + page + " -> frame=" + frame);
                        }
                    } else {
                        // shouldn't happen, but if it does, roll-back allocation of pages we added
                        for (Iterator<Integer> it = pagesSet.iterator(); it.hasNext();) {
                            int pnum = it.next();
                            Integer f = findFrameFor(process, pnum);
                            if (f != null) {
                                evictFrame(f);
                            }
                            it.remove();
                        }
                        processPages.remove(process);
                        return false;
                    }
                }
            }

            // notify UI if we changed frames
            if (allocatedAny) {
                notifyUpdate();
            }

            if (eventLogger != null) {
                eventLogger.log("Loaded all pages for " + process.getPid() + " (pages=" + needed + ")");
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Simula un acceso a una página por parte de un proceso.
     * - Si la página está: notifica al algoritmo via pageAccessed.
     * - Si no, realiza page fault, asigna un frame (haciendo evictions si hace
     * falta) y notifica.
     */
    public void accessPage(Proceso process, int pageNumber) {
        if (process == null)
            return;
        lock.lock();
        try {
            // aseguramos estructura del proceso
            processPages.computeIfAbsent(process, k -> new HashSet<>());

            Integer presentFrame = null;
            for (Map.Entry<Integer, Integer> e : frameToPage.entrySet()) {
                int f = e.getKey();
                int pnum = e.getValue();
                if (pnum == pageNumber && frameToProcess.get(f) == process) {
                    presentFrame = f;
                    break;
                }
            }

            if (presentFrame == null) {
                // page fault
                totalPageFaults++;
                if (frameToProcess.size() >= totalFrames) {
                    boolean freed = tryEvictOneFrame();
                    if (!freed) {
                        // no se pudo liberar; en un sistema real habría bloqueo/espera o swap.
                        if (eventLogger != null) {
                            eventLogger.log(
                                    "Page fault but cannot free frame for " + process.getPid() + " page " + pageNumber);
                        }
                        return;
                    }
                }

                int frame = allocatePage(process, pageNumber);
                if (frame >= 0) {
                    processPages.get(process).add(pageNumber);
                    if (eventLogger != null) {
                        eventLogger.log(
                                "Page loaded: proc=" + process.getPid() + " page=" + pageNumber + " -> frame=" + frame);
                    }
                    algorithm.pageAccessed(frame, process, pageNumber, currentTime);
                    notifyUpdate();
                }
            } else {
                // pagina ya presente
                algorithm.pageAccessed(presentFrame, process, pageNumber, currentTime);
            }

            process.setLastAccessTime(currentTime);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Descarga todas las páginas pertenecientes a un proceso (por terminación).
     */
    public void unloadProcessPages(Proceso process) {
        if (process == null)
            return;
        lock.lock();
        try {
            boolean changed = false;
            Set<Integer> pages = processPages.get(process);
            if (pages != null) {
                // eliminar cada frame del proceso
                Iterator<Map.Entry<Integer, Proceso>> it = frameToProcess.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, Proceso> entry = it.next();
                    if (entry.getValue() == process) {
                        int frame = entry.getKey();
                        it.remove();
                        frameToPage.remove(frame);
                        algorithm.frameFreed(frame);
                        changed = true;
                    }
                }
                processPages.remove(process);
            }
            memoryAvailable.signalAll();
            if (eventLogger != null) {
                eventLogger.log("Unloaded pages for " + process.getPid());
            }
            if (changed) {
                notifyUpdate();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Devuelve el número de frames libres actualmente. */
    public int getFreeFrames() {
        lock.lock();
        try {
            return totalFrames - frameToProcess.size();
        } finally {
            lock.unlock();
        }
    }

    /** Estadísticas */
    public int getTotalPageFaults() {
        lock.lock();
        try {
            return totalPageFaults;
        } finally {
            lock.unlock();
        }
    }

    public int getTotalReplacements() {
        lock.lock();
        try {
            return totalReplacements;
        } finally {
            lock.unlock();
        }
    }

    /** Estado de frames (frame -> proceso). Útil para UI. */
    public Map<Integer, Proceso> getFrameStatus() {
        lock.lock();
        try {
            return new HashMap<>(frameToProcess);
        } finally {
            lock.unlock();
        }
    }

    /** Devuelve el número total de frames físicos del sistema. */
    public int getTotalFrames() {
        return totalFrames;
    }

    /**
     * Devuelve un mapa frame -> pageNumber (thread-safe snapshot).
     * Útil para la UI que desea mostrar qué página está almacenada en un frame.
     */
    public Map<Integer, Integer> getFrameToPageMap() {
        lock.lock();
        try {
            return new HashMap<>(frameToPage);
        } finally {
            lock.unlock();
        }
    }

    // ------------------ Métodos auxiliares privados ------------------

    /** Evicta un frame elegido por el algoritmo; devuelve true si expulsó uno. */
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
                eventLogger.log("Evicted frame " + frameToReplace + " (process=" + victim.getPid() + ", page="
                        + victimPage + ")");
            }
            notifyUpdate();
            memoryAvailable.signalAll();
            return true;
        } else {
            // limpiar el frame de todas formas
            frameToProcess.remove(frameToReplace);
            frameToPage.remove(frameToReplace);
            algorithm.frameFreed(frameToReplace);
            totalReplacements++;
            notifyUpdate();
            memoryAvailable.signalAll();
            return true;
        }
    }

    /**
     * Asigna el primer frame libre al (process,pageNumber), notifica al algoritmo.
     */
    private int allocatePage(Proceso process, int pageNumber) {
        for (int frame = 0; frame < totalFrames; frame++) {
            if (!frameToProcess.containsKey(frame)) {
                frameToProcess.put(frame, process);
                frameToPage.put(frame, pageNumber);
                algorithm.frameAllocated(frame, process, pageNumber);
                // notify here (we changed frames)
                notifyUpdate();
                return frame;
            }
        }
        return -1;
    }

    /**
     * Encuentra el frame que contiene (process,pageNumber) si existe, null si no.
     */
    private Integer findFrameFor(Proceso process, int pageNumber) {
        for (Map.Entry<Integer, Integer> e : frameToPage.entrySet()) {
            int f = e.getKey();
            int pnum = e.getValue();
            if (pnum == pageNumber && frameToProcess.get(f) == process) {
                return f;
            }
        }
        return null;
    }

    /**
     * Fuerza la expulsión de un frame concreto (sin notificar página específica).
     */
    private void evictFrame(int frame) {
        Proceso p = frameToProcess.remove(frame);
        frameToPage.remove(frame);
        algorithm.frameFreed(frame);
        totalReplacements++;
        if (eventLogger != null) {
            eventLogger.log("Force-evicted frame " + frame + " (process=" + (p != null ? p.getPid() : "null") + ")");
        }
        notifyUpdate();
    }

    // ---------- Memory update listener support ----------
    public interface MemoryUpdateListener {
        void onMemoryUpdate();
    }

    private volatile MemoryUpdateListener updateListener;

    /**
     * Registrar listener para notificaciones cuando el estado de frames cambia.
     * El listener generalmente debe programar la actualización de UI en el EDT.
     */
    public void setUpdateListener(MemoryUpdateListener listener) {
        this.updateListener = listener;
    }

    private void notifyUpdate() {
        MemoryUpdateListener l = this.updateListener;
        if (l != null) {
            try {
                l.onMemoryUpdate();
            } catch (Throwable t) {
                // proteger al manager frente a listeners problemáticos
                if (eventLogger != null) {
                    eventLogger.log("MemoryManager: update listener threw: " + t.getMessage());
                }
            }
        }
    }
}
