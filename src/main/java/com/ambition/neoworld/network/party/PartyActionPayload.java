package com.ambition.neoworld.network.party;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PartyActionPayload(Action action, String target, int inviteRange) implements CustomPacketPayload {
    public static final Type<PartyActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "party_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyActionPayload> STREAM_CODEC = StreamCodec.of(
            PartyActionPayload::encode, PartyActionPayload::decode);

    public PartyActionPayload {
        target = target == null ? "" : target;
        if (inviteRange != 16 && inviteRange != 32 && inviteRange != 64) {
            throw new IllegalArgumentException("Invalid party invite range");
        }
    }

    public PartyActionPayload(Action action, String target) {
        this(action, target, 64);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PartyActionPayload payload) {
        buffer.writeEnum(payload.action);
        buffer.writeUtf(payload.target, 64);
        buffer.writeVarInt(payload.inviteRange);
    }

    private static PartyActionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new PartyActionPayload(buffer.readEnum(Action.class), buffer.readUtf(64), buffer.readVarInt());
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
        READY,
        BROWSE_ALL, BROWSE_GENERAL, BROWSE_DUNGEON,
        CREATE_DUNGEON_TRIO, CREATE_DUNGEON_RAID,
        PURPOSE_GENERAL, PURPOSE_DUNGEON,
        REQUEST_JOIN, ACCEPT_REQUEST, DECLINE_REQUEST,
        ALLOW_INVITES, BLOCK_INVITES
    }
}
