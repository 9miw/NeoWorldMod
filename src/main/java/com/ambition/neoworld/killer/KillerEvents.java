package com.ambition.neoworld.killer;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Server event handlers driving combat tracking, sin progression, and death penalties. */
@EventBusSubscriber(modid = NeoWorld.MODID)
public final class KillerEvents {
    private KillerEvents() {}

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            KillerManager.recordDamage(victim, event.getSource(), event.getNewDamage());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            KillerManager.observeDeath(victim, event.getSource(), event::isCanceled);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        KillerManager.flushDeaths(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getOriginal() instanceof ServerPlayer original
                && event.getEntity() instanceof ServerPlayer replacement) {
            // NeoForge copies attachments at NORMAL priority; charge the replacement after that copy.
            KillerManager.flushDeath(original, replacement);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        KillerManager.flushDeaths(event.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        KillerManager.stop(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KillerManager.tick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KillerManager.syncToTrackingAndSelf(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // The departing player may be a queued killer, not just a queued victim.
            // PlayerLoggedOutEvent runs before PlayerList saves and removes this player.
            KillerManager.flushDeaths(player.server);
            KillerManager.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KillerManager.clear(player.getUUID());
            KillerManager.syncToTrackingAndSelf(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer receiver
                && event.getTarget() instanceof ServerPlayer subject) {
            KillerManager.syncTo(subject, receiver);
        }
    }
}
