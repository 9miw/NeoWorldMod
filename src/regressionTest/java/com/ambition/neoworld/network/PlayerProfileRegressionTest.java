package com.ambition.neoworld.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class PlayerProfileRegressionTest {
    public static void main(String[] args) {
        var stats = new ArrayList<>(List.of(new PlayerProfilePayload.Stat("attribute.name.generic.attack_damage", 1, 12.5, true)));
        var effects = List.of(new PlayerProfilePayload.Effect("effect.minecraft.speed", 1, -1));
        var payload = new PlayerProfilePayload(UUID.randomUUID(), UUID.randomUUID(), "Player", 35,
                100, 1000, 17.5F, 20, 4.5F, 10, stats, effects, "item.minecraft.diamond_sword");
        stats.clear();
        check(payload.stats().size() == 1, "Snapshot must copy mutable stat list");
        check(roundTrip(payload).equals(payload), "Codec must preserve identity, fractional stats, EXP and infinite effects");
        var empty = new PlayerProfilePayload(UUID.randomUUID(), UUID.randomUUID(), "Player", 100,
                Long.MAX_VALUE, Long.MAX_VALUE, 20, 20, -1, -1, List.of(), List.of(), "");
        check(roundTrip(empty).equals(empty), "Codec must preserve unavailable Epic Fight and max-level EXP");
        rejects(() -> new PlayerProfilePayload(payload.requestId(), payload.targetId(), "Player", 1, 0, 100,
                20, 20, 1, 1, Collections.nCopies(33, payload.stats().getFirst()), effects, ""));
        rejects(() -> new PlayerProfilePayload(payload.requestId(), payload.targetId(), "Player", 1, 0, 100,
                20, 20, 1, 1, List.of(), Collections.nCopies(65, effects.getFirst()), ""));
        for (int count : new int[] {-1, 33}) {
            var b = buffer();
            try {
                header(b); b.writeVarInt(count);
                rejects(() -> PlayerProfilePayload.STREAM_CODEC.decode(b));
            } finally { b.release(); }
        }
        for (int count : new int[] {-1, 65}) {
            var b = buffer();
            try {
                header(b); b.writeVarInt(0); b.writeVarInt(count);
                rejects(() -> PlayerProfilePayload.STREAM_CODEC.decode(b));
            } finally { b.release(); }
        }
        System.out.println("Player profile codec regression checks passed");
    }
    private static void header(RegistryFriendlyByteBuf b) {
        b.writeUUID(UUID.randomUUID()); b.writeUUID(UUID.randomUUID()); b.writeUtf("Player", 16);
        b.writeVarInt(1); b.writeVarLong(0); b.writeVarLong(100);
        b.writeFloat(20); b.writeFloat(20); b.writeFloat(1); b.writeFloat(1);
    }
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }
    private static PlayerProfilePayload roundTrip(PlayerProfilePayload p) {
        var b = buffer();
        try { PlayerProfilePayload.STREAM_CODEC.encode(b, p); return PlayerProfilePayload.STREAM_CODEC.decode(b); }
        finally { b.release(); }
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Oversized or negative count must be rejected");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
