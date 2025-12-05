package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import com.ossimulator.simulator.EventLogger;
import com.ossimulator.util.Semaphore;
import java.util.*;

/**
 * MemoryManager
 *
 * Gestor simple de memoria física con notificaciones para UI. Proporciona:
 * - Mapas frame->process y frame->page.
 * - Registro de eventos por frame (AccessEvent) con una secuencia global.
 * - Hooks para algoritmos de reemplazo (PageReplacementAlgorithm).
 * - Métodos thread-safe para snapshot utilizados por la UI.
 *
 * La clase utiliza un Semaphore personalizado para exclusión mutua y mantiene
 * la compatibilidad con la API anterior (métodos lanzan InterruptedException).
 */
public class MemoryManager {
    private final int totalFrames;
    private final Map<Integer, Proceso> frameToProcess;
    private final Map<Integer, Integer> frameToPage;
    private final Map<Proceso, Set<Integer>> processPages;

    private boolean preserveFramesOnProcessTermination = false;
    private final Map<Integer, Proceso> terminatedFrameToProcess = new HashMap<>();
    private final Map<Integer, Integer> terminatedFrameToPage = new HashMap<>();

    private final PageReplacementAlgorithm algorithm;
    private EventLogger eventLogger;

    private int totalPageFaults = 0;
    private int totalReplacements = 0;
    private int currentTime = 0;

    private final Semaphore mutex;

    private final Map<Integer, List<AccessEvent>> frameAccessHistory = new HashMap<>();
    private long accessSequence = 0L;

    /**
     * Constructor.
     *
     * @param totalFrames número total de frames físicos
     * @param algorithm   algoritmo de reemplazo de páginas
     * @throws IllegalArgumentException si totalFrames <= 0
     */
    public MemoryManager(int totalFrames, PageReplacementAlgorithm algorithm) {
        if (totalFrames <= 0)
            throw new IllegalArgumentException("totalFrames must be > 0");
        this.totalFrames = totalFrames;
        this.algorithm = algorithm;
        this.frameToProcess = new HashMap<>();
        this.frameToPage = new HashMap<>();
        this.processPages = new HashMap<>();
        this.mutex = new Semaphore(1);
    }

    /**
     * Inyecta el EventLogger del simulador para centralizar trazas.
     *
     * @param logger instancia de EventLogger (puede ser null)
     */
    public void setEventLogger(EventLogger logger) {
        this.eventLogger = logger;
    }

    /**
     * Actualiza el tiempo actual utilizado por algunos algoritmos.
     *
     * @param time tick de simulación
     * @throws InterruptedException si el hilo es interrumpido esperando el mutex
     */
    public void setCurrentTime(int time) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            this.currentTime = time;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Evento de acceso registrado por frame.
     *
     * Cada AccessEvent contiene:
     * - seq: secuencia lógica de referencia (columna en UI).
     * - time: tick del simulador.
     * - page: número de página o -1.
     * - hit: true para HIT, false para MISS/load/evict.
     * - note: nota opcional ("load","evict","alloc","unload","access", etc.).
     */
    public static class AccessEvent {
        public final long seq;
        public final int time;
        public final int page;
        public final boolean hit;
        public final String note;

        /**
         * Construye un AccessEvent inmutable.
         *
         * @param seq  número de secuencia
         * @param time tick del simulador
         * @param page número de página o -1
         * @param hit  true si fue hit
         * @param note nota opcional
         */
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

    /**
     * Incrementa y devuelve la siguiente secuencia de acceso.
     *
     * @return nuevo valor de secuencia
     */
    private long nextAccessSeq() {
        return ++accessSequence;
    }

    /**
     * Devuelve el máximo seq observado (snapshot thread-safe).
     *
     * @return último valor de secuencia
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public long getMaxAccessSequence() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return this.accessSequence;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve el tiempo actual de simulación (thread-safe).
     *
     * @return tick actual
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public int getCurrentTime() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return this.currentTime;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve un snapshot deep-copy del historial por frame.
     *
     * @return mapa frame -> lista de AccessEvent
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public Map<Integer, List<AccessEvent>> getFrameAccessHistorySnapshot() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            Map<Integer, List<AccessEvent>> snap = new HashMap<>();
            for (Map.Entry<Integer, List<AccessEvent>> e : frameAccessHistory.entrySet()) {
                snap.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            return snap;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Registra un AccessEvent en el historial del frame.
     *
     * @param frame índice del frame
     * @param ev    evento a registrar
     */
    private void recordFrameAccessEvent(int frame, AccessEvent ev) {
        List<AccessEvent> list = frameAccessHistory.computeIfAbsent(frame, k -> new ArrayList<>());
        list.add(ev);
    }

