package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import com.ossimulator.simulator.EventLogger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
 *
 * Mejoras:
 * - AccessEvent incluye 'seq' (secuencia de accesos) además de 'time' para
 * permitir
 * representar una columna por referencia lógica en la UI (hit/miss/evict
 * alineados).
 */
public class MemoryManager {
    private final int totalFrames;
    private final Map<Integer, Proceso> frameToProcess; // frame -> proceso
    private final Map<Integer, Integer> frameToPage; // frame -> pageNumber
    private final Map<Proceso, Set<Integer>> processPages; // proceso -> set(pagenumbers loaded)

    // Preserve terminated frames instead of freeing them immediately
    private boolean preserveFramesOnProcessTermination = false;
    // store frames moved from unloadProcessPages when preserve flag is on
    private final Map<Integer, Proceso> terminatedFrameToProcess = new HashMap<>();
    private final Map<Integer, Integer> terminatedFrameToPage = new HashMap<>();

    private final PageReplacementAlgorithm algorithm;
    private EventLogger eventLogger;

    private int totalPageFaults = 0;
    private int totalReplacements = 0;
    private int currentTime = 0;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition memoryAvailable = lock.newCondition();

    // historial por frame: frameIndex -> lista de AccessEvent (append-only)
    private final Map<Integer, List<AccessEvent>> frameAccessHistory = new HashMap<>();

