package com.ambition.neoworld.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/** Explicitly lossy, bounded adapter for full 1.21.2/1.21.3 terrain chunks. Not a general downgrade engine. */
public final class WorldImportDowngrade {
    private static final Set<String> ROOT_KEYS = Set.of("DataVersion", "xPos", "zPos", "yPos", "Status", "LastUpdate",
            "InhabitedTime", "sections", "block_entities", "block_ticks", "fluid_ticks", "PostProcessing", "Heightmaps",
            "structures", "isLightOn", "CarvingMasks", "below_zero_retrogen", "blending_data", "UpgradeData", "neoforge:attachments");
    public static final class Report {
        public long convertedChunks;
        public long skippedChunks;
        public long replacedBlocks;
        public long replacedBiomeEntries;
        public long removedBlockEntities;
        public long removedItemStacks;
        public long removedStructures;
        public long removedSpawnerDataFields;
        public final boolean entitiesAndPoiNotImported = true;
        public final List<String> issues = new ArrayList<>();

        void merge(Report other) {
            replacedBlocks += other.replacedBlocks;
            replacedBiomeEntries += other.replacedBiomeEntries;
            removedBlockEntities += other.removedBlockEntities;
            removedItemStacks += other.removedItemStacks;
            removedStructures += other.removedStructures;
            removedSpawnerDataFields += other.removedSpawnerDataFields;
        }
    }

    private WorldImportDowngrade() {}

    public static boolean supportsVersion(String name) {
        return name.equals("1.21.2") || name.equals("1.21.3");
    }

    public static boolean requiresDowngrade(int version, int current, String name, boolean experimental) throws IOException {
        if (version <= 0) throw new IOException("ไม่พบ DataVersion ของ save Java");
        if (version > current && (!experimental || !supportsVersion(name))) {
            throw new IOException("โลกใหม่กว่าต้องใช้ experimental และรองรับเฉพาะ Java 1.21.2–1.21.3 (DataVersion " + version + ")");
        }
        return version > current;
    }

