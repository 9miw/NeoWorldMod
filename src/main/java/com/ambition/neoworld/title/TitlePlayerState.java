package com.ambition.neoworld.title;

import java.util.Map;
import java.util.Set;

/** Immutable committed database snapshot; dates are Unix milliseconds. */
public record TitlePlayerState(Map<String, Long> unlocked, String equipped, boolean showTitle,
        boolean showEffects, Map<String, Long> progress, Map<String, Set<String>> discoveries) {
    public TitlePlayerState {
        unlocked = Map.copyOf(unlocked);
        progress = Map.copyOf(progress);
        discoveries = discoveries.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
        if (equipped == null || (!equipped.isEmpty() && !unlocked.containsKey(equipped)))
            throw new IllegalArgumentException("Equipped title is not unlocked");
    }
}
