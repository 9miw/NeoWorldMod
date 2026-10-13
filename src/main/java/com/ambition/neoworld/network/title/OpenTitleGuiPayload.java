package com.ambition.neoworld.network.title;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenTitleGuiPayload() implements CustomPacketPayload {
    public static final OpenTitleGuiPayload INSTANCE = new OpenTitleGuiPayload();
    public static final Type<OpenTitleGuiPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "open_titles"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTitleGuiPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
