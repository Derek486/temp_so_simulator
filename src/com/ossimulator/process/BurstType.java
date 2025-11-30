package com.ossimulator.process;

public enum BurstType {
    CPU("CPU"),
    IO("E/S");

    private final String displayName;

    BurstType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
