package com.ambition.neoworld.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.core.registries.Registries;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/** Synthetic NBT/region regression coverage, using the target game's actual NBT and region readers. */
public final class WorldImportCompatibilityRegressionTest {
    private interface Checked { void run() throws IOException; }
    private static final int TARGET = 3955;
    private static final int FUTURE = 4000; // Synthetic fixture version, not a released game's version identifier.

    public static void main(String[] args) throws Exception {
        check(!WorldImportDowngrade.requiresDowngrade(3700, TARGET, "older", false), "older accepted");
        check(!WorldImportDowngrade.requiresDowngrade(TARGET, TARGET, "current", false), "current accepted");
        fails(() -> WorldImportDowngrade.requiresDowngrade(FUTURE, TARGET, "1.21.2", false));
        check(WorldImportDowngrade.requiresDowngrade(FUTURE, TARGET, "1.21.2", true), "explicit newer supported");
        fails(() -> WorldImportDowngrade.requiresDowngrade(FUTURE, TARGET, "1.22", true));
        fails(() -> WorldImportDowngrade.requiresDowngrade(0, TARGET, "", false));
        for (int size : new int[] {1, 2, 16, 17, 32, 33, 256, 4096}) {
            int[] values = new int[4096];
            for (int index = 0; index < values.length; index++) values[index] = index % size;
            long[] packed = WorldImportPalette.pack(size, values, 4);
            for (int index = 0; index < values.length; index++) check(WorldImportPalette.index(size, packed, 4096, 4, index) == values[index], "palette round trip");
        }
        CompoundTag unknown = fixture("future:new_block");
        var report = sanitize(unknown);
        check(report.replacedBlocks == 4096 && unknown.getInt("DataVersion") == TARGET, "count actual removed blocks");
        check(states(unknown).getList("palette", Tag.TAG_COMPOUND).getCompound(0).getString("Name").equals("minecraft:air"), "unknown becomes air");
        CompoundTag duplicate = fixture("minecraft:air");
        CompoundTag missing = new CompoundTag(); missing.putString("Name", "future:block");
        states(duplicate).getList("palette", Tag.TAG_COMPOUND).add(missing);
        int[] ones = new int[4096]; Arrays.fill(ones, 1);
        states(duplicate).putLongArray("data", WorldImportPalette.pack(2, ones, 4));
        sanitize(duplicate);
        check(states(duplicate).getList("palette", Tag.TAG_COMPOUND).size() == 1 && !states(duplicate).contains("data"), "deduplicate and repack singleton air");
        CompoundTag corrupt = fixture("minecraft:air");
        states(corrupt).getList("palette", Tag.TAG_COMPOUND).add(missing.copy());
        states(corrupt).putLongArray("data", new long[1]);
        fails(() -> sanitize(corrupt));
        CompoundTag schema = fixture("minecraft:stone"); schema.putInt("future_schema", 1);
        fails(() -> sanitize(schema));
        CompoundTag inventory = fixture("minecraft:chest");
        ListTag entities = new ListTag();
        CompoundTag chest = new CompoundTag(); chest.putString("id", "minecraft:chest"); chest.putInt("x", 0); chest.putInt("y", 0); chest.putInt("z", 0);
        ListTag items = new ListTag();
        CompoundTag item = new CompoundTag(); item.putString("id", "future:item"); item.putInt("count", 3); items.add(item); chest.put("Items", items); entities.add(chest);
        CompoundTag unsupported = chest.copy(); unsupported.putString("id", "future:block_entity"); entities.add(unsupported); inventory.put("block_entities", entities);
        var removed = sanitize(inventory);
        check(removed.removedItemStacks == 1 && removed.removedBlockEntities == 1 && items.isEmpty(), "remove only incompatible inventory/entity data");
        CompoundTag spawner = fixture("minecraft:spawner");
        CompoundTag spawnerData = new CompoundTag();
        spawnerData.putString("id", "minecraft:mob_spawner"); spawnerData.putInt("x", 0); spawnerData.putInt("y", 0); spawnerData.putInt("z", 0);
        spawnerData.put("SpawnData", new CompoundTag()); spawnerData.put("SpawnPotentials", new ListTag());
        ListTag spawners = new ListTag(); spawners.add(spawnerData); spawner.put("block_entities", spawners);
        var spawnerReport = sanitize(spawner);
        check(spawnerReport.removedSpawnerDataFields == 2 && !spawnerData.contains("SpawnData") && !spawnerData.contains("SpawnPotentials"), "spawner data deletion reported");
        CompoundTag repeated = spawner.copy(); repeated.putInt("DataVersion", FUTURE);
        check(sanitize(repeated).removedSpawnerDataFields == 0, "absent spawner fields not counted twice");
        testOldMetadata();
        testRegions();
        System.out.println("World import compatibility checks passed: version policy, eight palette widths, block replacement/counts, deduplication, schema rejection, inventories, region round trip/skipped chunks and unchanged source.");
    }

    private static WorldImportDowngrade.Report sanitize(CompoundTag chunk) throws IOException {
        return WorldImportDowngrade.sanitize(chunk, FUTURE, TARGET, -4, 20,
                state -> state.getString("Name").startsWith("minecraft:"), biome -> biome.equals("minecraft:plains"),
                id -> id.equals("minecraft:chest") || id.equals("minecraft:mob_spawner"), item -> item.getString("id").startsWith("minecraft:"));
    }

