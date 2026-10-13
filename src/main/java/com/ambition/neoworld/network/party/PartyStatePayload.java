package com.ambition.neoworld.network.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PartyStatePayload(
        boolean inParty,
        int capacity,
        String leaderName,
        List<MemberEntry> members,
        List<InviteEntry> invites,
        List<PartyEntry> parties,
        List<NearbyPlayerEntry> nearbyPlayers,
        String message,
        boolean success
) implements CustomPacketPayload {
    private static final int MAX_MEMBERS = 6;
    private static final int MAX_INVITES = 12;
    private static final int MAX_PARTIES = 24;
    private static final int MAX_NEARBY = 32;
    public static final Type<PartyStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "party_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PartyStatePayload> STREAM_CODEC = StreamCodec.of(
            PartyStatePayload::encode, PartyStatePayload::decode);

    public PartyStatePayload {
        members = List.copyOf(members);
        invites = List.copyOf(invites);
        parties = List.copyOf(parties);
        nearbyPlayers = List.copyOf(nearbyPlayers);
        message = message == null ? "" : message;
        leaderName = leaderName == null ? "" : leaderName;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PartyStatePayload payload) {
        buffer.writeBoolean(payload.inParty);
        buffer.writeVarInt(payload.capacity);
        buffer.writeUtf(payload.leaderName, 64);
        buffer.writeVarInt(payload.members.size());
        for (MemberEntry member : payload.members) {
            buffer.writeUUID(member.playerId);
            buffer.writeUtf(member.name, 64);
            buffer.writeVarInt(member.level);
            buffer.writeBoolean(member.online);
            buffer.writeBoolean(member.leader);
            buffer.writeBoolean(member.ready);
        }
        buffer.writeVarInt(payload.invites.size());
        for (InviteEntry invite : payload.invites) {
            buffer.writeUtf(invite.leaderName, 64);
            buffer.writeVarInt(invite.capacity);
            buffer.writeVarInt(invite.memberCount);
            buffer.writeVarInt(invite.remainingSeconds);
        }
        buffer.writeVarInt(payload.parties.size());
        for (PartyEntry party : payload.parties) {
            buffer.writeUtf(party.leaderName, 64);
            buffer.writeVarInt(party.capacity);
            buffer.writeVarInt(party.memberCount);
            buffer.writeBoolean(party.open);
        }
        buffer.writeVarInt(payload.nearbyPlayers.size());
        for (NearbyPlayerEntry nearby : payload.nearbyPlayers) {
            buffer.writeUtf(nearby.name, 64);
            buffer.writeVarInt(nearby.level);
            buffer.writeVarInt(nearby.distanceMeters);
            buffer.writeUtf(nearby.status, 64);
            buffer.writeBoolean(nearby.available);
        }
        buffer.writeUtf(payload.message, 256);
        buffer.writeBoolean(payload.success);
    }

    private static PartyStatePayload decode(RegistryFriendlyByteBuf buffer) {
        boolean inParty = buffer.readBoolean();
        int capacity = buffer.readVarInt();
        String leaderName = buffer.readUtf(64);
        int memberCount = buffer.readVarInt();
        if (memberCount < 0 || memberCount > MAX_MEMBERS) {
            throw new IllegalArgumentException("Invalid party member count: " + memberCount);
        }
        List<MemberEntry> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            members.add(new MemberEntry(buffer.readUUID(), buffer.readUtf(64), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));
        }
        int inviteCount = buffer.readVarInt();
        if (inviteCount < 0 || inviteCount > MAX_INVITES) {
            throw new IllegalArgumentException("Invalid party invite count: " + inviteCount);
        }
        List<InviteEntry> invites = new ArrayList<>(inviteCount);
        for (int i = 0; i < inviteCount; i++) {
            invites.add(new InviteEntry(buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt()));
        }
        int partyCount = buffer.readVarInt();
        if (partyCount < 0 || partyCount > MAX_PARTIES) {
            throw new IllegalArgumentException("Invalid party list count: " + partyCount);
        }
        List<PartyEntry> parties = new ArrayList<>(partyCount);
        for (int i = 0; i < partyCount; i++) {
            parties.add(new PartyEntry(buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean()));
        }
        int nearbyCount = buffer.readVarInt();
        if (nearbyCount < 0 || nearbyCount > MAX_NEARBY) {
            throw new IllegalArgumentException("Invalid nearby player count: " + nearbyCount);
        }
        List<NearbyPlayerEntry> nearbyPlayers = new ArrayList<>(nearbyCount);
        for (int i = 0; i < nearbyCount; i++) {
            nearbyPlayers.add(new NearbyPlayerEntry(buffer.readUtf(64), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readUtf(64), buffer.readBoolean()));
        }
        return new PartyStatePayload(inParty, capacity, leaderName, members, invites, parties, nearbyPlayers,
                buffer.readUtf(256), buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record MemberEntry(UUID playerId, String name, int level, boolean online, boolean leader, boolean ready) {
    }

    public record InviteEntry(String leaderName, int capacity, int memberCount, int remainingSeconds) {
    }

    public record PartyEntry(String leaderName, int capacity, int memberCount, boolean open) {
    }

    public record NearbyPlayerEntry(String name, int level, int distanceMeters, String status, boolean available) {
    }
}
