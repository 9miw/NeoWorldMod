package com.ambition.neoworld.datagen;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModDungeonRegistries;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;

/** Generates the dungeon dimension type and level stem registry JSON files. */
public final class ModDungeonDatapackProvider extends DatapackBuiltinEntriesProvider {
    public ModDungeonDatapackProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider
    ) {
        super(output, lookupProvider, ModDungeonRegistries.BUILDER, Set.of(NeoWorld.MODID));
    }
}
