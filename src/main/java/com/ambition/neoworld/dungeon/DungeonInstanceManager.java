package com.ambition.neoworld.dungeon;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Owns active runs and allocates isolated build slots inside the dungeon dimension. */
public final class DungeonInstanceManager {
    public static final int SLOT_SPACING_BLOCKS = 128;

    private final Map<UUID, DungeonInstance> instances = new HashMap<>();
    private final Map<UUID, UUID> instanceByPlayer = new HashMap<>();

    public DungeonInstance create(
            String dungeonId,
            Map<UUID, DungeonReturnPoint> members,
            long gameTick
    ) {
        if (dungeonId == null || dungeonId.isBlank()) {
            throw new IllegalArgumentException("Dungeon id cannot be blank");
        }
        if (members.isEmpty() || members.size() > 6) {
            throw new IllegalArgumentException("Dungeon party size must be between 1 and 6 players");
        }
        if (members.keySet().stream().anyMatch(instanceByPlayer::containsKey)) {
            throw new IllegalStateException("A party member is already inside a dungeon instance");
        }

        int slotIndex = findFreeSlot();
        DungeonInstance instance = new DungeonInstance(
                UUID.randomUUID(), dungeonId, slotIndex, members, gameTick
        );
        instances.put(instance.id(), instance);
        members.keySet().forEach(playerId -> instanceByPlayer.put(playerId, instance.id()));
        return instance;
    }

    public void transition(UUID instanceId, DungeonInstanceState nextState, long gameTick) {
        DungeonInstance instance = requireInstance(instanceId);
        instance.transitionTo(nextState, gameTick);
        if (nextState == DungeonInstanceState.CLOSED) {
            instances.remove(instanceId);
            instance.members().keySet().forEach(instanceByPlayer::remove);
        }
    }

    public Optional<DungeonInstance> get(UUID instanceId) {
        return Optional.ofNullable(instances.get(instanceId));
    }

    public Optional<DungeonInstance> getForPlayer(UUID playerId) {
        UUID instanceId = instanceByPlayer.get(playerId);
        return instanceId == null ? Optional.empty() : get(instanceId);
    }

    public Collection<DungeonInstance> activeInstances() {
        return java.util.List.copyOf(instances.values());
    }

    public int slotOriginX(int slotIndex) {
        return Math.multiplyExact(slotIndex, SLOT_SPACING_BLOCKS);
    }

    private DungeonInstance requireInstance(UUID instanceId) {
        DungeonInstance instance = instances.get(instanceId);
        if (instance == null) {
            throw new IllegalArgumentException("Unknown dungeon instance: " + instanceId);
        }
        return instance;
    }

    private int findFreeSlot() {
        for (int slot = 0; slot < Integer.MAX_VALUE / SLOT_SPACING_BLOCKS; slot++) {
            int candidate = slot;
            boolean occupied = instances.values().stream()
                    .anyMatch(instance -> instance.slotIndex() == candidate);
            if (!occupied) {
                return slot;
            }
        }
        throw new IllegalStateException("No dungeon instance slots are available");
    }
}
