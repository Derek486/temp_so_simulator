package com.ossimulator.memory;

import java.util.LinkedList;
import java.util.Queue;

import com.ossimulator.process.Proceso;

public class FIFO implements PageReplacementAlgorithm {
    private Queue<Integer> pageQueue;

    public FIFO() {
        this.pageQueue = new LinkedList<>();
    }

    @Override
    public void pageAccessed(Proceso process, int pageNumber, int currentTime) {
        if (!pageQueue.contains(pageNumber)) {
            pageQueue.offer(pageNumber);
        }
    }

    @Override
    public int selectPageToReplace(int currentTime) {
        return pageQueue.poll() != null ? pageQueue.peek() : -1;
    }

    @Override
    public String getName() {
        return "FIFO (First In, First Out)";
    }

    @Override
    public void reset() {
        pageQueue.clear();
    }
}
