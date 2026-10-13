package com.ambition.neoworld.network;

import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client sends intent only; stacks, identity and balances are resolved on the server. */
public record TradeActionPayload(UUID sessionId, int revision, Action action, int slot, int count,
                                  long silver) implements CustomPacketPayload {
    public enum Action { ACCEPT, CANCEL, OFFER, SILVER, CONFIRM, UNCONFIRM, HEARTBEAT }
    public static final Type<TradeActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "trade_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.sessionId); buf.writeVarInt(value.revision); buf.writeEnum(value.action);
                buf.writeVarInt(value.slot); buf.writeVarInt(value.count); buf.writeLong(value.silver);
            }, buf -> new TradeActionPayload(buf.readUUID(), buf.readVarInt(), buf.readEnum(Action.class),
                    buf.readVarInt(), buf.readVarInt(), buf.readLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
