package com.ambition.neoworld.network.market;

import java.util.ArrayList;
import java.util.List;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.market.model.MarketManagementEntry;
import com.ambition.neoworld.market.model.MarketManagementPage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record MarketManagementPagePayload(
        int requestId,
        int page,
        int totalPages,
        long totalEntries,
        long pendingSilver,
        long serverGameTime,
        List<Entry> entries,
        String error
) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 20;
    public static final Type<MarketManagementPagePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "market_management_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketManagementPagePayload> STREAM_CODEC =
            StreamCodec.of(MarketManagementPagePayload::encode, MarketManagementPagePayload::decode);

    public MarketManagementPagePayload {
        entries = List.copyOf(entries);
    }

    public static MarketManagementPagePayload fromPage(int requestId, long gameTime,
                                                        MarketManagementPage page) {
        List<Entry> rows = page.entries().stream().map(entry -> new Entry(
                entry.recordId(), entry.listingId(), entry.status(), entry.item(), entry.quantity(),
                entry.unitPriceSilver(), entry.proceedsSilver(), entry.updatedGameTime(),
                entry.expiresGameTime())).toList();
        return new MarketManagementPagePayload(requestId, page.page(), page.totalPages(), page.totalEntries(),
                page.pendingSilver(), gameTime, rows, "");
    }

    public static MarketManagementPagePayload error(int requestId, String message) {
        return new MarketManagementPagePayload(requestId, 1, 1, 0L, 0L, 0L, List.of(), message);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MarketManagementPagePayload payload) {
        buffer.writeVarInt(payload.requestId);
        buffer.writeVarInt(payload.page);
        buffer.writeVarInt(payload.totalPages);
        buffer.writeVarLong(payload.totalEntries);
        buffer.writeVarLong(payload.pendingSilver);
        buffer.writeVarLong(payload.serverGameTime);
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeVarLong(entry.recordId);
            buffer.writeVarLong(entry.listingId);
            buffer.writeEnum(entry.status);
            ItemStack.STREAM_CODEC.encode(buffer, entry.item);
            buffer.writeVarInt(entry.quantity);
            buffer.writeVarLong(entry.unitPriceSilver);
            buffer.writeVarLong(entry.proceedsSilver);
            buffer.writeVarLong(entry.updatedGameTime);
            buffer.writeVarLong(entry.expiresGameTime);
        }
        buffer.writeUtf(payload.error, 256);
    }

    private static MarketManagementPagePayload decode(RegistryFriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt();
        int page = buffer.readVarInt();
        int totalPages = buffer.readVarInt();
        long totalEntries = buffer.readVarLong();
        long pendingSilver = buffer.readVarLong();
        long serverGameTime = buffer.readVarLong();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid market management page size: " + size);
        }
        List<Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new Entry(buffer.readVarLong(), buffer.readVarLong(),
                    buffer.readEnum(MarketManagementEntry.Status.class),
                    ItemStack.STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readVarLong(),
                    buffer.readVarLong(), buffer.readVarLong(), buffer.readVarLong()));
        }
        return new MarketManagementPagePayload(requestId, page, totalPages, totalEntries, pendingSilver,
                serverGameTime, entries, buffer.readUtf(256));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(long recordId, long listingId, MarketManagementEntry.Status status, ItemStack item,
                        int quantity, long unitPriceSilver, long proceedsSilver, long updatedGameTime,
                        long expiresGameTime) {
        public Entry {
            item = item.copy();
        }

        @Override
        public ItemStack item() {
            return item.copy();
        }
    }
}
