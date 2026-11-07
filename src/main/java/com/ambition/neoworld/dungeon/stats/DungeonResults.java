package com.ambition.neoworld.dungeon.stats;

import com.ambition.neoworld.NeoWorld;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class DungeonResults {
    private static final Map<MinecraftServer, DungeonResultWriter> WRITERS = new WeakHashMap<>();
    private DungeonResults() {}

    private static DungeonResultWriter writer(MinecraftServer server) {
        return WRITERS.computeIfAbsent(server, key -> new DungeonResultWriter(DungeonResultOutbox.get(key),
                Util.ioPool(), DungeonResultRepository::record,
                (id, error) -> NeoWorld.LOGGER.error("Dungeon result {} remains pending; database write will retry", id, error)));
    }

    public static void completed(MinecraftServer server, DungeonClear clear) {
        DungeonResultOutbox.get(server).add(clear);
        writer(server).tick(server.getTickCount());
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        writer(event.getServer());
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var writer = WRITERS.get(event.getServer());
        if (writer != null) writer.tick(event.getServer().getTickCount());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) {
        var writer = WRITERS.remove(event.getServer());
        if (writer != null) writer.stop();
    }
}
