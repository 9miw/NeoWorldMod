package com.ambition.neoworld.dungeon;

import java.util.Map;
import java.util.UUID;

/** Mutable state for one party's dungeon run. Mutations are owned by DungeonInstanceManager. */
public final class DungeonInstance {
    private final UUID id;
    private final String dungeonId;
    private final int slotIndex;
    private final Map<UUID, DungeonReturnPoint> members;
    private final long createdAtTick;
    private DungeonInstanceState state;
    private long stateChangedAtTick;

    DungeonInstance(
            UUID id,
            String dungeonId,
            int slotIndex,
            Map<UUID, DungeonReturnPoint> members,
            long createdAtTick
    ) {
        this.id = id;
        this.dungeonId = dungeonId;
        this.slotIndex = slotIndex;
        this.members = Map.copyOf(members);
        this.createdAtTick = createdAtTick;
        this.state = DungeonInstanceState.PREPARING;
        this.stateChangedAtTick = createdAtTick;
    }

    public UUID id() {
        return id;
    }

    public String dungeonId() {
        return dungeonId;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public Map<UUID, DungeonReturnPoint> members() {
        return members;
    }

    public long createdAtTick() {
        return createdAtTick;
    }

    public DungeonInstanceState state() {
        return state;
    }

    public long stateChangedAtTick() {
        return stateChangedAtTick;
    }

    void transitionTo(DungeonInstanceState nextState, long gameTick) {
        if (!isValidTransition(state, nextState)) {
            throw new IllegalStateException("Invalid dungeon transition: " + state + " -> " + nextState);
        }
        state = nextState;
        stateChangedAtTick = gameTick;
    }

    private static boolean isValidTransition(DungeonInstanceState current, DungeonInstanceState next) {
        if (next == DungeonInstanceState.CLOSING && current != DungeonInstanceState.CLOSED) {
            return true;
        }
        return switch (current) {
            case PREPARING -> next == DungeonInstanceState.ACTIVE;
            case ACTIVE -> next == DungeonInstanceState.BOSS || next == DungeonInstanceState.REWARD;
            case BOSS -> next == DungeonInstanceState.REWARD;
            case REWARD -> next == DungeonInstanceState.CLOSING;
            case CLOSING -> next == DungeonInstanceState.CLOSED;
            case CLOSED -> false;
        };
    }
}
