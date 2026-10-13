package com.ambition.neoworld.network.market;

import java.util.ArrayList;
import java.util.List;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.market.model.MarketListing;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record MarketPagePayload(int requestId, int page, int totalPages, long totalListings,
                                long serverGameTime, List<Entry> entries, String error)
        implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 36;
    public static final Type<MarketPagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "market_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MarketPagePayload> STREAM_CODEC = StreamCodec.of(
            MarketPagePayload::encode, MarketPagePayload::decode);

    public MarketPagePayload {
        entries = List.copyOf(entries);
    }

    public static MarketPagePayload fromListings(int requestId, int page, int totalPages, long totalListings,
                                                 long gameTime, List<MarketListing> listings) {
        List<Entry> entries = listings.stream().map(listing -> new Entry(
                listing.id(), listing.item(), listing.sellerName(), listing.priceSilver(), listing.expiresAtGameTime()))
                .toList();
        return new MarketPagePayload(requestId, page, totalPages, totalListings, gameTime, entries, "");
    }

    public static MarketPagePayload error(int requestId, String message) {
        return new MarketPagePayload(requestId, 1, 1, 0L, 0L, List.of(), message);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MarketPagePayload payload) {
        buffer.writeVarInt(payload.requestId);
        buffer.writeVarInt(payload.page);
        buffer.writeVarInt(payload.totalPages);
        buffer.writeVarLong(payload.totalListings);
        buffer.writeVarLong(payload.serverGameTime);
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeVarLong(entry.listingId);
            ItemStack.STREAM_CODEC.encode(buffer, entry.item);
            buffer.writeUtf(entry.sellerName, 64);
            buffer.writeVarLong(entry.priceSilver);
            buffer.writeVarLong(entry.expiresAtGameTime);
        }
        buffer.writeUtf(payload.error, 256);
    }

    private static MarketPagePayload decode(RegistryFriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt();
        int page = buffer.readVarInt();
        int totalPages = buffer.readVarInt();
        long total = buffer.readVarLong();
        long gameTime = buffer.readVarLong();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid market page size: " + size);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readVarLong(), ItemStack.STREAM_CODEC.decode(buffer),
                    buffer.readUtf(64), buffer.readVarLong(), buffer.readVarLong()));
        }
        return new MarketPagePayload(requestId, page, totalPages, total, gameTime, entries, buffer.readUtf(256));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(long listingId, ItemStack item, String sellerName, long priceSilver, long expiresAtGameTime) {
        public Entry {
            item = item.copy();
        }

        @Override
        public ItemStack item() {
            return item.copy();
        }
    }
}
