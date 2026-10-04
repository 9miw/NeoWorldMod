package com.ambition.neoworld.datagen;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModDimensionRegistries;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;

/** Generates the dimension type and level stem registry JSON files. */
public final class ModDimensionDatapackProvider extends DatapackBuiltinEntriesProvider {
    public ModDimensionDatapackProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider
    ) {
        super(output, lookupProvider, ModDimensionRegistries.BUILDER, Set.of(NeoWorld.MODID));
    }
}
