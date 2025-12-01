package com.ossimulator.memory;

import com.ossimulator.process.Proceso;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class FIFO implements PageReplacementAlgorithm {
    private Queue<Integer> frameQueue;

    public FIFO() {
        this.frameQueue = new LinkedList<>();
    }

    @Override
    public void pageAccessed(int frame, Proceso process, int pageNumber, int currentTime) {
        // FIFO no requiere actualizar en access
    }

    @Override
    public int selectFrameToReplace(Map<Integer, Proceso> frameToProcess, Map<Integer, Integer> frameToPage,
            int currentTime) {
        // poll until we find a frame that is currently allocated
        while (!frameQueue.isEmpty()) {
            Integer f = frameQueue.poll();
            if (f != null && frameToProcess.containsKey(f)) {
                return f;
            }
        }
        return -1;
    }

    @Override
    public void frameAllocated(int frame, Proceso process, int pageNumber) {
        frameQueue.offer(frame);
    }

    @Override
    public void frameFreed(int frame) {
        frameQueue.remove(frame);
    }

    @Override
    public String getName() {
        return "FIFO (First In, First Out)";
    }

    @Override
    public void reset() {
        frameQueue.clear();
    }
}
