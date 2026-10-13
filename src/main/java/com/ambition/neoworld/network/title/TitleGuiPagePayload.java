package com.ambition.neoworld.network.title;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TitleGuiPagePayload(UUID requestId, String error, List<Entry> entries, int page,
        int filteredCount, int totalCount, int unlockedCount, String equipped, boolean showTitle,
        boolean showEffects) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 5;
    public record Entry(String id, String name, String display, String description, String requirement, int category,
            boolean rare, int color, boolean unlocked, long acquiredAt, long progress, long target) {
        public Entry {
            if (id == null || id.length() > 64 || name == null || name.length() > 64
                    || display == null || display.length() > 64
                    || description == null || description.length() > 512 || requirement == null || requirement.length() > 512
                    || category < 0 || category > 1 || color < 0 || color > 0xFFFFFF
                    || acquiredAt < 0 || progress < 0 || target < 0) throw new IllegalArgumentException("Invalid title entry");
        }
    }
    public static final Type<TitleGuiPagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "title_gui_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TitleGuiPagePayload> STREAM_CODEC = StreamCodec.of(
            TitleGuiPagePayload::encode, TitleGuiPagePayload::decode);
    public TitleGuiPagePayload {
        entries = List.copyOf(entries);
        if (requestId == null || error == null || error.length() > 256 || entries.size() > PAGE_SIZE
                || page < 0 || page > 10000 || filteredCount < 0 || filteredCount > 1024
                || totalCount < 0 || totalCount > 1024 || filteredCount > totalCount
                || unlockedCount < 0 || unlockedCount > totalCount || equipped == null || equipped.length() > 64)
            throw new IllegalArgumentException("Invalid title GUI page");
    }
    public static TitleGuiPagePayload failure(UUID id, String message) {
        return new TitleGuiPagePayload(id, message, List.of(), 0, 0, 0, 0, "", false, false);
    }
    private static void encode(RegistryFriendlyByteBuf b, TitleGuiPagePayload p) {
        b.writeUUID(p.requestId); b.writeUtf(p.error, 256); b.writeVarInt(p.entries.size());
        for (var e : p.entries) {
            b.writeUtf(e.id, 64); b.writeUtf(e.name, 64); b.writeUtf(e.display, 64);
            b.writeUtf(e.description, 512); b.writeUtf(e.requirement, 512);
            b.writeVarInt(e.category); b.writeBoolean(e.rare); b.writeInt(e.color); b.writeBoolean(e.unlocked);
            b.writeLong(e.acquiredAt); b.writeLong(e.progress); b.writeLong(e.target);
        }
        b.writeVarInt(p.page); b.writeVarInt(p.filteredCount); b.writeVarInt(p.totalCount); b.writeVarInt(p.unlockedCount);
        b.writeUtf(p.equipped, 64); b.writeBoolean(p.showTitle); b.writeBoolean(p.showEffects);
    }
    private static TitleGuiPagePayload decode(RegistryFriendlyByteBuf b) {
        var id = b.readUUID(); var error = b.readUtf(256); int count = b.readVarInt();
        if (count < 0 || count > PAGE_SIZE) throw new IllegalArgumentException("Invalid title page size");
        var entries = new ArrayList<Entry>(count);
        for (int i = 0; i < count; i++) entries.add(new Entry(b.readUtf(64), b.readUtf(64), b.readUtf(64), b.readUtf(512),
                b.readUtf(512), b.readVarInt(), b.readBoolean(), b.readInt(), b.readBoolean(), b.readLong(), b.readLong(), b.readLong()));
        return new TitleGuiPagePayload(id, error, entries, b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readUtf(64), b.readBoolean(), b.readBoolean());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