    private static void testOldMetadata() throws Exception {
        SharedConstants.tryDetectVersion();
        CompoundTag old = new CompoundTag();
        old.putInt("DataVersion", 3700); old.putString("LevelName", "legacy-map");
        old.putInt("SpawnX", 24); old.putInt("SpawnY", 80); old.putInt("SpawnZ", -40);
        CompoundTag gen = new CompoundTag(); gen.putLong("seed", 123456789L); gen.putBoolean("generate_features", true); gen.putBoolean("bonus_chest", false);
        CompoundTag dimensions = new CompoundTag(), stem = new CompoundTag(), generator = new CompoundTag(), biome = new CompoundTag();
        stem.putString("type", "minecraft:overworld"); generator.putString("type", "minecraft:noise"); generator.putString("settings", "minecraft:overworld");
        biome.putString("type", "minecraft:multi_noise"); biome.putString("preset", "minecraft:overworld"); generator.put("biome_source", biome);
        stem.put("generator", generator); dimensions.put("minecraft:overworld", stem); gen.put("dimensions", dimensions); old.put("WorldGenSettings", gen);
        CompoundTag before = old.copy();
        var fixer = DataFixers.getDataFixer();
        CompoundTag upgraded = WorldImportMetadata.upgrade(old, fixer, TARGET);
        check(old.equals(before), "DataFixer leaves original metadata untouched");
        check(upgraded.getInt("DataVersion") == TARGET && upgraded.getInt("SpawnX") == 24 && upgraded.getInt("SpawnY") == 80
                && upgraded.getInt("SpawnZ") == -40 && upgraded.getString("LevelName").equals("legacy-map"), "real DataFixer preserves legacy spawn/name");
        CompoundTag fixedGen = upgraded.getCompound("WorldGenSettings");
        check(fixedGen.getLong("seed") == 123456789L && fixedGen.getCompound("dimensions").getCompound("minecraft:overworld")
                .getCompound("generator").getString("type").equals("minecraft:noise"), "real DataFixer preserves seed/generator");
        check(WorldImportMetadata.upgrade(upgraded, fixer, TARGET).equals(upgraded), "current metadata remains unchanged");
        CompoundTag newer = old.copy(); newer.putInt("DataVersion", TARGET + 1);
        fails(() -> WorldImportMetadata.upgrade(newer, fixer, TARGET));
        System.out.println("Real Minecraft DataFixer metadata checks passed (3700 -> " + TARGET + ").");
    }

    private static CompoundTag states(CompoundTag chunk) {
        return chunk.getList("sections", Tag.TAG_COMPOUND).getCompound(0).getCompound("block_states");
    }

    private static CompoundTag fixture(String block) {
        CompoundTag chunk = new CompoundTag(); chunk.putInt("DataVersion", FUTURE); chunk.putInt("xPos", 0); chunk.putInt("zPos", 0); chunk.putString("Status", "minecraft:full");
        ListTag sections = new ListTag(); CompoundTag section = new CompoundTag(); section.putByte("Y", (byte) 0);
        CompoundTag states = new CompoundTag(); ListTag palette = new ListTag(); CompoundTag entry = new CompoundTag(); entry.putString("Name", block); palette.add(entry); states.put("palette", palette); section.put("block_states", states);
        CompoundTag biomes = new CompoundTag(); ListTag biomePalette = new ListTag(); biomePalette.add(StringTag.valueOf("future:biome")); biomes.put("palette", biomePalette); section.put("biomes", biomes);
        sections.add(section); chunk.put("sections", sections); return chunk;
    }

    private static void testRegions() throws Exception {
        Path base = Path.of("build/tmp").toAbsolutePath().normalize(); Files.createDirectories(base);
        Path root = Files.createTempDirectory(base, "world-compat-test-");
        try {
            Path input = Files.createDirectory(root.resolve("input")); Path file = input.resolve("r.0.0.mca");
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("test", "import"));
            RegionStorageInfo info = new RegionStorageInfo("test", key, "chunk");
            try (RegionFile region = new RegionFile(info, file, input, false)) {
                try (var stream = region.getChunkDataOutputStream(new ChunkPos(0, 0))) { NbtIo.write(fixture("future:block"), stream); }
                CompoundTag bad = fixture("minecraft:stone"); bad.putInt("xPos", 1); bad.putString("future_schema", "unsupported");
                try (var stream = region.getChunkDataOutputStream(new ChunkPos(1, 0))) { NbtIo.write(bad, stream); }
            }
            byte[] original = Files.readAllBytes(file);
            Path output = root.resolve("output");
            var report = WorldImportDowngrade.convertRegions(input, output, key, FUTURE, TARGET, -4, 20,
                    state -> state.getString("Name").startsWith("minecraft:"), biome -> false, id -> false, item -> false, chunk -> chunk);
            check(report.convertedChunks == 1 && report.skippedChunks == 1 && report.replacedBlocks == 4096, "region conversion report");
            check(Arrays.equals(original, Files.readAllBytes(file)), "source region unchanged");
            try (RegionFile region = new RegionFile(info, output.resolve("r.0.0.mca"), output, false)) {
                check(region.hasChunk(new ChunkPos(0, 0)) && !region.hasChunk(new ChunkPos(1, 0)), "skip unsupported chunk");
                try (var stream = region.getChunkDataInputStream(new ChunkPos(0, 0))) { check(NbtIo.read(stream).getInt("DataVersion") == TARGET, "output reads in target NBT/region reader"); }
            }
        } finally {
            if (!root.getParent().equals(base)) throw new IllegalStateException("Unsafe cleanup root");
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(root)) throw new IllegalStateException("Unsafe cleanup child");
                    Files.delete(path);
                }
            }
        }
    }

    private static void fails(Checked action) throws IOException {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Unsupported input should be rejected");
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
