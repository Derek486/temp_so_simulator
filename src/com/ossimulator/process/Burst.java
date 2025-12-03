package com.ossimulator.process;

/**
 * Burst
 *
 * Representa una ráfaga de CPU o E/S con su tipo y duración.
 */
public class Burst {
    private final BurstType type;
    private int duration;

    /**
     * Construye una ráfaga.
     *
     * @param type     tipo de ráfaga (CPU o IO)
     * @param duration duración en ticks
     */
    public Burst(BurstType type, int duration) {
        this.type = type;
        this.duration = duration;
    }

    /**
     * Devuelve el tipo de la ráfaga.
     *
     * @return BurstType
     */
    public BurstType getType() {
        return type;
    }

    /**
     * Devuelve la duración restante/definida de la ráfaga.
     *
     * @return duración en ticks
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Establece la duración de la ráfaga.
     *
     * @param duration duración en ticks
     */
    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Override
    public String toString() {
        return type.getDisplayName() + "(" + duration + ")";
    }
}
