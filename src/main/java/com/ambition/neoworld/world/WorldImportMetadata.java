package com.ambition.neoworld.world;

import com.mojang.datafixers.DataFixer;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;

/** Upgrades only the in-memory inspection copy; the original level.dat is never rewritten. */
public final class WorldImportMetadata {
    private WorldImportMetadata() {}

    public static CompoundTag upgrade(CompoundTag source, DataFixer fixer, int targetVersion) throws IOException {
        int sourceVersion = source.getInt("DataVersion");
        if (sourceVersion <= 0 || sourceVersion > targetVersion) throw new IOException("Metadata version cannot be upgraded to this target");
        CompoundTag result = source.copy();
        if (sourceVersion < targetVersion) {
            // Match the vanilla level-data pipeline: LEVEL first, then WORLD_GEN_SETTINGS.
            result = DataFixTypes.LEVEL.update(fixer, result, sourceVersion, targetVersion);
            result.put("WorldGenSettings", DataFixTypes.WORLD_GEN_SETTINGS.update(fixer,
                    result.getCompound("WorldGenSettings"), sourceVersion, targetVersion));
        }
        result.putInt("DataVersion", targetVersion);
        return result;
    }
}
