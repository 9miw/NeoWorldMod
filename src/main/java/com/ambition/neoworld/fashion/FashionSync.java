package com.ambition.neoworld.fashion;

import com.ambition.neoworld.data.fashion.FashionData;
import com.ambition.neoworld.network.SyncFashionPayload;
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
        PacketDistributor.sendToPlayer(receiver, new SyncFashionPayload(subject.getId(), data.asMap()));
    }

    public static void syncToTrackingAndSelf(ServerPlayer player) {
        FashionData data = player.getData(ModAttachments.FASHION_DATA);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SyncFashionPayload(player.getId(), data.asMap()));
    }
}
