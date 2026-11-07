package com.ambition.neoworld.client.state;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side mirror of player sin and red-name status. */
public final class ClientKillerData {
    private static final Map<UUID, Integer> PLAYER_SIN = new ConcurrentHashMap<>();

    private ClientKillerData() {}

    public static boolean isRed(UUID playerId) {
        if (playerId == null) return false;
        Integer sin = PLAYER_SIN.get(playerId);
        return sin != null && sin > 0;
    }

    public static int getSin(UUID playerId) {
        if (playerId == null) return 0;
        Integer sin = PLAYER_SIN.get(playerId);
        return sin != null ? sin : 0;
    }

    public static void update(UUID playerId, int sin) {
        if (playerId == null) return;
        if (sin <= 0) {
            PLAYER_SIN.remove(playerId);
        } else {
            PLAYER_SIN.put(playerId, sin);
        }
    }

    public static void remove(UUID playerId) {
        if (playerId != null) {
            PLAYER_SIN.remove(playerId);
        }
    }

    public static void reset() {
        PLAYER_SIN.clear();
    }
}
