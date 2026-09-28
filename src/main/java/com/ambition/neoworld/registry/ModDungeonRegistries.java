package com.ambition.neoworld.registry;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.core.RegistrySetBuilder;
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

/** Dynamic registry entries for the shared dungeon instance dimension. */
public final class ModDungeonRegistries {
    private static final ResourceLocation DUNGEON_ID =
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "dungeon");

    public static final ResourceKey<DimensionType> DUNGEON_DIMENSION_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, DUNGEON_ID);
    public static final ResourceKey<LevelStem> DUNGEON_LEVEL_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, DUNGEON_ID);
    public static final ResourceKey<Level> DUNGEON_LEVEL =
            ResourceKey.create(Registries.DIMENSION, DUNGEON_ID);

    public static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.DIMENSION_TYPE, ModDungeonRegistries::bootstrapDimensionType)
            .add(Registries.LEVEL_STEM, ModDungeonRegistries::bootstrapLevelStem);

    private ModDungeonRegistries() {
    }

    private static void bootstrapDimensionType(BootstrapContext<DimensionType> context) {
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
    }

    private static void bootstrapLevelStem(BootstrapContext<LevelStem> context) {
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
    }
}
