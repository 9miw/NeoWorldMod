package com.ambition.neoworld.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PartyHudPayload(List<Member> members) implements CustomPacketPayload {
    private static final int MAX_MEMBERS = 6;
    public static final PartyHudPayload EMPTY = new PartyHudPayload(List.of());
    public static final Type<PartyHudPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "party_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyHudPayload> STREAM_CODEC =
            StreamCodec.of(PartyHudPayload::encode, PartyHudPayload::decode);

    public PartyHudPayload {
        members = List.copyOf(members);
        if (members.size() > MAX_MEMBERS) throw new IllegalArgumentException("Too many party HUD members");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PartyHudPayload payload) {
        buffer.writeVarInt(payload.members.size());
        for (Member member : payload.members) {
            buffer.writeUUID(member.playerId());
            buffer.writeUtf(member.name(), 16);
            buffer.writeVarInt(member.level());
            buffer.writeFloat(member.health());
            buffer.writeFloat(member.maxHealth());
        }
    }

    private static PartyHudPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_MEMBERS) throw new IllegalArgumentException("Invalid party HUD member count");
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(new Member(buffer.readUUID(), buffer.readUtf(16), buffer.readVarInt(),
                    buffer.readFloat(), buffer.readFloat()));
        }
        return new PartyHudPayload(members);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // A zero maximum means the member is offline and has no current health sample.
    public record Member(UUID playerId, String name, int level, float health, float maxHealth) {
        public Member {
            if (playerId == null || name == null || name.isBlank() || name.length() > 16 || level < 0
                    || !Float.isFinite(health) || !Float.isFinite(maxHealth)
                    || health < 0 || maxHealth < 0 || health > maxHealth) {
                throw new IllegalArgumentException("Invalid party HUD member");
            }
        }
    }
}
