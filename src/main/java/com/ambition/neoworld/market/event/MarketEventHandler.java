package com.ambition.neoworld.market.event;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.market.service.MarketDatabaseExecutor;
import com.ambition.neoworld.market.service.MarketService;
import com.ambition.neoworld.market.storage.JdbcMarketRepository;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Periodically returns expired listings asynchronously and manages market database lifecycle. */
@EventBusSubscriber(modid = NeoWorld.MODID)
public final class MarketEventHandler {
    private static final int EXPIRY_CHECK_INTERVAL_TICKS = 20 * 60;
    private static final int EXPIRY_BATCH_SIZE = 50;
    private static final AtomicBoolean EXPIRING = new AtomicBoolean(false);

    private MarketEventHandler() {}

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            com.ambition.neoworld.market.service.MarketDurability.recover(player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long gameTime = server.overworld().getGameTime();
        if (gameTime % EXPIRY_CHECK_INTERVAL_TICKS == 0) {
            if (EXPIRING.compareAndSet(false, true)) {
                MarketDatabaseExecutor.execute(() -> {
                    try {
                        int expiredCount;
                        do {
                            expiredCount = MarketService.expireListingsBatch(server, EXPIRY_BATCH_SIZE);
                        } while (expiredCount == EXPIRY_BATCH_SIZE);
                    } catch (Exception exception) {
                        NeoWorld.LOGGER.error("Error during asynchronous market listing expiration", exception);
                    } finally {
                        EXPIRING.set(false);
                    }
                });
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        long gameTime = event.getServer().overworld().getGameTime();
        MarketDatabaseExecutor.execute(() -> {
            try {
                int recovered = JdbcMarketRepository.recoverInFlightDeliveries(gameTime);
                if (recovered > 0) {
                    NeoWorld.LOGGER.warn("Recovered {} in-flight market deliveries to NEEDS_REVIEW status", recovered);
                }
            } catch (SQLException exception) {
                NeoWorld.LOGGER.error("Could not run market in-flight delivery recovery", exception);
            }
            try {
                long compacted=0;
                while(!Thread.currentThread().isInterrupted()) {
                    var batch=com.ambition.neoworld.market.storage.MarketPlayerJournal.compactBatch(
                            com.ambition.neoworld.database.NeoWorldDatabase::getConnection,
                            com.ambition.neoworld.database.NeoWorldDatabase.tableName("market_player_journal"),128);
                    compacted+=batch.snapshots();
                    if(batch.groups()==0) break;
                }
                if(compacted>0) NeoWorld.LOGGER.info("Compacted {} historical player snapshots; operation receipts retained",compacted);
            } catch(SQLException failure) {
                NeoWorld.LOGGER.error("Could not compact historical player journal snapshots",failure);
            }
        });
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MarketDatabaseExecutor.shutdown();
    }
}
