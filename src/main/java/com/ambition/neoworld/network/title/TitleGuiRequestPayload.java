package com.ambition.neoworld.network.title;

import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The acting player is resolved from the server context, never from this packet. */
public record TitleGuiRequestPayload(UUID requestId, Action action, String titleId, int filter,
        String search, int page, boolean showTitle, boolean showEffects, int ownership, int sort) implements CustomPacketPayload {
    public TitleGuiRequestPayload(UUID requestId, Action action, String titleId, int filter,
            String search, int page, boolean showTitle, boolean showEffects) {
        this(requestId, action, titleId, filter, search, page, showTitle, showEffects, 0, 0);
    }
    public enum Action { QUERY, EQUIP, CLEAR, SETTINGS }
    public static final Type<TitleGuiRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "title_gui_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TitleGuiRequestPayload> STREAM_CODEC = StreamCodec.of(
        (b, p) -> { b.writeUUID(p.requestId); b.writeEnum(p.action); b.writeUtf(p.titleId, 64);
            b.writeVarInt(p.filter); b.writeUtf(p.search, 48); b.writeVarInt(p.page);
            b.writeBoolean(p.showTitle); b.writeBoolean(p.showEffects); b.writeVarInt(p.ownership); b.writeVarInt(p.sort); },
        b -> new TitleGuiRequestPayload(b.readUUID(), b.readEnum(Action.class), b.readUtf(64),
                b.readVarInt(), b.readUtf(48), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readVarInt()));
    public TitleGuiRequestPayload {
        if (requestId == null || action == null || titleId == null || titleId.length() > 64
                || filter < 0 || filter > 3 || search == null || search.length() > 48 || page < 0 || page > 10000
                || ownership < 0 || ownership > 2 || sort < 0 || sort > 2
                || (action == Action.EQUIP && !titleId.matches("[a-z][a-z0-9_]{0,63}")))
            throw new IllegalArgumentException("Invalid title GUI request");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
