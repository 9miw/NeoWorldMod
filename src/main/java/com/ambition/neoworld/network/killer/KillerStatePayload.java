package com.ambition.neoworld.network.killer;

import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client notification of a player's current sin value. */
public record KillerStatePayload(UUID playerId, int sin) implements CustomPacketPayload {
    public static final Type<KillerStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "killer_state"));
    public static final StreamCodec<FriendlyByteBuf, KillerStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeUUID(p.playerId);
                b.writeVarInt(p.sin);
            },
            b -> new KillerStatePayload(b.readUUID(), b.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
