package com.ambition.neoworld.network.dungeon;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonHudPayload(boolean visible, String title, Phase phase, int wave, int totalWaves,
        int enemies, String bossName, int bossPercent, int secondsRemaining, boolean unlimited,
        int alive, int members) implements CustomPacketPayload {
    public enum Phase { WAITING_WAVE, WAVE, WAITING_BOSS, BOSS, REWARD }
    public static final Type<DungeonHudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "dungeon_hud"));
    public static final DungeonHudPayload HIDDEN = new DungeonHudPayload(false, "", Phase.WAVE, 0, 0, 0, "", 0, 0, false, 0, 0);
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonHudPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeBoolean(p.visible); b.writeUtf(p.title, 128); b.writeEnum(p.phase);
                b.writeVarInt(p.wave); b.writeVarInt(p.totalWaves); b.writeVarInt(p.enemies);
                b.writeUtf(p.bossName, 128); b.writeVarInt(p.bossPercent); b.writeVarInt(p.secondsRemaining);
                b.writeBoolean(p.unlimited); b.writeVarInt(p.alive); b.writeVarInt(p.members);
            }, b -> new DungeonHudPayload(b.readBoolean(), b.readUtf(128), b.readEnum(Phase.class),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(128), b.readVarInt(),
                    b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt()));

    public DungeonHudPayload {
        if (title.length() > 128 || bossName.length() > 128 || phase == null || wave < 0 || totalWaves < wave
                || enemies < 0 || bossPercent < 0 || bossPercent > 100 || secondsRemaining < 0
                || alive < 0 || members < alive || members > 6) throw new IllegalArgumentException("Invalid dungeon HUD state");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
