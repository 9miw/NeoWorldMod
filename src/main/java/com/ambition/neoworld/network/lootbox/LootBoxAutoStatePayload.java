package com.ambition.neoworld.network.lootbox;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.lootbox.LootBoxAutoRun;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LootBoxAutoStatePayload(int remaining) implements CustomPacketPayload {
    public LootBoxAutoStatePayload {
        if (remaining < 0 || remaining > LootBoxAutoRun.MAX_BOXES) throw new IllegalArgumentException("Invalid remaining count");
    }
    public static final Type<LootBoxAutoStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID,"loot_box_auto_state"));
    public static final StreamCodec<FriendlyByteBuf, LootBoxAutoStatePayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> b.writeVarInt(p.remaining()), b -> new LootBoxAutoStatePayload(b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
