package com.ambition.neoworld.party;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.party.PartyNetworkHandler;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class PartyEventHandler {
    private PartyEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var server = player.getServer();
            var result = PartyManager.get(server).handleDisconnect(player.getUUID());
            PartyHudSync.forget(server, player.getUUID());
            if (result.successful()) {
                for (PartyMember member : result.party().members()) {
                    ServerPlayer remaining = server.getPlayerList().getPlayer(member.playerId());
                    if (remaining != null) PartyNetworkHandler.sendState(remaining);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        PartyHudSync.tick(event.getServer());
    }
}
