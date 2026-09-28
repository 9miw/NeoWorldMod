package com.ambition.neoworld.dungeon;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.MinecraftServer;

/** Provides one runtime instance manager per running server. */
public final class DungeonRuntime {
    private static final Map<MinecraftServer, DungeonInstanceManager> MANAGERS = new WeakHashMap<>();

    private DungeonRuntime() {
    }

    public static synchronized DungeonInstanceManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, ignored -> new DungeonInstanceManager());
    }
}
