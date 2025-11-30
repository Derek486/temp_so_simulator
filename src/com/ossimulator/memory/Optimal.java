package com.ossimulator.memory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.ossimulator.process.Proceso;

public class Optimal implements PageReplacementAlgorithm {
    private Set<Integer> loadedPages;
    private List<Proceso> allProcesses;
    private int currentTime;

    public Optimal(List<Proceso> allProcesses) {
        this.loadedPages = new HashSet<>();
        this.allProcesses = new ArrayList<>(allProcesses);
        this.currentTime = 0;
    }

    @Override
    public void pageAccessed(Proceso process, int pageNumber, int currentTime) {
        this.currentTime = currentTime;
        loadedPages.add(pageNumber);
    }

    @Override
    public int selectPageToReplace(int currentTime) {
        if (loadedPages.isEmpty()) {
            return -1;
        }

        this.currentTime = currentTime;
        int pageToReplace = -1;
        int maxDistance = -1;

        for (int page : loadedPages) {
            int distance = getDistanceToNextUse(page, currentTime);
            if (distance > maxDistance) {
                maxDistance = distance;
                pageToReplace = page;
            }
        }

        if (pageToReplace != -1) {
            loadedPages.remove(pageToReplace);
        }
        return pageToReplace;
    }

    private int getDistanceToNextUse(int page, int currentTime) {
        return Integer.MAX_VALUE;
    }

    @Override
    public String getName() {
        return "Optimal";
    }

    @Override
    public void reset() {
        loadedPages.clear();
    }
}
