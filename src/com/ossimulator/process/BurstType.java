package com.ossimulator.process;

/**
 * BurstType
 *
 * Tipos de ráfaga soportados por el simulador.
 */
public enum BurstType {
    CPU("CPU"),
    IO("E/S");

    private final String displayName;

    BurstType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Devuelve el nombre para mostrar del tipo de ráfaga.
     *
     * @return nombre legible
     */
    public String getDisplayName() {
        return displayName;
    }
}
