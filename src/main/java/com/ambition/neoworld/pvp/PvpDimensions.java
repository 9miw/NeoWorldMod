package com.ambition.neoworld.pvp;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

/** Independent void dimension reserved for PvP arenas. */
public final class PvpDimensions {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "pvp");

    public static final ResourceKey<DimensionType> DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, ID);
    public static final ResourceKey<LevelStem> LEVEL_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, ID);
    public static final ResourceKey<Level> LEVEL =
            ResourceKey.create(Registries.DIMENSION, ID);

    private PvpDimensions() {
    }

    public static void bootstrapDimensionType(BootstrapContext<DimensionType> context) {
        context.register(DIMENSION_TYPE, new DimensionType(
                OptionalLong.of(6_000L),
                true,
                false,
                false,
                false,
                1.0D,
                false,
                false,
                0,
                256,
                256,
                BlockTags.INFINIBURN_OVERWORLD,
                ResourceLocation.withDefaultNamespace("overworld"),
                0.0F,
                new DimensionType.MonsterSettings(false, false, ConstantInt.ZERO, 0)
        ));
    }

    public static void bootstrapLevelStem(BootstrapContext<LevelStem> context) {
        var dimensionType = context.lookup(Registries.DIMENSION_TYPE).getOrThrow(DIMENSION_TYPE);
        var voidBiome = context.lookup(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
        var settings = new FlatLevelGeneratorSettings(Optional.empty(), voidBiome, List.of());
        context.register(LEVEL_STEM, new LevelStem(dimensionType, new FlatLevelSource(settings)));
    }
}
