package com.ossimulator.process;

public enum ProcessState {
    NEW("New"),
    READY("Ready"),
    RUNNING("Running"),
    BLOCKED_IO("Blocked (I/O)"),
    BLOCKED_MEMORY("Blocked (Memory)"),
    TERMINATED("Terminated");

    private final String displayName;

    ProcessState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
