package com.ambition.neoworld.party;

import java.util.UUID;

public record PartyMember(UUID playerId, String playerName) {
    public PartyMember {
        if (playerId == null || playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("Party member data is invalid");
        }
    }
}
