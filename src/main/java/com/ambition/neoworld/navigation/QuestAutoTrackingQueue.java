package com.ambition.neoworld.navigation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One-shot acceptance order, never inferred from Custom NPCs unordered active-quest map. */
public final class QuestAutoTrackingQueue {
    private final Map<UUID, Integer> pending = new HashMap<>();

    public void accepted(UUID player, int quest) {
        if (player == null || quest < 0) throw new IllegalArgumentException("Invalid quest acceptance");
        pending.put(player, quest);
    }

    public void cancel(UUID player) { pending.remove(player); }

    public Map<UUID, Integer> drain() {
        Map<UUID, Integer> result = Map.copyOf(pending);
        pending.clear();
        return result;
    }
}
