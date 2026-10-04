package com.ambition.neoworld.market;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;

/** Encodes complete ItemStack NBT for storage in a database BLOB. */
public final class MarketItemCodec {
    private static final long MAX_DECOMPRESSED_BYTES = 2L * 1024L * 1024L;

    private MarketItemCodec() {}

    public static byte[] encode(ItemStack item, HolderLookup.Provider registries) throws IOException {
        if (item == null || item.isEmpty()) throw new IOException("Cannot encode an empty market item");
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("item", item.saveOptional(registries));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(wrapper, output);
        return output.toByteArray();
    }

    public static ItemStack decode(byte[] data, HolderLookup.Provider registries) throws IOException {
        if (data == null || data.length == 0) throw new IOException("Market item data is empty");
        CompoundTag wrapper = NbtIo.readCompressed(
                new ByteArrayInputStream(data), NbtAccounter.create(MAX_DECOMPRESSED_BYTES));
        ItemStack item = ItemStack.parseOptional(registries, wrapper.getCompound("item"));
        if (item.isEmpty()) throw new IOException("Market item data decoded to an empty stack");
        return item;
    }
}
