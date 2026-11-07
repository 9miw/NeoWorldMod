package com.ambition.neoworld.pvp.ranked;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/** Server-owned, uniform choice from currently usable arenas; callers cannot nominate a map. */
public final class RankedArenaSelector {
    private RankedArenaSelector() { }
    public static Optional<String> pick(List<String> arenas,Predicate<String> ready,RandomGenerator random) {
        var candidates=arenas.stream().distinct().filter(ready).toList();
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(random.nextInt(candidates.size())));
    }
}