    /**
     * Intenta cargar páginas iniciales del proceso. Devuelve true si pudo cargar
     * la(s) página(s) solicitada(s), false en caso contrario.
     *
     * @param process proceso a cargar
     * @return true si la carga inicial fue exitosa
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public boolean tryLoadProcessPages(Proceso process) throws InterruptedException {
        if (process == null)
            return false;

        mutex.waitSemaphore();
        try {
            Set<Integer> existing = processPages.get(process);
            if (existing != null && existing.isEmpty()) {
                return true;
            }

            int needed = 1;

            if (getFreeFramesInternal() < needed) {
                boolean freed = tryEvictOneFrame();
                if (!freed) {
                    if (eventLogger != null) {
                        eventLogger.log(process.getPid() + " cannot allocate initial page (RAM full)");
                    }
                    return false;
                }
            }

            Set<Integer> pagesSet = processPages.computeIfAbsent(process, k -> new HashSet<>());
            int pageZero = 0;

            int frame = allocatePage(process, pageZero);

            if (frame >= 0) {
                pagesSet.add(pageZero);
                if (eventLogger != null) {
                    eventLogger.log(
                            "Loaded initial page for " + process.getPid() + " page=" + pageZero + " -> frame=" + frame);
                }
                notifyUpdate();
                return true;
            } else {
                return false;
            }
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Simula un acceso a una página por parte de un proceso. Si la página no está
     * presente realiza page fault y asigna frame, realizando expulsiones según sea
     * necesario.
     *
     * @param process    proceso que accede
     * @param pageNumber número de página referenciada
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public void accessPage(Proceso process, int pageNumber) throws InterruptedException {
        if (process == null)
            return;

        mutex.waitSemaphore();
        try {
            long seq = nextAccessSeq();

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
                totalPageFaults++;
                if (frameToProcess.size() >= totalFrames) {
                    boolean freed = tryEvictOneFrame(seq);
                    if (!freed) {
                        if (eventLogger != null) {
                            eventLogger.log(
                                    "Page fault but cannot free frame for " + process.getPid() + " page " + pageNumber);
                        }
                        return;
                    }
                }

                int frame = allocatePage(process, pageNumber, seq);
                if (frame >= 0) {
                    processPages.get(process).add(pageNumber);
                    if (eventLogger != null) {
                        eventLogger.log(
                                "Page loaded: proc=" + process.getPid() + " page=" + pageNumber + " -> frame=" + frame);
                    }
                    recordFrameAccessEvent(frame, new AccessEvent(seq, currentTime, pageNumber, false, "load"));
                    algorithm.pageAccessed(frame, process, pageNumber, currentTime);
                    notifyUpdate();
                }
            } else {
                algorithm.pageAccessed(presentFrame, process, pageNumber, currentTime);
                recordFrameAccessEvent(presentFrame, new AccessEvent(seq, currentTime, pageNumber, true, "access"));
            }
            process.setLastAccessTime(currentTime);
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Descarga todas las páginas de un proceso (por terminación). Si está activo el
     * modo preserve, los frames se registran en tablas 'terminated' en lugar de
     * liberarse.
     *
     * @param process proceso a descargar
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public void unloadProcessPages(Proceso process) throws InterruptedException {
        if (process == null)
            return;

        mutex.waitSemaphore();
        try {
            boolean changed = false;
            Set<Integer> pages = processPages.get(process);
            if (pages != null) {
                Iterator<Map.Entry<Integer, Proceso>> it = frameToProcess.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, Proceso> entry = it.next();
                    if (entry.getValue() == process) {
                        int frame = entry.getKey();
                        Integer pg = frameToPage.get(frame);
                        if (preserveFramesOnProcessTermination) {
                            terminatedFrameToProcess.put(frame, entry.getValue());
                            terminatedFrameToPage.put(frame, (pg == null) ? -1 : pg);
                        } else {
                            algorithm.frameFreed(frame);
                            frameToPage.remove(frame);
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
            if (eventLogger != null) {
                eventLogger.log("Unloaded pages for " + process.getPid());
            }
            if (changed) {
                notifyUpdate();
            }
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve el número de frames libres actualmente (uso interno sin bloqueo).
     *
     * @return número de frames libres
     */
    private int getFreeFramesInternal() {
        return totalFrames - frameToProcess.size();
    }

    /**
     * Devuelve el número de frames libres actualmente (thread-safe).
     *
     * @return número de frames libres
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public int getFreeFrames() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return getFreeFramesInternal();
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve el total de page faults registrados.
     *
     * @return contador de page faults
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public int getTotalPageFaults() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return totalPageFaults;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve el total de reemplazos realizados.
     *
     * @return contador de reemplazos
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public int getTotalReplacements() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return totalReplacements;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve el número total de frames físicos.
     *
     * @return totalFrames
     */
    public int getTotalFrames() {
        return totalFrames;
    }

