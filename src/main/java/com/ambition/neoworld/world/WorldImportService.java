package com.ambition.neoworld.world;

import com.ambition.neoworld.NeoWorld;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.datafixers.DataFixer;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.LevelResource;

/** Installs a dimension datapack for the next restart; never mutates live registries. */
public final class WorldImportService {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "NeoWorld world import");
        thread.setDaemon(true);
        return thread;
    });
    private record Inspection(WorldImportFiles.Snapshot files, JsonObject dimension, CompoundTag data,
            int sourceVersion, boolean downgrade, int minSection, int maxSection) {}

    private WorldImportService() {}

    public static int submit(CommandSourceStack source, String folder, ResourceLocation target) {
        return submit(source, folder, target, false);
    }

    public static int submit(CommandSourceStack source, String folder, ResourceLocation target, boolean experimental) {
        if (!folder.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("ชื่อโฟลเดอร์ใช้ตัวอักษรอังกฤษ ตัวเลข _ หรือ - เท่านั้น");
        MinecraftServer server = source.getServer();
        if (target != null) {
            if (target.getNamespace().equals("minecraft") || !target.getNamespace().matches("[a-z0-9_-]+")
                    || !target.getPath().matches("[a-z0-9_-]+")) {
                throw new IllegalArgumentException("ชื่อโลกต้องเป็น namespace:name โดยใช้ a-z, 0-9, _ หรือ - และไม่ใช้ namespace minecraft");
            }
            if (server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).containsKey(target)) {
                throw new IllegalArgumentException("ชื่อโลกนี้ลงทะเบียนอยู่แล้ว: " + target);
            }
        }
        Path imports = server.getServerDirectory().toAbsolutePath().normalize().resolve("world-imports");
        Path save = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        RegistryAccess registries = server.registryAccess();
        if (!BUSY.compareAndSet(false, true)) throw new IllegalStateException("มีงานตรวจหรือนำเข้าโลกกำลังทำอยู่ กรุณารอ");
        source.sendSuccess(() -> Component.literal("กำลัง" + (target == null ? "ตรวจ" : "นำเข้า") + "โลกจาก " + folder + " ผ่านคิวไฟล์"), false);
        IO.execute(() -> {
            try {
                Files.createDirectories(imports);
                WorldImportFiles.requireDirectPath(imports.getParent(), imports);
                Path input = imports.resolve(folder);
                WorldImportFiles.requireDirectPath(imports, input);
                Path lock = input.resolve("session.lock");
                if (Files.exists(lock, LinkOption.NOFOLLOW_LINKS)) {
                    WorldImportFiles.requireDirectPath(input, lock);
                    try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.READ);
                            FileLock held = channel.tryLock(0, Long.MAX_VALUE, true)) {
                        if (held == null) throw new IOException("โลกต้นทางกำลังถูกใช้งาน กรุณาปิดหรือใช้สำเนาโลก");
                        inspectAndInstall(server, source, registries, input, save, target, experimental);
                    }
                } else {
                    inspectAndInstall(server, source, registries, input, save, target, experimental);
                }
            } catch (IOException | RuntimeException exception) {
                NeoWorld.LOGGER.error("World import/check failed for {}", folder, exception);
                if (server.isRunning()) server.execute(() -> source.sendFailure(Component.literal("นำเข้า/ตรวจโลกไม่สำเร็จ: " + exception.getMessage())));
            } finally {
                BUSY.set(false);
            }
        });
        return 1;
    }

    private static Inspection inspect(RegistryAccess registries, DataFixer fixer, Path input, boolean experimental) throws IOException {
        Path level = input.resolve("level.dat");
        WorldImportFiles.requireDirectPath(input, level);
        CompoundTag data = NbtIo.readCompressed(level, NbtAccounter.create(64L * 1024 * 1024)).getCompound("Data");
        int version = data.getInt("DataVersion");
        int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        boolean downgrade = WorldImportDowngrade.requiresDowngrade(version, current, data.getCompound("Version").getString("Name"), experimental);
        if (version < current) {
            data = WorldImportMetadata.upgrade(data, fixer, current);
        }
        CompoundTag stem = data.getCompound("WorldGenSettings").getCompound("dimensions").getCompound("minecraft:overworld");
        var parsed = LevelStem.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries), stem);
        if (parsed.error().isPresent()) throw new IOException("generator หรือ dimension type ต้องใช้ม็อด/datapack ที่เซิร์ฟเวอร์ไม่มี: " + parsed.error().get().message());
        JsonObject dimension = LevelStem.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries),
                parsed.getOrThrow()).getOrThrow().getAsJsonObject();
        var type = parsed.getOrThrow().type().value();
        return new Inspection(WorldImportFiles.scan(input), dimension, data, version, downgrade,
                Math.floorDiv(type.minY(), 16), Math.floorDiv(type.minY() + type.height(), 16));
    }

    private static void inspectAndInstall(MinecraftServer server, CommandSourceStack source, RegistryAccess registries,
            Path input, Path save, ResourceLocation target, boolean experimental) throws IOException {
        Inspection inspection = inspect(registries, server.getFixerUpper(), input, experimental);
        String summary = "ตรวจแล้ว: " + inspection.files.chunks() + " chunks, " + inspection.files.parts().size()
                + " ไฟล์, " + inspection.files.bytes() / (1024 * 1024) + " MiB; จุดเกิดต้นทาง "
                + inspection.data.getInt("SpawnX") + " " + inspection.data.getInt("SpawnY") + " " + inspection.data.getInt("SpawnZ");
        String versionNotice = inspection.downgrade ? " — โหมดทดลอง: แปลงสำเนา แทนบล็อกที่ไม่มีด้วยอากาศ ไม่ย้าย entities/POI และข้าม chunk ที่แปลงไม่ได้"
                : " — DataVersion " + inspection.sourceVersion + "; chunk เก่าจะอัปเกรดเมื่อโหลด";
        if (target == null) {
            if (server.isRunning()) server.execute(() -> source.sendSuccess(() -> Component.literal(summary
                    + versionNotice + " — check ตรวจ metadata/header เท่านั้น ยังไม่ทดสอบแปลง chunk"), false));
            return;
        }
        Path destination = save.resolve("dimensions").resolve(target.getNamespace()).resolve(target.getPath());
        String packName = "neoworld-import-" + UUID.nameUUIDFromBytes(target.toString().getBytes(StandardCharsets.UTF_8));
        Path pack = save.resolve("datapacks").resolve(packName);
        checkedParent(save, destination.getParent());
        checkedParent(save, pack.getParent());
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) || Files.exists(pack, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("มีโฟลเดอร์โลกหรือ datapack ชื่อนี้อยู่แล้ว จะไม่เขียนทับ");
        }
        Path stagingRoot = save.resolve("neoworld/import-staging");
        checkedParent(save, stagingRoot);
        Path staging = Files.createTempDirectory(stagingRoot, "import-");
        Path stagedWorld = staging.resolve("dimension");
        Path stagedPack = staging.resolve("pack");
        WorldImportFiles.Snapshot copy = inspection.downgrade
                ? new WorldImportFiles.Snapshot(input, inspection.files.parts().stream()
                        .filter(part -> part.relative().getName(0).toString().equals("region")).toList(), inspection.files.bytes(), inspection.files.chunks())
                : inspection.files;
        WorldImportFiles.copy(copy, stagedWorld);
        WorldImportDowngrade.Report conversion = null;
        if (inspection.downgrade) {
            Path converted = staging.resolve("converted-region");
            var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            conversion = WorldImportDowngrade.convertRegions(stagedWorld.resolve("region"), converted,
                    ResourceKey.create(Registries.DIMENSION, target), inspection.sourceVersion, current, inspection.minSection, inspection.maxSection,
                    state -> validBlockState(registries, state) && BlockState.CODEC.parse(ops, state).error().isEmpty(),
                    id -> ResourceLocation.tryParse(id) != null && registries.registryOrThrow(Registries.BIOME).containsKey(ResourceLocation.parse(id)),
                    id -> ResourceLocation.tryParse(id) != null && registries.registryOrThrow(Registries.BLOCK_ENTITY_TYPE).containsKey(ResourceLocation.parse(id)),
                    item -> ItemStack.CODEC.parse(ops, item).error().isEmpty(),
                    chunk -> {
                        CompoundTag upgraded = DataFixTypes.CHUNK.updateToCurrentVersion(server.getFixerUpper(), chunk, chunk.getInt("DataVersion"));
                        upgraded.putInt("DataVersion", current);
                        return upgraded;
                    });
            writeObject(stagedWorld.resolve("neoworld-import-conversion-report.json"), conversion);
            Path raw = staging.resolve("raw-region");
            Files.move(stagedWorld.resolve("region"), raw);
            Files.move(converted, stagedWorld.resolve("region"));
            deleteStagedTree(staging, raw);
        }
        JsonObject metadata = new JsonObject();
        metadata.addProperty("world", target.toString());
        metadata.addProperty("sourceFolder", input.getFileName().toString());
        metadata.addProperty("sourceDataVersion", inspection.sourceVersion);
        metadata.addProperty("experimentalDowngrade", inspection.downgrade);
        metadata.addProperty("sourceSeed", inspection.data.getCompound("WorldGenSettings").getLong("seed"));
        metadata.addProperty("spawnX", inspection.data.getInt("SpawnX"));
        metadata.addProperty("spawnY", inspection.data.getInt("SpawnY"));
        metadata.addProperty("spawnZ", inspection.data.getInt("SpawnZ"));
        writeJson(stagedWorld.resolve("neoworld-import.json"), metadata);
        Path definition = stagedPack.resolve("data").resolve(target.getNamespace()).resolve("dimension").resolve(target.getPath() + ".json");
        Files.createDirectories(definition.getParent());
        writeJson(definition, inspection.dimension);
        JsonObject packInfo = new JsonObject();
        JsonObject settings = new JsonObject();
        settings.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA));
        settings.addProperty("description", "NeoWorld imported dimension " + target);
        packInfo.add("pack", settings);
        writeJson(stagedPack.resolve("pack.mcmeta"), packInfo);
        // No REPLACE_EXISTING: refuse existing targets even if another process created them during the copy.
        Files.move(stagedWorld, destination);
        try {
            Files.move(stagedPack, pack); // Publish registration only after all dimension files are in place.
        } catch (IOException exception) {
            try {
                Files.move(destination, stagedWorld);
            } catch (IOException rollback) {
                exception.addSuppressed(rollback);
            }
            throw exception;
        }
        NeoWorld.LOGGER.info("Imported world {} to {}; no template created", target, destination);
        String conversionNotice = conversion == null ? versionNotice : " — แปลง " + conversion.convertedChunks + " chunks, ข้าม "
                + conversion.skippedChunks + ", บล็อกแทนอากาศ " + conversion.replacedBlocks + ", block entities ลบ " + conversion.removedBlockEntities
                + ", กองไอเทมลบ " + conversion.removedItemStacks + ", ฟิลด์ spawner ลบ " + conversion.removedSpawnerDataFields
                + "; ไม่ย้าย entities/POI; รายงาน neoworld-import-conversion-report.json";
        if (server.isRunning()) server.execute(() -> {
            try {
                WorldManagementManager.get(server).loadWorld(target);
                source.sendSuccess(() -> Component.literal(summary + conversionNotice + " — นำเข้าและโหลดเป็น " + target
                        + " เรียบร้อยแล้ว สามารถเข้าใช้งานได้ทันที ไม่ต้องรีสตาร์ต; พื้นที่ที่สร้างเพิ่มจะใช้ seed ของเซิร์ฟเวอร์นี้"), true);
            } catch (Exception loadEx) {
                NeoWorld.LOGGER.warn("Auto-load after import failed for {}", target, loadEx);
                source.sendSuccess(() -> Component.literal(summary + conversionNotice + " — นำเข้าเป็น " + target
                        + " แล้ว (ใช้คำสั่ง /neow world admin load " + target + " หรือรีสตาร์ตเพื่อเปิดใช้)"), true);
            }
        });
    }

    private static void checkedParent(Path save, Path directory) throws IOException {
        Path cursor = save;
        for (Path segment : save.relativize(directory)) {
            cursor = cursor.resolve(segment);
            if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(cursor);
            WorldImportFiles.requireDirectPath(save, cursor);
        }
    }

    private static void writeJson(Path path, JsonObject object) throws IOException {
        writeObject(path, object);
    }

    private static void writeObject(Path path, Object object) throws IOException {
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(object), StandardOpenOption.CREATE_NEW);
    }

    private static boolean validBlockState(RegistryAccess registries, CompoundTag state) {
        ResourceLocation id = ResourceLocation.tryParse(state.getString("Name"));
        if (id == null || !registries.registryOrThrow(Registries.BLOCK).containsKey(id)) return false;
        var block = registries.registryOrThrow(Registries.BLOCK).get(id);
        CompoundTag properties = state.getCompound("Properties");
        for (String name : properties.getAllKeys()) {
            var property = block.getStateDefinition().getProperty(name);
            if (property == null || property.getValue(properties.getString(name)).isEmpty()) return false;
        }
        return true;
    }

    private static void deleteStagedTree(Path staging, Path directory) throws IOException {
        WorldImportFiles.requireDirectPath(staging, directory);
        if (!directory.toAbsolutePath().normalize().startsWith(staging.toAbsolutePath().normalize())) throw new IOException("Unsafe staging cleanup path");
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                WorldImportFiles.requireDirectPath(staging, path);
                Files.delete(path);
            }
        }
    }
}
