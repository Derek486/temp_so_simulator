package com.ossimulator.memory;

import java.util.HashMap;
import java.util.Map;

import com.ossimulator.process.Proceso;

public class LRU implements PageReplacementAlgorithm {
    private Map<Integer, Integer> pageAccessTime;
    private int nextPageToReplace = -1;

    public LRU() {
        this.pageAccessTime = new HashMap<>();
    }

    @Override
    public void pageAccessed(Proceso process, int pageNumber, int currentTime) {
        pageAccessTime.put(pageNumber, currentTime);
    }

    @Override
    public int selectPageToReplace(int currentTime) {
        if (pageAccessTime.isEmpty()) {
            return -1;
        }

        int lruPage = -1;
        int minAccessTime = Integer.MAX_VALUE;

        for (Map.Entry<Integer, Integer> entry : pageAccessTime.entrySet()) {
            if (entry.getValue() < minAccessTime) {
                minAccessTime = entry.getValue();
                lruPage = entry.getKey();
            }
        }

        if (lruPage != -1) {
            pageAccessTime.remove(lruPage);
        }
        return lruPage;
    }

    @Override
    public String getName() {
        return "LRU (Least Recently Used)";
    }

    @Override
    public void reset() {
        pageAccessTime.clear();
    }
}
