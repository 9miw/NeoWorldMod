package com.ambition.neoworld.network.market;

import java.util.UUID;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MarketManagementActionPayload(UUID operationId, Action action, long targetId) implements CustomPacketPayload {
    public static final Type<MarketManagementActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "market_management_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketManagementActionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeUUID(payload.operationId);
                        buffer.writeEnum(payload.action);
                        buffer.writeVarLong(payload.targetId);
                    },
                    buffer -> new MarketManagementActionPayload(
                            buffer.readUUID(), buffer.readEnum(Action.class), buffer.readVarLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        CANCEL_LISTING,
        CLAIM_SALE,
        CLAIM_ALL,
        DELETE_HISTORY
    }
}
