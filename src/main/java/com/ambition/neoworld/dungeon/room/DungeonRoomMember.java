package com.ambition.neoworld.dungeon.room;

import java.util.UUID;

public record DungeonRoomMember(UUID playerId, String playerName) {
    public DungeonRoomMember {
        if (playerId == null || playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("Dungeon room member data is invalid");
        }
    }
}
