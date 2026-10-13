package com.ambition.neoworld.network.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record ActivityStatePayload(UUID requestId, int tab, int page, int total, int monthDays,
        int onlineSeconds, long nextReset, boolean afk, boolean checkedIn, int pendingCount, String message, List<Entry> entries)
        implements CustomPacketPayload {
    public static final int PAGE_SIZE = 6;
    /** Status: 0 = locked, 1 = claimable, 2 = received. Preview items never enter an inventory. */
    public record Entry(String key, String label, String reward, int status, ItemStack icon) {}
    public static final Type<ActivityStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "activity_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActivityStatePayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeUUID(p.requestId); b.writeVarInt(p.tab); b.writeVarInt(p.page); b.writeVarInt(p.total);
                b.writeVarInt(p.monthDays); b.writeVarInt(p.onlineSeconds); b.writeLong(p.nextReset);
                b.writeBoolean(p.afk); b.writeBoolean(p.checkedIn); b.writeVarInt(p.pendingCount); b.writeUtf(p.message, 256);
                b.writeVarInt(p.entries.size());
                for (var e : p.entries) { b.writeUtf(e.key, 96); b.writeUtf(e.label, 96); b.writeUtf(e.reward, 512);
                    b.writeVarInt(e.status); ItemStack.OPTIONAL_STREAM_CODEC.encode(b, e.icon); }
            }, b -> {
                var id = b.readUUID(); int tab = b.readVarInt(), page = b.readVarInt(), total = b.readVarInt();
                int days = b.readVarInt(), seconds = b.readVarInt(); long reset = b.readLong();
                boolean afk = b.readBoolean(), checkedIn = b.readBoolean(); int pending = b.readVarInt(); String message = b.readUtf(256);
                int count = b.readVarInt();
                if (count < 0 || count > PAGE_SIZE) throw new IllegalArgumentException("Too many activity rows");
                var entries = new ArrayList<Entry>();
                for (int i = 0; i < count; i++) entries.add(new Entry(b.readUtf(96), b.readUtf(96), b.readUtf(512),
                        b.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(b)));
                return new ActivityStatePayload(id, tab, page, total, days, seconds, reset, afk, checkedIn, pending, message, entries);
            });
    public ActivityStatePayload { entries = List.copyOf(entries); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
