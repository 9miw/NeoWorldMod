package com.ambition.neoworld.network.upgrade;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpgradeSubmitPayload(int containerId, long session, int revision) implements CustomPacketPayload {
    public static final Type<UpgradeSubmitPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "upgrade_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeSubmitPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> { buffer.writeVarInt(value.containerId); buffer.writeLong(value.session); buffer.writeInt(value.revision); },
            buffer -> new UpgradeSubmitPayload(buffer.readVarInt(), buffer.readLong(), buffer.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