    /**
     * Devuelve un snapshot del estado frame->process, incluyendo frames terminados
     * si
     * el flag preserve está activo.
     *
     * @return mapa frame->Proceso
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public Map<Integer, Proceso> getFrameStatus() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            Map<Integer, Proceso> snap = new HashMap<>(frameToProcess);
            for (Map.Entry<Integer, Proceso> e : terminatedFrameToProcess.entrySet()) {
                snap.putIfAbsent(e.getKey(), e.getValue());
            }
            return snap;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Devuelve un snapshot del mapeo frame->page, incluyendo frames terminados si
     * el flag preserve está activo.
     *
     * @return mapa frame->pageNumber
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public Map<Integer, Integer> getFrameToPageMap() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            Map<Integer, Integer> snap = new HashMap<>(frameToPage);
            for (Map.Entry<Integer, Integer> e : terminatedFrameToPage.entrySet()) {
                snap.putIfAbsent(e.getKey(), e.getValue());
            }
            return snap;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Evicta un frame elegido por el algoritmo. Genera su propia seq y delega.
     *
     * @return true si expulsó un frame, false en caso contrario
     */
    private boolean tryEvictOneFrame() {
        long seq = nextAccessSeq();
        return tryEvictOneFrame(seq);
    }

    /**
     * Evicta un frame elegido por el algoritmo y registra el evento con la seq
     * dada.
     *
     * @param seq secuencia lógica asociada al evento
     * @return true si expulsó un frame, false en caso contrario
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
        }

        frameToProcess.remove(frameToReplace);
        frameToPage.remove(frameToReplace);

        algorithm.frameFreed(frameToReplace);
        totalReplacements++;

        if (eventLogger != null) {
            String victimPid = (victim != null) ? victim.getPid() : "unknown";
            eventLogger.log("Evicted frame " + frameToReplace + " (process=" + victimPid + ", page="
                    + victimPage + ")");
        }

        recordFrameAccessEvent(frameToReplace,
                new AccessEvent(seq, currentTime, victimPage, false, "evict"));
        notifyUpdate();

        return true;
    }

    /**
     * Asigna el primer frame libre al (process,pageNumber) generando su propia seq.
     *
     * @param process    proceso propietario
     * @param pageNumber número de página
     * @return índice del frame asignado o -1 si no hay libre
     */
    private int allocatePage(Proceso process, int pageNumber) {
        long seq = nextAccessSeq();
        return allocatePage(process, pageNumber, seq);
    }

    /**
     * Asigna el primer frame libre al (process,pageNumber), registra evento con la
     * seq proporcionada.
     *
     * @param process    proceso propietario
     * @param pageNumber número de página
     * @param seq        secuencia lógica a usar para el evento
     * @return índice del frame asignado o -1 si no hay libre
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
     * Listener para actualizaciones de memoria (UI).
     */
    public interface MemoryUpdateListener {
        /**
         * Método invocado cuando cambia el estado de los frames.
         */
        void onMemoryUpdate();
    }

    private volatile MemoryUpdateListener updateListener;

    /**
     * Registra un listener que será notificado cuando el estado de frames cambie.
     *
     * @param listener instancia de MemoryUpdateListener o null para cancelar
     */
    public void setUpdateListener(MemoryUpdateListener listener) {
        this.updateListener = listener;
    }

    /**
     * Notifica al listener registrado (si existe) que hubo un cambio.
     */
    private void notifyUpdate() {
        MemoryUpdateListener l = this.updateListener;
        if (l != null) {
            try {
                l.onMemoryUpdate();
            } catch (Throwable t) {
                if (eventLogger != null) {
                    eventLogger.log("MemoryManager: update listener threw: " + t.getMessage());
                }
            }
        }
    }

    /**
     * Activa o desactiva el modo preserve para frames de procesos terminados.
     *
     * @param preserve true para preservar frames de procesos terminados
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public void setPreserveFramesOnProcessTermination(boolean preserve) throws InterruptedException {
        mutex.waitSemaphore();
        try {
            this.preserveFramesOnProcessTermination = preserve;
        } finally {
            mutex.signalSemaphore();
        }
    }

    /**
     * Comprueba si está activo el modo preserve.
     *
     * @return true si preserve está activo
     * @throws InterruptedException si la espera del mutex es interrumpida
     */
    public boolean isPreserveFramesOnProcessTermination() throws InterruptedException {
        mutex.waitSemaphore();
        try {
            return this.preserveFramesOnProcessTermination;
        } finally {
            mutex.signalSemaphore();
        }
    }
}