    /** Predicates use the target server's registries/codecs; input must be a disposable copied chunk. */
    public static Report sanitize(CompoundTag chunk, int sourceVersion, int targetVersion, int minSection, int maxSection,
            Predicate<CompoundTag> blockAllowed, Predicate<String> biomeAllowed, Predicate<String> blockEntityAllowed,
            Predicate<CompoundTag> itemAllowed) throws IOException {
        if (chunk.getInt("DataVersion") != sourceVersion || !chunk.contains("xPos", Tag.TAG_INT)
                || !chunk.contains("zPos", Tag.TAG_INT) || !chunk.getString("Status").equals("minecraft:full")
                || !chunk.contains("sections", Tag.TAG_LIST) || !ROOT_KEYS.containsAll(chunk.getAllKeys())) {
            throw new IOException("Unsupported chunk version/status/root schema");
        }
        Report report = new Report();
        Map<Integer, CompoundTag> sectionByY = new HashMap<>();
        ListTag sections = chunk.getList("sections", Tag.TAG_COMPOUND);
        if (sections.isEmpty()) throw new IOException("Invalid section list");
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CompoundTag section = sections.getCompound(sectionIndex);
            if (!section.contains("Y", Tag.TAG_BYTE)
                    || !Set.of("Y", "block_states", "biomes", "BlockLight", "SkyLight").containsAll(section.getAllKeys())) {
                throw new IOException("Invalid section schema");
            }
            int y = section.getByte("Y");
            if (sectionByY.put(y, section) != null || y < minSection - 1 || y > maxSection) throw new IOException("Invalid section height");
            section.remove("BlockLight");
            section.remove("SkyLight");
            if (section.contains("block_states")) {
                if (y < minSection || y >= maxSection) throw new IOException("Blocks outside target build height");
                CompoundTag states = section.getCompound("block_states");
                ListTag palette = states.getList("palette", Tag.TAG_COMPOUND);
                boolean[] replaced = new boolean[palette.size()];
                for (int index = 0; index < palette.size(); index++) {
                    if (!blockAllowed.test(palette.getCompound(index))) {
                        replaced[index] = true;
                        CompoundTag air = new CompoundTag();
                        air.putString("Name", "minecraft:air");
                        palette.set(index, air);
                    }
                }
                report.replacedBlocks += canonicalize(states, palette, 4096, 4, replaced);
            }
            if (section.contains("biomes")) {
                CompoundTag biomes = section.getCompound("biomes");
                ListTag palette = biomes.getList("palette", Tag.TAG_STRING);
                for (int index = 0; index < palette.size(); index++) {
                    if (!biomeAllowed.test(palette.getString(index))) {
                        palette.set(index, StringTag.valueOf("minecraft:plains"));
                        report.replacedBiomeEntries++;
                    }
                }
                canonicalize(biomes, palette, 64, 1, null);
            }
        }
        ListTag entities = chunk.getList("block_entities", Tag.TAG_COMPOUND);
        for (int index = entities.size() - 1; index >= 0; index--) {
            CompoundTag entity = entities.getCompound(index);
            int x = entity.getInt("x"), y = entity.getInt("y"), z = entity.getInt("z");
            CompoundTag section = sectionByY.get(Math.floorDiv(y, 16));
            boolean remove = !blockEntityAllowed.test(entity.getString("id")) || (x >> 4) != chunk.getInt("xPos")
                    || (z >> 4) != chunk.getInt("zPos") || section == null || !section.contains("block_states");
            if (!remove) {
                CompoundTag states = section.getCompound("block_states");
                ListTag palette = states.getList("palette", Tag.TAG_COMPOUND);
                int position = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
                String block = palette.getCompound(WorldImportPalette.index(palette.size(), states.getLongArray("data"), 4096, 4, position)).getString("Name");
                remove = block.equals("minecraft:air") || block.equals("minecraft:cave_air") || block.equals("minecraft:void_air");
            }
            if (remove) {
                entities.remove(index);
                report.removedBlockEntities++;
            } else {
                // Future entity NBT inside spawners cannot be validated by the target version.
                if (entity.contains("SpawnData")) { entity.remove("SpawnData"); report.removedSpawnerDataFields++; }
                if (entity.contains("SpawnPotentials")) { entity.remove("SpawnPotentials"); report.removedSpawnerDataFields++; }
                sanitizeItems(entity, itemAllowed, report);
            }
        }
        if (!chunk.getCompound("structures").isEmpty()) report.removedStructures++;
        for (String field : List.of("block_ticks", "fluid_ticks", "PostProcessing", "Heightmaps", "structures", "CarvingMasks",
                "below_zero_retrogen", "blending_data", "UpgradeData", "neoforge:attachments")) chunk.remove(field);
        chunk.putBoolean("isLightOn", false);
        chunk.putInt("DataVersion", targetVersion); // Only after validating/adapting all accepted fields.
        return report;
    }

    private static long canonicalize(CompoundTag container, ListTag palette, int entries, int minimumBits, boolean[] replaced) throws IOException {
        Map<Tag, Integer> unique = new LinkedHashMap<>();
        ListTag canonical = new ListTag();
        int[] mapping = new int[palette.size()];
        for (int index = 0; index < palette.size(); index++) {
            Tag value = palette.get(index);
            Integer known = unique.get(value);
            if (known == null) {
                known = canonical.size();
                unique.put(value, known);
                canonical.add(value);
            }
            mapping[index] = known;
        }
        long removed = 0;
        int[] values = new int[entries];
        long[] packed = container.getLongArray("data");
        for (int index = 0; index < entries; index++) {
            int original = WorldImportPalette.index(palette.size(), packed, entries, minimumBits, index);
            if (replaced != null && replaced[original]) removed++;
            values[index] = mapping[original];
        }
        container.put("palette", canonical);
        long[] output = WorldImportPalette.pack(canonical.size(), values, minimumBits);
        if (output.length == 0) container.remove("data"); else container.putLongArray("data", output);
        return removed;
    }

    private static void sanitizeItems(CompoundTag parent, Predicate<CompoundTag> allowed, Report report) {
        for (String key : new HashSet<>(parent.getAllKeys())) {
            Tag tag = parent.get(key);
            if (tag instanceof CompoundTag compound) {
                if (compound.contains("id", Tag.TAG_STRING) && compound.contains("count", Tag.TAG_ANY_NUMERIC) && !allowed.test(compound)) {
                    parent.remove(key);
                    report.removedItemStacks++;
                } else sanitizeItems(compound, allowed, report);
            } else if (tag instanceof ListTag list) {
                for (int index = list.size() - 1; index >= 0; index--) {
                    if (!(list.get(index) instanceof CompoundTag compound)) continue;
                    if (compound.contains("id", Tag.TAG_STRING) && compound.contains("count", Tag.TAG_ANY_NUMERIC) && !allowed.test(compound)) {
                        list.remove(index);
                        report.removedItemStacks++;
                    } else sanitizeItems(compound, allowed, report);
                }
            }
        }
    }

    public static Report convertRegions(Path sourceRegion, Path outputRegion, ResourceKey<Level> target, int sourceVersion,
            int targetVersion, int minSection, int maxSection, Predicate<CompoundTag> blockAllowed,
            Predicate<String> biomeAllowed, Predicate<String> blockEntityAllowed, Predicate<CompoundTag> itemAllowed,
            UnaryOperator<CompoundTag> upgradeOlder) throws IOException {
        Files.createDirectory(outputRegion);
        Report report = new Report();
        RegionStorageInfo info = new RegionStorageInfo("neoworld-import-conversion", target, "chunk");
        try (var files = Files.list(sourceRegion)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".mca")).sorted().toList()) {
                String[] coordinates = file.getFileName().toString().split("\\.");
                int rx = Integer.parseInt(coordinates[1]), rz = Integer.parseInt(coordinates[2]);
                if (Math.abs((long) rx) > 58_593 || Math.abs((long) rz) > 58_593) throw new IOException("Region outside world coordinate bounds");
                try (RegionFile input = new RegionFile(info, file, sourceRegion, false);
                        RegionFile output = new RegionFile(info, outputRegion.resolve(file.getFileName()), outputRegion, false)) {
                    for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) {
                        ChunkPos position = new ChunkPos(rx * 32 + x, rz * 32 + z);
                        if (!input.hasChunk(position)) continue;
                        CompoundTag chunk;
                        Report changes;
                        try (var stream = input.getChunkDataInputStream(position)) {
                            if (stream == null) throw new IOException("Unreadable chunk compression/header");
                            chunk = NbtIo.read(stream, NbtAccounter.create(64L * 1024 * 1024));
                            if (chunk.getInt("xPos") != position.x || chunk.getInt("zPos") != position.z) throw new IOException("Chunk coordinates do not match region slot");
                            int chunkVersion = chunk.getInt("DataVersion");
                            if (chunkVersion <= 0 || chunkVersion > targetVersion && chunkVersion != sourceVersion) throw new IOException("Unsupported chunk DataVersion");
                            if (chunkVersion < targetVersion) chunk = upgradeOlder.apply(chunk);
                            changes = sanitize(chunk, chunk.getInt("DataVersion"), targetVersion, minSection, maxSection, blockAllowed, biomeAllowed, blockEntityAllowed, itemAllowed);
                        } catch (IOException | RuntimeException exception) {
                            report.skippedChunks++;
                            if (report.issues.size() < 256) report.issues.add(position + ": " + exception.getMessage());
                            continue;
                        }
                        // A write failure aborts the entire import instead of being counted as a skipped chunk.
                        try (var stream = output.getChunkDataOutputStream(position)) { NbtIo.write(chunk, stream); }
                        report.convertedChunks++;
                        report.merge(changes);
                    }
                }
            }
        }
        if (report.convertedChunks == 0) throw new IOException("ไม่มี chunk ที่แปลงได้ จึงไม่ติดตั้งโลก");
        return report;
    }
}
