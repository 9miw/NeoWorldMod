package com.ambition.neoworld.killer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Persistent player sin and decay progress. */
public final class KillerData {
    public static final Codec<KillerData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("sin", 0).forGetter(KillerData::getSin),
                    Codec.INT.optionalFieldOf("onlineTicks", 0).forGetter(KillerData::getOnlineTicks)
            ).apply(instance, KillerData::new));

    private int sin;
    private int onlineTicks;

    public KillerData() {
        this(0, 0);
    }

    public KillerData(int sin, int onlineTicks) {
        if (sin <= 0) {
            this.sin = 0;
            this.onlineTicks = 0;
        } else {
            this.sin = sin;
            this.onlineTicks = Math.max(0, Math.min(KillerRules.DECAY_TICKS - 1, onlineTicks));
        }
    }

    public int getSin() {
        return sin;
    }

    public int getOnlineTicks() {
        return onlineTicks;
    }

    public void setSin(int sin) {
        if (sin <= 0) {
            this.sin = 0;
            this.onlineTicks = 0;
        } else {
            this.sin = sin;
        }
    }

    public void addKill() {
        this.sin = KillerRules.addKillSin(this.sin);
    }

    public boolean tickOnline() {
        return tickOnline(true);
    }

    /** A protected zone pauses the stored partial timer rather than consuming or resetting it. */
    public boolean tickOnline(boolean allowDecay) {
        if (!allowDecay) return false;
        if (sin <= 0) {
            onlineTicks = 0;
            return false;
        }
        onlineTicks++;
        if (onlineTicks >= KillerRules.DECAY_TICKS) {
            onlineTicks = 0;
            sin = Math.max(0, sin - KillerRules.DECAY_SIN);
            return true;
        }
        return false;
    }
}
