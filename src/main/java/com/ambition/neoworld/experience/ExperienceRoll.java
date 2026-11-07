package com.ambition.neoworld.experience;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** Integer sampling without floating-point rounding or stack-wide rerolls. */
public final class ExperienceRoll {
    private ExperienceRoll() {}

    public static long roll(long minimum, long maximum) {
        return roll(minimum, maximum, ThreadLocalRandom.current());
    }

    public static long roll(long minimum, long maximum, RandomGenerator random) {
        if (minimum <= 0 || maximum < minimum) return 0;
        if (minimum == maximum) return minimum;
        return minimum + random.nextLong(maximum - minimum + 1);
    }

    public static long total(long minimum, long maximum, int count, RandomGenerator random) {
        if (count <= 0 || minimum <= 0 || maximum < minimum) return 0;
        if (minimum == maximum) return minimum > Long.MAX_VALUE / count ? Long.MAX_VALUE : minimum * count;
        long total = 0;
        for (int i = 0; i < count; i++) {
            long value = roll(minimum, maximum, random);
            if (value > Long.MAX_VALUE - total) return Long.MAX_VALUE;
            total += value;
        }
        return total;
    }
}
