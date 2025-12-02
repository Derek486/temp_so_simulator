package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * LRU: usamos un mapa frame -> lastAccessTime (basado en currentTime recibido
 * en pageAccessed). Cuando frameAllocated es invocado lo marcamos como 'no
 * accedido aún' (Integer.MIN_VALUE). pageAccessed actualiza el tiempo real.
 */
public class LRU implements PageReplacementAlgorithm {
    private final Map<Integer, Integer> frameLastAccessTime;

    public LRU() {
        this.frameLastAccessTime = new HashMap<>();
    }

    @Override
    public void pageAccessed(int frame, Proceso process, int pageNumber, int currentTime) {
        if (frame >= 0) {
            // registrar el tiempo real de acceso para LRU
            frameLastAccessTime.put(frame, currentTime);
        }
    }

    @Override
    public int selectFrameToReplace(Map<Integer, Proceso> frameToProcess,
            Map<Integer, Integer> frameToPage, int currentTime) {
        if (frameToProcess.isEmpty())
            return -1;

        int lruFrame = -1;
        int minTime = Integer.MAX_VALUE;
        for (Entry<Integer, Proceso> e : frameToProcess.entrySet()) {
            int f = e.getKey();
            int t = frameLastAccessTime.getOrDefault(f, Integer.MIN_VALUE);
            if (t < minTime) {
                minTime = t;
                lruFrame = f;
            }
        }
        return lruFrame;
    }

    @Override
    public void frameAllocated(int frame, Proceso process, int pageNumber) {
        // marcar asignación como 'no accedida aún' para que pueda ser candidata
        frameLastAccessTime.put(frame, Integer.MIN_VALUE);
    }

    @Override
    public void frameFreed(int frame) {
        frameLastAccessTime.remove(frame);
    }

    @Override
    public String getName() {
        return "LRU (Least Recently Used)";
    }

    @Override
    public void reset() {
        System.err.println("🔥 LRU.reset() CALLED!");
        Thread.dumpStack();
        frameLastAccessTime.clear();
    }
}
