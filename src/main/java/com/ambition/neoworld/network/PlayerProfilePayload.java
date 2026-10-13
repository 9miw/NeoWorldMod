package com.ambition.neoworld.network;

import java.util.List;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlayerProfilePayload(UUID requestId, UUID targetId, String name, int level,
        long exp, long requiredExp, float health, float maxHealth, float stamina, float maxStamina,
        List<Stat> stats, List<Effect> effects, String weapon) implements CustomPacketPayload {
    /** Reserved ID for server-initiated self-profile opens via /profile. */
    public static final UUID COMMAND_REQUEST = new UUID(0L, 0L);
    public record Stat(String key, double base, double value, boolean weaponValue) {}
    public record Effect(String key, int amplifier, int ticks) {}
    public PlayerProfilePayload {
        stats = List.copyOf(stats); effects = List.copyOf(effects);
        if (stats.size() > 32 || effects.size() > 64) throw new IllegalArgumentException("Profile too large");
    }
    public static final Type<PlayerProfilePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "player_profile"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerProfilePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeUUID(p.requestId); b.writeUUID(p.targetId); b.writeUtf(p.name, 16);
                b.writeVarInt(p.level); b.writeVarLong(p.exp); b.writeVarLong(p.requiredExp);
                b.writeFloat(p.health); b.writeFloat(p.maxHealth); b.writeFloat(p.stamina); b.writeFloat(p.maxStamina);
                b.writeVarInt(p.stats.size());
                for (Stat s : p.stats) { b.writeUtf(s.key, 128); b.writeDouble(s.base); b.writeDouble(s.value); b.writeBoolean(s.weaponValue); }
                b.writeVarInt(p.effects.size());
                for (Effect e : p.effects) { b.writeUtf(e.key, 128); b.writeVarInt(e.amplifier); b.writeInt(e.ticks); }
                b.writeUtf(p.weapon, 256);
            }, b -> {
                UUID request = b.readUUID(), target = b.readUUID(); String name = b.readUtf(16);
                int level = b.readVarInt(); long exp = b.readVarLong(), required = b.readVarLong();
                float hp = b.readFloat(), maxHp = b.readFloat(), sta = b.readFloat(), maxSta = b.readFloat();
                int count = bounded(b, 32); var stats = new java.util.ArrayList<Stat>(count);
                for (int i = 0; i < count; i++) stats.add(new Stat(b.readUtf(128), b.readDouble(), b.readDouble(), b.readBoolean()));
                count = bounded(b, 64); var effects = new java.util.ArrayList<Effect>(count);
                for (int i = 0; i < count; i++) effects.add(new Effect(b.readUtf(128), b.readVarInt(), b.readInt()));
                return new PlayerProfilePayload(request, target, name, level, exp, required, hp, maxHp, sta, maxSta, stats, effects, b.readUtf(256));
            });
    private static int bounded(RegistryFriendlyByteBuf b, int max) {
        int count = b.readVarInt();
        if (count < 0 || count > max) throw new IllegalArgumentException("Invalid profile count");
        return count;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
