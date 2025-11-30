package com.ossimulator.process;

public class Burst {
    private BurstType type;
    private int duration;

    public Burst(BurstType type, int duration) {
        this.type = type;
        this.duration = duration;
    }

    public BurstType getType() {
        return type;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Override
    public String toString() {
        return type.getDisplayName() + "(" + duration + ")";
    }
}
