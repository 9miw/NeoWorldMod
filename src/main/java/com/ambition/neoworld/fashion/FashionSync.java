package com.ambition.neoworld.fashion;

import com.ambition.neoworld.fashion.data.FashionData;
import com.ambition.neoworld.network.fashion.SyncFashionPayload;
import com.ambition.neoworld.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FashionSync {
    private FashionSync() {
    }

    public static void syncTo(ServerPlayer player) {
        syncPlayerTo(player, player);
    }

    public static void syncPlayerTo(ServerPlayer subject, ServerPlayer receiver) {
        FashionData data = subject.getData(ModAttachments.FASHION_DATA);
        boolean self = subject == receiver;
        PacketDistributor.sendToPlayer(receiver, new SyncFashionPayload(subject.getId(), data.asMap(),
                self, self ? data.wardrobe() : java.util.Map.of()));
    }

    public static void syncToTrackingAndSelf(ServerPlayer player) {
        FashionData data = player.getData(ModAttachments.FASHION_DATA);
        PacketDistributor.sendToPlayersTrackingEntity(player,
                new SyncFashionPayload(player.getId(), data.asMap(), false, java.util.Map.of()));
        syncTo(player);
    }
}
