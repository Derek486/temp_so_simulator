package com.ossimulator.process;

/**
 * ProcessState
 *
 * Estados posibles de un proceso en el simulador.
 */
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

    /**
     * Devuelve el nombre legible del estado.
     *
     * @return nombre para mostrar
     */
    public String getDisplayName() {
        return displayName;
    }
}
