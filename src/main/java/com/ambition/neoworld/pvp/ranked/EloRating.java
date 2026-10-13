package com.ambition.neoworld.pvp.ranked;

public final class EloRating {
    public static final int INITIAL = 1000;
    private EloRating() { }
    public static int delta(int own, int opponent, double score) {
        if (score != 0 && score != 0.5 && score != 1) throw new IllegalArgumentException("Invalid match score");
        double expected = 1.0 / (1.0 + Math.pow(10, ((double)opponent - own) / 400.0));
        return (int)Math.round(32 * (score - expected));
    }
}
