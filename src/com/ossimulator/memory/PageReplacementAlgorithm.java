package com.ossimulator.memory;

import com.ossimulator.process.Proceso;

public interface PageReplacementAlgorithm {
    void pageAccessed(Proceso process, int pageNumber, int currentTime);
    int selectPageToReplace(int currentTime);
    String getName();
    void reset();
}