    // contador de secuencia de accesos: cada llamada a accessPage obtiene un seq
    // único
    private long accessSequence = 0L;

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
        lock.lock();
        try {
            this.currentTime = time;
        } finally {
            lock.unlock();
        }
    }

    // ---------------- AccessEvent y secuencia ----------------

    /**
     * Evento de acceso registrado por frame.
     * - seq: número de secuencia lógico de referencia (1,2,3,...) -> una columna
     * por referencia
     * - time: tick del simulador en el que ocurrió
     * - page: número de página referenciada o -1 si no aplica
     * - hit: true = hit; false = miss/load/evict
     * - note: "load","evict","alloc","force-evict","access", etc.
     */
    public static class AccessEvent {
        public final long seq;
        public final int time;
        public final int page;
        public final boolean hit; // true = hit, false = miss/load/evict
        public final String note; // optional note like "load"/"evict"

        public AccessEvent(long seq, int time, int page, boolean hit, String note) {
            this.seq = seq;
            this.time = time;
            this.page = page;
            this.hit = hit;
            this.note = note;
        }

        @Override
        public String toString() {
            return String.format("[s=%d t=%d p=%d %s%s]", seq, time, page, hit ? "HIT" : "MISS",
                    (note != null ? " " + note : ""));
        }
    }

    // atomically get next sequence (must be called holding lock or synchronized via
    // methods)
    private long nextAccessSeq() {
        return ++accessSequence;
    }

    /**
     * Getter público del máximo seq observado (snapshot thread-safe).
     * Útil para la UI horizontal scale.
     */
    public long getMaxAccessSequence() {
        lock.lock();
        try {
            return this.accessSequence;
        } finally {
            lock.unlock();
        }
    }

    // getter thread-safe para currentTime
    public int getCurrentTime() {
        lock.lock();
        try {
            return this.currentTime;
        } finally {
            lock.unlock();
        }
    }

    // snapshot seguro del historial (deep copy de listas)
    public Map<Integer, List<AccessEvent>> getFrameAccessHistorySnapshot() {
        lock.lock();
        try {
            Map<Integer, List<AccessEvent>> snap = new HashMap<>();
            for (Map.Entry<Integer, List<AccessEvent>> e : frameAccessHistory.entrySet()) {
                snap.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            return snap;
        } finally {
            lock.unlock();
        }
    }

    // helper para registrar eventos (usa lock externo)
    private void recordFrameAccessEvent(int frame, AccessEvent ev) {
        List<AccessEvent> list = frameAccessHistory.computeIfAbsent(frame, k -> new ArrayList<>());
        list.add(ev);
    }

    // ---------------- carga de procesos ----------------

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
                boolean freed = tryEvictOneFrame(); // legacy call — allocates its own seq internally
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
                    int frame = allocatePage(process, page); // legacy allocate (will create its own seq)
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
                                evictFrame(f); // legacy evictFrame uses its own seq
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
     *
     * Importante: cada llamada a accessPage genera UNA seq única; evicts/allocs
     * generados
     * por esta referencia se registran con esa misma seq para alinearse en la UI.
     */
    public void accessPage(Proceso process, int pageNumber) {
        if (process == null)
            return;
        lock.lock();
        try {
            // generar secuencia única para esta referencia
            long seq = nextAccessSeq();

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
                    boolean freed = tryEvictOneFrame(seq); // pass seq to align events
                    if (!freed) {
                        if (eventLogger != null) {
                            eventLogger.log(
                                    "Page fault but cannot free frame for " + process.getPid() + " page " + pageNumber);
                        }
                        return;
                    }
                }

                int frame = allocatePage(process, pageNumber, seq); // allocate with seq
                if (frame >= 0) {
                    processPages.get(process).add(pageNumber);
                    if (eventLogger != null) {
                        eventLogger.log(
                                "Page loaded: proc=" + process.getPid() + " page=" + pageNumber + " -> frame=" + frame);
                    }
                    // registrar evento MISS/LOAD con seq
                    recordFrameAccessEvent(frame, new AccessEvent(seq, currentTime, pageNumber, false, "load"));
                    algorithm.pageAccessed(frame, process, pageNumber, currentTime);
                    notifyUpdate();
                }
            } else {
                // pagina ya presente -> HIT
                algorithm.pageAccessed(presentFrame, process, pageNumber, currentTime);
                recordFrameAccessEvent(presentFrame, new AccessEvent(seq, currentTime, pageNumber, true, "access"));
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
                        Integer pg = frameToPage.get(frame);
                        // Si preservamos, movemos el frame a la tabla 'terminated' en lugar de borrarlo
                        if (preserveFramesOnProcessTermination) {
                            terminatedFrameToProcess.put(frame, entry.getValue());
                            terminatedFrameToPage.put(frame, (pg == null) ? -1 : pg);
                        } else {
                            // liberamos efectivamente
                            algorithm.frameFreed(frame);
                            frameToPage.remove(frame);
                            // registrar con seq (marca que fue liberado por terminación)
                            long seq = nextAccessSeq();
                            recordFrameAccessEvent(frame,
                                    new AccessEvent(seq, currentTime, (pg == null ? -1 : pg), false, "unload"));
                        }
                        it.remove();
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

    /** Devuelve el número total de frames físicos del sistema. */
    public int getTotalFrames() {
        return totalFrames;
    }

    public Map<Integer, Proceso> getFrameStatus() {
        lock.lock();
        try {
            Map<Integer, Proceso> snap = new HashMap<>(frameToProcess);
            for (Map.Entry<Integer, Proceso> e : terminatedFrameToProcess.entrySet()) {
                snap.putIfAbsent(e.getKey(), e.getValue());
            }
            return snap;
        } finally {
            lock.unlock();
        }
    }

    public Map<Integer, Integer> getFrameToPageMap() {
        lock.lock();
        try {
            Map<Integer, Integer> snap = new HashMap<>(frameToPage);
            for (Map.Entry<Integer, Integer> e : terminatedFrameToPage.entrySet()) {
                snap.putIfAbsent(e.getKey(), e.getValue());
            }
            return snap;
        } finally {
            lock.unlock();
        }
    }

    // ------------------ Métodos auxiliares privados ------------------

    /**
     * Evicta un frame elegido por el algoritmo; devuelve true si expulsó uno.
     * Firma legacy (sin seq) — delega a la versión con seq generando su propia seq.
     */
    private boolean tryEvictOneFrame() {
        long seq = nextAccessSeq();
        return tryEvictOneFrame(seq);
    }

    /**
     * Evicta un frame elegido por el algoritmo y registra evento con la seq dada.
     */
    private boolean tryEvictOneFrame(long seq) {
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
            // registrar eviction con seq
            recordFrameAccessEvent(frameToReplace,
                    new AccessEvent(seq, currentTime, victimPage, false, "evict"));
            notifyUpdate();
            memoryAvailable.signalAll();
            return true;
        } else {
            // limpiar el frame de todas formas
            frameToProcess.remove(frameToReplace);
            frameToPage.remove(frameToReplace);
            algorithm.frameFreed(frameToReplace);
            totalReplacements++;
            // registrar eviction sin page
            recordFrameAccessEvent(frameToReplace,
                    new AccessEvent(seq, currentTime, -1, false, "evict"));
            notifyUpdate();
            memoryAvailable.signalAll();
            return true;
        }
    }

    /**
     * Asigna el primer frame libre al (process,pageNumber), notifica al algoritmo.
     * Firma legacy (sin seq) — delega a la versión con seq generando su propia seq.
     */
    private int allocatePage(Proceso process, int pageNumber) {
        long seq = nextAccessSeq();
        return allocatePage(process, pageNumber, seq);
    }

    /**
     * Asigna el primer frame libre al (process,pageNumber), notifica al algoritmo
     * y registra el evento con la seq proporcionada.
     */
    private int allocatePage(Proceso process, int pageNumber, long seq) {
        for (int frame = 0; frame < totalFrames; frame++) {
            if (!frameToProcess.containsKey(frame)) {
                frameToProcess.put(frame, process);
                frameToPage.put(frame, pageNumber);
                algorithm.frameAllocated(frame, process, pageNumber);
                recordFrameAccessEvent(frame, new AccessEvent(seq, currentTime, pageNumber, false, "alloc"));
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
     * Esta versión legacy genera su propia seq para registrar el evento.
     */
    private void evictFrame(int frame) {
        long seq = nextAccessSeq();
        evictFrame(frame, seq);
    }

    /**
     * Fuerza la expulsión de un frame concreto y registra con seq.
     */
    private void evictFrame(int frame, long seq) {
        Proceso p = frameToProcess.remove(frame);
        Integer pg = frameToPage.remove(frame);
        algorithm.frameFreed(frame);
        totalReplacements++;
        if (eventLogger != null) {
            eventLogger.log("Force-evicted frame " + frame + " (process=" + (p != null ? p.getPid() : "null") + ")");
        }
        recordFrameAccessEvent(frame, new AccessEvent(seq, currentTime, (pg == null ? -1 : pg), false, "force-evict"));
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

    /**
     * Activar para que los frames de procesos terminados se conserven en el
     * snapshot.
     */
    public void setPreserveFramesOnProcessTermination(boolean preserve) {
        lock.lock();
        try {
            this.preserveFramesOnProcessTermination = preserve;
        } finally {
            lock.unlock();
        }
    }

    /** Comprueba si está activo el modo preserve */
    public boolean isPreserveFramesOnProcessTermination() {
        lock.lock();
        try {
            return this.preserveFramesOnProcessTermination;
        } finally {
            lock.unlock();
        }
    }
}
