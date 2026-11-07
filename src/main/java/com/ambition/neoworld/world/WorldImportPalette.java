package com.ambition.neoworld.world;

import java.io.IOException;

/** Validates the non-straddling palette storage used by modern Java chunks. */
public final class WorldImportPalette {
    private WorldImportPalette() {}

    public static int index(int paletteSize, long[] data, int entries, int minimumBits, int index) throws IOException {
        if (paletteSize < 1 || paletteSize > entries || index < 0 || index >= entries) throw new IOException("Invalid palette size/index");
        if (paletteSize == 1) {
            if (data.length != 0) throw new IOException("Unexpected singleton palette data");
            return 0;
        }
        int bits = Math.max(minimumBits, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        int perLong = 64 / bits;
        if (data.length != (entries + perLong - 1) / perLong) throw new IOException("Invalid packed palette length");
        int value = (int) ((data[index / perLong] >>> ((index % perLong) * bits)) & ((1L << bits) - 1));
        if (value >= paletteSize) throw new IOException("Palette index outside palette");
        return value;
    }

    public static long[] pack(int paletteSize, int[] values, int minimumBits) throws IOException {
        if (paletteSize < 1 || paletteSize > values.length) throw new IOException("Invalid output palette size");
        int bits = Math.max(minimumBits, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        int perLong = 64 / bits;
        long[] packed = paletteSize == 1 ? new long[0] : new long[(values.length + perLong - 1) / perLong];
        for (int index = 0; index < values.length; index++) {
            if (values[index] < 0 || values[index] >= paletteSize) throw new IOException("Invalid output palette index");
            if (paletteSize > 1) packed[index / perLong] |= (long) values[index] << ((index % perLong) * bits);
        }
        return packed;
    }
}
