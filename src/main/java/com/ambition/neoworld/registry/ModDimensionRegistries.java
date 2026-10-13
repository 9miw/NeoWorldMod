package com.ambition.neoworld.registry;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.pvp.PvpDimensions;

import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

/** Dynamic registry entries for the custom dimensions. */
public final class ModDimensionRegistries {
    private static final ResourceLocation DUNGEON_ID =
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "dungeon");

    public static final ResourceKey<DimensionType> DUNGEON_DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, DUNGEON_ID);
    public static final ResourceKey<LevelStem> DUNGEON_LEVEL_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, DUNGEON_ID);
    public static final ResourceKey<Level> DUNGEON_LEVEL =
            ResourceKey.create(Registries.DIMENSION, DUNGEON_ID);

    private static final ResourceLocation BUILD_WORLD_ID =
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "build_world");
    private static final ResourceLocation RESOURCE_WORLD_ID =
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "resource_world");

    public static final ResourceKey<DimensionType> BUILD_WORLD_DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, BUILD_WORLD_ID);
    public static final ResourceKey<LevelStem> BUILD_WORLD_LEVEL_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, BUILD_WORLD_ID);
    public static final ResourceKey<Level> BUILD_WORLD_LEVEL =
            ResourceKey.create(Registries.DIMENSION, BUILD_WORLD_ID);

    public static final ResourceKey<DimensionType> RESOURCE_WORLD_DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, RESOURCE_WORLD_ID);
    public static final ResourceKey<LevelStem> RESOURCE_WORLD_LEVEL_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, RESOURCE_WORLD_ID);
    public static final ResourceKey<Level> RESOURCE_WORLD_LEVEL =
            ResourceKey.create(Registries.DIMENSION, RESOURCE_WORLD_ID);

    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.DIMENSION_TYPE, ModDimensionRegistries::bootstrapDimensionType)
            .add(Registries.LEVEL_STEM, ModDimensionRegistries::bootstrapLevelStem);

    private ModDimensionRegistries() {
    }

    private static void bootstrapDimensionType(BootstrapContext<DimensionType> context) {
        PvpDimensions.bootstrapDimensionType(context);
        context.register(DUNGEON_DIMENSION_TYPE, new DimensionType(
                OptionalLong.of(18_000L),
                false,
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
                ResourceLocation.withDefaultNamespace("the_end"),
                0.1F,
                new DimensionType.MonsterSettings(false, false, ConstantInt.ZERO, 0)
        ));

        context.register(BUILD_WORLD_DIMENSION_TYPE, createOverworldDimensionType());
        context.register(RESOURCE_WORLD_DIMENSION_TYPE, createOverworldDimensionType());
    }

    private static void bootstrapLevelStem(BootstrapContext<LevelStem> context) {
        PvpDimensions.bootstrapLevelStem(context);
        var dimensionType = context.lookup(Registries.DIMENSION_TYPE)
                .getOrThrow(DUNGEON_DIMENSION_TYPE);
        var voidBiome = context.lookup(Registries.BIOME)
                .getOrThrow(Biomes.THE_VOID);
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
                Optional.empty(),
                voidBiome,
                List.of()
        );

        context.register(DUNGEON_LEVEL_STEM, new LevelStem(
                dimensionType,
                new FlatLevelSource(settings)
        ));

        registerOverworldLevelStem(context, BUILD_WORLD_LEVEL_STEM, BUILD_WORLD_DIMENSION_TYPE);
        registerOverworldLevelStem(context, RESOURCE_WORLD_LEVEL_STEM, RESOURCE_WORLD_DIMENSION_TYPE);
    }

    private static DimensionType createOverworldDimensionType() {
        return new DimensionType(
                OptionalLong.empty(),
                true,
                false,
                false,
                true,
                1.0D,
                true,
                false,
                -64,
                384,
                384,
                BlockTags.INFINIBURN_OVERWORLD,
                ResourceLocation.withDefaultNamespace("overworld"),
                0.0F,
                new DimensionType.MonsterSettings(false, true, UniformInt.of(0, 7), 0)
        );
    }

    private static void registerOverworldLevelStem(
            BootstrapContext<LevelStem> context,
            ResourceKey<LevelStem> levelStemKey,
            ResourceKey<DimensionType> dimensionTypeKey
    ) {
        var dimensionType = context.lookup(Registries.DIMENSION_TYPE)
                .getOrThrow(dimensionTypeKey);
        var biomePreset = context.lookup(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var noiseSettings = context.lookup(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);

        context.register(levelStemKey, new LevelStem(
                dimensionType,
                new NoiseBasedChunkGenerator(
                        MultiNoiseBiomeSource.createFromPreset(biomePreset),
                        noiseSettings
                )
        ));
    }
}
