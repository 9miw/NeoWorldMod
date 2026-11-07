package com.ambition.neoworld.network.lootbox;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.lootbox.LootBoxAutoRun;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** count=0 cancels; positive counts start additional server-validated openings. */
public record LootBoxAutoRequestPayload(UUID opening, int count, boolean fast) implements CustomPacketPayload {
    public LootBoxAutoRequestPayload(UUID opening, int count) { this(opening, count, false); }
    public LootBoxAutoRequestPayload {
        if (count < 0 || count > LootBoxAutoRun.MAX_BOXES) throw new IllegalArgumentException("Invalid auto count");
    }
    public static final Type<LootBoxAutoRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID,"loot_box_auto_request"));
    public static final StreamCodec<FriendlyByteBuf, LootBoxAutoRequestPayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeUUID(p.opening()); b.writeVarInt(p.count()); b.writeBoolean(p.fast()); },
            b -> new LootBoxAutoRequestPayload(b.readUUID(),b.readVarInt(),b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
