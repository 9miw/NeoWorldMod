package com.ambition.neoworld.party;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ambition.neoworld.client.ClientPartyHudData;
import com.ambition.neoworld.network.PartyHudPayload;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class PartyHudRegressionTest {
    public static void main(String[] args) {
        var online = new PartyHudPayload.Member(UUID.randomUUID(), "Member", 45, 13.5F, 40);
        var offline = new PartyHudPayload.Member(UUID.randomUUID(), "Offline", 0, 0, 0);
        var source = new ArrayList<>(List.of(online, offline));
        var payload = new PartyHudPayload(source);
        source.clear();
        check(payload.members().size() == 2, "Snapshot must not alias mutable membership");
        check(roundTrip(payload).equals(payload), "Codec must preserve fractional HP, level and offline data");
        check(roundTrip(PartyHudPayload.EMPTY).members().isEmpty(), "Empty update must survive codec");
        var full = new PartyHudPayload(java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new PartyHudPayload.Member(UUID.randomUUID(), "Player" + i, i + 1, 20, 20)).toList());
        check(roundTrip(full).equals(full), "Raid must support all six members");
        rejects(() -> new PartyHudPayload(java.util.Collections.nCopies(7, online)), "Oversized roster");
        rejects(() -> new PartyHudPayload.Member(UUID.randomUUID(), "Member", 1, Float.NaN, 20), "NaN HP");
        rejects(() -> new PartyHudPayload.Member(UUID.randomUUID(), "Member", 1, 20, Float.POSITIVE_INFINITY), "Infinite HP");
        rejects(() -> new PartyHudPayload.Member(UUID.randomUUID(), "Member", 1, 30, 20), "HP over maximum");
        rejects(() -> new PartyHudPayload.Member(UUID.randomUUID(), "Member", -1, 20, 20), "Negative level");
        for (int count : new int[] {-1, 7}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(count);
                rejects(() -> PartyHudPayload.STREAM_CODEC.decode(buffer), "Untrusted roster count");
            } finally {
                buffer.release();
            }
        }
        ClientPartyHudData.update(payload);
        check(ClientPartyHudData.members().equals(payload.members()), "HUD must update without opening a screen");
        ClientPartyHudData.update(PartyHudPayload.EMPTY);
        check(ClientPartyHudData.members().isEmpty(), "Leaving party must clear HUD");
        ClientPartyHudData.update(payload);
        ClientPartyHudData.reset();
        check(ClientPartyHudData.members().isEmpty(), "Disconnect must clear old server data");
        System.out.println("Party HUD codec and client-state regression checks passed");
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static PartyHudPayload roundTrip(PartyHudPayload payload) {
        var buffer = buffer();
        try {
            PartyHudPayload.STREAM_CODEC.encode(buffer, payload);
            return PartyHudPayload.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void rejects(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message + " must be rejected");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
