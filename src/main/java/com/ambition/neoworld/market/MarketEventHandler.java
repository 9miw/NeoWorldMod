package com.ambition.neoworld.market;

import com.ambition.neoworld.NeoWorld;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Periodically returns expired listings to their sellers' market mailboxes. */
@EventBusSubscriber(modid = NeoWorld.MODID)
public final class MarketEventHandler {
    private static final int EXPIRY_CHECK_INTERVAL_TICKS = 20 * 60;

    private MarketEventHandler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        if (gameTime % EXPIRY_CHECK_INTERVAL_TICKS == 0) {
            MarketService.expireListings(event.getServer());
        }
    }
}
