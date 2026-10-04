package com.ambition.neoworld.network;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PartyActionPayload(Action action, String target) implements CustomPacketPayload {
    public static final Type<PartyActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "party_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyActionPayload> STREAM_CODEC = StreamCodec.of(
            PartyActionPayload::encode, PartyActionPayload::decode);

    public PartyActionPayload {
        target = target == null ? "" : target;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PartyActionPayload payload) {
        buffer.writeEnum(payload.action);
        buffer.writeUtf(payload.target, 64);
    }

    private static PartyActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new PartyActionPayload(buffer.readEnum(Action.class), buffer.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        REFRESH,
        CREATE_TRIO,
        CREATE_RAID,
        INVITE,
        ACCEPT,
        DECLINE,
        LEAVE,
        KICK,
        DISBAND,
        READY
    }
}
