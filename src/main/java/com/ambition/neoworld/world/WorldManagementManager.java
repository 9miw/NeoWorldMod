package com.ambition.neoworld.world;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.mixin.WorldBorderAccessor;
import com.ambition.neoworld.mixin.WorldBorderDelegateAccessor;
import com.ambition.neoworld.mixin.WorldChunkCacheInvoker;
import com.ambition.neoworld.mixin.WorldServerAccessor;
import com.ambition.neoworld.registry.ModDimensionRegistries;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/** World management runs exclusively on the server thread; state belongs to this save. */
public final class WorldManagementManager {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final TicketType<UUID> RANDOM_TICKET = TicketType.create("neoworld_world_random", Comparator.<UUID>naturalOrder());
    private static final Map<MinecraftServer, WorldManagementManager> INSTANCES = new HashMap<>();
    private final MinecraftServer server;
    private final Path statePath;
    private final Set<String> failures = new HashSet<>();
    private final Map<String, ResetWarning> warnings = new HashMap<>();
    private final List<ServerLevel> detached = new ArrayList<>();
    private final ArrayDeque<RandomJob> randomJobs = new ArrayDeque<>();
    private State state;
    private boolean storageFailed;
    private boolean stopping;
    private boolean advancing;
    private int ticks;
    private long lastScheduleSecond = -1;
    private int nextRandomGenerationTick;
    private ResetJob resetJob;

    private static final class State {
        int version = 1;
        Map<String, ResetEntry> worlds = new HashMap<>();
        Map<String, Visit> visits = new HashMap<>();
    }

    private static final class ResetEntry {
        int intervalDays;
        long nextReset;
        long pendingReset;
        long lastReset;
    }

    private record ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch) {}
    private record Visit(String world, long generation, ReturnPoint point) {}
    private record ResetWarning(long scheduledReset, long seconds) {}

    /** Used only to import the previously shipped resource-world state. */
    private static final class LegacyState {
        long nextReset;
        long pendingReset;
        Map<String, ReturnPoint> returns;
    }

    private static final class ResetJob {
        final ResourceKey<Level> key;
        final Set<UUID> evacuated = new HashSet<>();
        ServerLevel old;
        LevelStem stem;
        long deadline;
        boolean draining;
        ResetJob(ResourceKey<Level> key) { this.key = key; }
    }

    private static final class RandomJob {
        final UUID player;
        final ServerLevel target;
        final CommandSourceStack source;
        final int radius;
        long deadline;
        int loadedProbes;
        boolean ticketHeld;
        int attempts;
        int x;
        int z;
        CompletableFuture<ChunkResult<ChunkAccess>> chunk;
        RandomJob(ServerPlayer player, ServerLevel target, CommandSourceStack source, int radius) {
            this.player = player.getUUID();
            this.target = target;
            this.source = source;
            this.radius = radius;
        }
    }

    private WorldManagementManager(MinecraftServer server) {
        this.server = server;
        statePath = server.getWorldPath(LevelResource.ROOT).resolve("neoworld/world-management.json");
        try {
            if (Files.exists(statePath)) {
                String content = Files.readString(statePath);
                JsonObject object = JSON.fromJson(content, JsonObject.class);
                if (object == null || !object.has("version") || !object.has("worlds") || !object.has("visits")) {
                    throw new IOException("Missing world management state fields");
                }
                state = JSON.fromJson(object, State.class);
            } else {
                state = new State();
                Path legacyPath = statePath.resolveSibling("resource-world.json");
                ResetEntry entry = new ResetEntry();
                entry.intervalDays = 7;
                entry.nextReset = System.currentTimeMillis() + 7 * DAY;
                if (Files.exists(legacyPath)) {
                    LegacyState legacy = JSON.fromJson(Files.readString(legacyPath), LegacyState.class);
                    if (legacy == null || legacy.nextReset <= 0 || legacy.pendingReset < 0 || legacy.returns == null) {
                        throw new IOException("Invalid legacy resource world state");
                    }
                    entry.nextReset = legacy.nextReset;
                    entry.pendingReset = legacy.pendingReset;
                    String world = ModDimensionRegistries.RESOURCE_WORLD_LEVEL.location().toString();
                    legacy.returns.forEach((id, point) -> state.visits.put(id, new Visit(world, -1, point)));
                }
                if (Files.exists(legacyPath) || server.getLevel(ModDimensionRegistries.RESOURCE_WORLD_LEVEL) != null) {
                    state.worlds.put(ModDimensionRegistries.RESOURCE_WORLD_LEVEL.location().toString(), entry);
                }
                validate();
                write();
            }
            validate();
        } catch (IOException | RuntimeException exception) {
            storageFailed = true;
            NeoWorld.LOGGER.error("World management disabled: could not load state", exception);
        }
    }

    public static WorldManagementManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, WorldManagementManager::new);
    }

    public static void stop(MinecraftServer server) {
        WorldManagementManager manager = INSTANCES.get(server);
        if (manager == null) return;
        manager.stopping = true;
        manager.randomJobs.forEach(manager::releaseRandom);
        manager.randomJobs.clear();
        for (ServerLevel level : List.copyOf(manager.detached)) {
            try {
                level.save(null, true, false);
                level.close();
            } catch (IOException | RuntimeException exception) {
                NeoWorld.LOGGER.error("Could not close detached world {}", level.dimension().location(), exception);
            }
        }
    }

    public static void forget(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    private static ResourceKey<Level> key(String id) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
    }

    private void validate() throws IOException {
        if (state == null || state.version != 1 || state.worlds == null || state.visits == null) {
            throw new IOException("Invalid world management state");
        }
        for (var item : state.worlds.entrySet()) {
            ResetEntry entry = item.getValue();
            if (key(item.getKey()).equals(Level.OVERWORLD) || entry == null || entry.intervalDays < 0
                    || entry.intervalDays > 3650 || entry.nextReset < 0 || entry.pendingReset < 0 || entry.lastReset < 0
                    || entry.intervalDays > 0 && entry.nextReset == 0) throw new IOException("Invalid world reset entry");
        }
        for (var item : state.visits.entrySet()) {
            UUID.fromString(item.getKey());
            Visit visit = item.getValue();
            if (visit == null || visit.world == null) throw new IOException("Invalid world visit");
            key(visit.world);
            if (visit.point != null) {
                key(visit.point.dimension);
                if (!Double.isFinite(visit.point.x) || !Double.isFinite(visit.point.y) || !Double.isFinite(visit.point.z)
                        || !Float.isFinite(visit.point.yaw) || !Float.isFinite(visit.point.pitch)) throw new IOException("Invalid return point");
            }
        }
    }

    private void requireStorage() {
        if (storageFailed || stopping) throw new IllegalStateException("ระบบจัดการโลกไม่พร้อม กรุณาตรวจสอบ log เซิร์ฟเวอร์");
    }

    private void write() throws IOException {
        Files.createDirectories(statePath.getParent());
        Path temporary = statePath.resolveSibling("world-management.json.tmp");
        Files.writeString(temporary, JSON.toJson(state));
        Files.move(temporary, statePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void commit(State previous) {
        try {
            write();
        } catch (IOException exception) {
            state = previous;
            storageFailed = true;
            NeoWorld.LOGGER.error("Could not commit world management state", exception);
            throw new IllegalStateException("บันทึกการจัดการโลกไม่สำเร็จ กรุณาตรวจสอบ log เซิร์ฟเวอร์", exception);
        }
    }

    private State snapshot() {
        return JSON.fromJson(JSON.toJson(state), State.class);
    }

    public List<String> worldIds() {
        Set<String> ids = new HashSet<>();
        server.levelKeys().forEach(dimension -> ids.add(dimension.location().toString()));
        if (state != null && state.worlds != null) ids.addAll(state.worlds.keySet());
        return ids.stream().sorted().toList();
    }

    public List<String> describeWorlds() {
        requireStorage();
        return worldIds().stream().map(id -> {
            ResetEntry entry = state.worlds.get(id);
            String loaded = server.getLevel(key(id)) == null ? " [ไม่โหลด]" : "";
            if (failures.contains(id)) return id + " [รีไม่สำเร็จ/ล็อก]" + loaded;
            if (entry != null && entry.pendingReset != 0) return id + " [รอรี/กำลังรี]" + loaded;
            if (entry == null || entry.intervalDays == 0) return id + " [ไม่รีอัตโนมัติ]" + loaded;
            return id + " — ทุก " + entry.intervalDays + " วัน, ครั้งถัดไป "
                    + Instant.ofEpochMilli(entry.nextReset).atZone(ZoneId.of("Asia/Bangkok")) + loaded;
        }).toList();
    }

    public boolean isLocked(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.OVERWORLD)) return false;
        if (storageFailed || stopping) return true;
        ResetEntry entry = state.worlds.get(dimension.location().toString());
        return failures.contains(dimension.location().toString()) || entry != null
                && (entry.pendingReset != 0 || entry.intervalDays > 0 && System.currentTimeMillis() >= entry.nextReset);
    }

    public ServerLevel loadedWorld(ResourceLocation id) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) throw new IllegalArgumentException("โลกนี้ยังไม่ได้โหลด: " + id);
        return level;
    }

    private void requireResettable(ResourceKey<Level> dimension) {
        requireStorage();
        if (dimension.equals(Level.OVERWORLD)) throw new IllegalArgumentException("รี Overworld หลักขณะเซิร์ฟเวอร์เปิดอยู่ไม่ได้");
        ServerLevel level = server.getLevel(dimension);
        if (level == null) throw new IllegalArgumentException("โลกนี้ยังไม่ได้โหลด: " + dimension.location());
        if (level.getClass() != ServerLevel.class) throw new IllegalArgumentException("โลกนี้ใช้ตัวโหลดเฉพาะที่ระบบรีไม่รองรับ: " + dimension.location());
        if (failures.contains(dimension.location().toString())) throw new IllegalStateException("โลกนี้รีไม่สำเร็จ ตรวจสอบ log ก่อนรีสตาร์ต");
        dimensionPath(dimension);
    }

    public void addReset(ResourceLocation id, int days) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
        requireResettable(dimension);
        if (days < 1 || days > 3650) throw new IllegalArgumentException("รอบรีต้องอยู่ระหว่าง 1–3650 วัน");
        ResetEntry old = state.worlds.get(id.toString());
        if (old != null && old.pendingReset != 0) throw new IllegalStateException("โลกนี้กำลังรอรีหรือรีอยู่");
        State previous = snapshot();
        ResetEntry entry = state.worlds.computeIfAbsent(id.toString(), ignored -> new ResetEntry());
        entry.intervalDays = days;
        entry.nextReset = System.currentTimeMillis() + days * DAY;
        commit(previous);
    }

    public void removeReset(ResourceLocation id) {
        requireStorage();
        ResetEntry entry = state.worlds.get(id.toString());
        if (entry == null || entry.intervalDays == 0) throw new IllegalArgumentException("โลกนี้ไม่ได้ตั้งรีอัตโนมัติ");
        if (entry.pendingReset != 0) throw new IllegalStateException("ยกเลิกรอบรีไม่ได้ขณะกำลังรอรีหรือรีอยู่");
        State previous = snapshot();
        entry.intervalDays = 0;
        entry.nextReset = 0;
        commit(previous); // Keep reset history to recover offline players after removal.
    }

    public void requestReset(ResourceLocation id) {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
        requireResettable(dimension);
        ResetEntry entry = state.worlds.get(id.toString());
        if (entry != null && entry.pendingReset != 0) throw new IllegalStateException("โลกนี้กำลังรอรีหรือรีอยู่");
        State previous = snapshot();
        entry = state.worlds.computeIfAbsent(id.toString(), ignored -> new ResetEntry());
        entry.pendingReset = Math.max(System.currentTimeMillis(), entry.lastReset + 1);
        commit(previous); // Durable intent precedes evacuation or any file move.
        notifyWorld(dimension, WorldMessageConfig.message(WorldMessageConfig.PREPARING));
    }

    /** Called both by this command and dimension travel events from other systems. */
    public boolean remember(ServerPlayer player, ResourceKey<Level> target) {
        if (storageFailed || stopping) return target.equals(Level.OVERWORLD);
        if (isLocked(target)) return false;
        ResetEntry entry = state.worlds.get(target.location().toString());
        if (entry == null || player.level().dimension().equals(target)) return true;
        Visit previous = state.visits.get(player.getUUID().toString());
        ReturnPoint point = point(player);
        if (state.worlds.containsKey(point.dimension)) point = previous == null ? null : previous.point;
        State before = snapshot();
        state.visits.put(player.getUUID().toString(), new Visit(target.location().toString(), entry.lastReset, point));
        try {
            commit(before);
            return true;
        } catch (RuntimeException exception) {
            NeoWorld.LOGGER.error("Could not record world visit for {}", player.getUUID(), exception);
            return false;
        }
    }

    private static ReturnPoint point(ServerPlayer player) {
        return new ReturnPoint(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    public void recordLogout(ServerPlayer player) {
        if (storageFailed || stopping) return;
        String world = player.level().dimension().location().toString();
        ResetEntry entry = state.worlds.get(world);
        if (entry == null) return;
        Visit previous = state.visits.get(player.getUUID().toString());
        if (previous != null && previous.world.equals(world)) return;
        State before = snapshot();
        state.visits.put(player.getUUID().toString(), new Visit(world, entry.lastReset, previous == null ? null : previous.point));
        try {
            commit(before);
        } catch (RuntimeException exception) {
            NeoWorld.LOGGER.error("Could not record logout generation for {}", player.getUUID(), exception);
        }
    }

    public void recover(ServerPlayer player) {
        String world = player.level().dimension().location().toString();
        ResetEntry entry = storageFailed || state == null || state.worlds == null ? null : state.worlds.get(world);
        Visit visit = storageFailed || state == null || state.visits == null ? null : state.visits.get(player.getUUID().toString());
        boolean stale = entry != null && (visit == null ? entry.lastReset != 0
                : !visit.world.equals(world) || visit.generation != entry.lastReset);
        if (!isLocked(player.level().dimension()) && !stale) return;
        try {
            if (!evacuate(player)) throw new IllegalStateException("Return teleport was canceled");
            player.sendSystemMessage(Component.literal("ระบบจัดการโลก: ส่งกลับจากโลกที่รีเซ็ตหรือปิดชั่วคราว"));
        } catch (RuntimeException exception) {
            NeoWorld.LOGGER.error("Could not recover player {} from world {}", player.getUUID(), world, exception);
            player.connection.disconnect(Component.literal("โลกนี้ไม่พร้อมและไม่พบจุดกลับที่ปลอดภัย กรุณาแจ้งแอดมิน"));
        }
    }

    public boolean teleport(ServerPlayer player, ServerLevel target, Vec3 position) {
        if (server.getLevel(target.dimension()) != target || isLocked(target.dimension())) {
            throw new IllegalStateException("โลกปลายทางกำลังรีเซ็ตหรือไม่พร้อมให้เข้า");
        }
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || Math.abs(position.x) >= 29_999_984 || Math.abs(position.z) >= 29_999_984
                || position.y < target.getMinBuildHeight() || position.y >= target.getMaxBuildHeight()
                || !target.getWorldBorder().isWithinBounds(BlockPos.containing(position))) {
            throw new IllegalArgumentException("พิกัดอยู่นอกขอบเขตโลก");
        }
        if (!remember(player, target.dimension())) throw new IllegalStateException("บันทึกจุดกลับไม่สำเร็จ");
        player.teleportTo(target, position.x, position.y, position.z, player.getYRot(), player.getXRot());
        boolean arrived = player.level() == target && player.position().distanceToSqr(position) < 1.0;
        if (arrived) player.fallDistance = 0;
        return arrived;
    }

    public boolean queueRandom(ServerPlayer player, ServerLevel target, CommandSourceStack source, int radius) {
        requireStorage();
        if (isLocked(target.dimension())) throw new IllegalStateException("โลกปลายทางกำลังรีเซ็ตหรือไม่พร้อมให้เข้า");
        if (radius < 1 || radius > 10_000) throw new IllegalArgumentException("รัศมีสุ่มต้องอยู่ระหว่าง 1–10000 บล็อก");
        if (randomJobs.size() >= 32 || randomJobs.stream().anyMatch(job -> job.player.equals(player.getUUID()))) return false;
        randomJobs.addLast(new RandomJob(player, target, source, radius));
        return true;
    }

    private void tickRandom() {
        RandomJob job = randomJobs.peekFirst();
        if (job == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(job.player);
        if (player == null) {
            releaseRandom(job);
            randomJobs.removeFirst();
            return;
        }
        try {
            if (job.deadline == 0) job.deadline = System.currentTimeMillis() + Duration.ofMinutes(2).toMillis();
            if (System.currentTimeMillis() >= job.deadline || job.attempts >= 16 && job.chunk == null) {
                throw new IllegalStateException("ไม่พบจุดสุ่มที่ปลอดภัยสำหรับ " + player.getGameProfile().getName());
            }
            if (server.getLevel(job.target.dimension()) != job.target || isLocked(job.target.dimension())) {
                throw new IllegalStateException("ยกเลิกการสุ่ม: โลกปลายทางกำลังรีเซ็ตหรือไม่พร้อม");
            }
            if (job.chunk == null) {
                if (job.loadedProbes >= 16 && ticks < nextRandomGenerationTick) return;
                WorldBorder border = job.target.getWorldBorder();
                double minX = Math.max(-29_999_983, Math.max(border.getMinX() + 1, border.getCenterX() - job.radius));
                double maxX = Math.min(29_999_983, Math.min(border.getMaxX() - 1, border.getCenterX() + job.radius));
                double minZ = Math.max(-29_999_983, Math.max(border.getMinZ() + 1, border.getCenterZ() - job.radius));
                double maxZ = Math.min(29_999_983, Math.min(border.getMaxZ() - 1, border.getCenterZ() + job.radius));
                if (minX >= maxX || minZ >= maxZ) throw new IllegalStateException("ขอบเขตโลกเล็กเกินไปสำหรับสุ่มตำแหน่ง");
                job.x = (int) Math.floor(minX + job.target.random.nextDouble() * (maxX - minX));
                job.z = (int) Math.floor(minZ + job.target.random.nextDouble() * (maxZ - minZ));
                ChunkPos position = new ChunkPos(job.x >> 4, job.z >> 4);
                if (job.loadedProbes < 16) {
                    job.loadedProbes++;
                    if (job.target.getChunkSource().getChunkNow(position.x, position.z) != null) {
                        BlockPos feet = safeColumn(job.target, job.x, job.z);
                        if (feet != null && teleport(player, job.target, Vec3.atBottomCenterOf(feet))) {
                            randomSucceeded(job, player, feet);
                        }
                    }
                    return;
                }
                job.attempts++;
                nextRandomGenerationTick = ticks + 20;
                job.target.getChunkSource().addRegionTicket(RANDOM_TICKET, position, 0, job.player);
                job.ticketHeld = true;
                job.chunk = ((WorldChunkCacheInvoker) job.target.getChunkSource())
                        .neoworld$requestWorldChunk(position.x, position.z, ChunkStatus.FULL, true);
            } else if (job.chunk.isDone()) {
                if (job.chunk.join().isSuccess()) {
                    BlockPos feet = safeColumn(job.target, job.x, job.z);
                    if (feet != null && teleport(player, job.target, Vec3.atBottomCenterOf(feet))) {
                        randomSucceeded(job, player, feet);
                        return;
                    }
                }
                releaseRandom(job);
            }
        } catch (RuntimeException exception) {
            releaseRandom(job);
            randomJobs.removeFirst();
            job.source.sendFailure(Component.literal(exception.getMessage() == null ? "สุ่มตำแหน่งไม่สำเร็จ" : exception.getMessage()));
            NeoWorld.LOGGER.debug("Random world teleport failed for {}", job.player, exception);
        }
    }

    private void randomSucceeded(RandomJob job, ServerPlayer player, BlockPos feet) {
        releaseRandom(job);
        randomJobs.removeFirst();
        job.source.sendSuccess(() -> Component.literal("ย้าย " + player.getGameProfile().getName() + " ไป "
                + job.target.dimension().location() + " ที่ " + feet.toShortString()), true);
    }

    private void releaseRandom(RandomJob job) {
        if (job.ticketHeld) {
            ChunkPos position = new ChunkPos(job.x >> 4, job.z >> 4);
            job.target.getChunkSource().removeRegionTicket(RANDOM_TICKET, position, 0, job.player);
            job.ticketHeld = false;
        }
        job.chunk = null;
    }

    private static boolean safeFeet(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 1 >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(feet)) return false;
        var floor = level.getBlockState(feet.below());
        return floor.isFaceSturdy(level, feet.below(), Direction.UP)
                && !floor.is(Blocks.MAGMA_BLOCK) && !floor.is(Blocks.CAMPFIRE) && !floor.is(Blocks.SOUL_CAMPFIRE)
                && !floor.is(Blocks.CACTUS) && !floor.is(Blocks.POWDER_SNOW)
                && level.getFluidState(feet.below()).isEmpty()
                && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
    }

    private static BlockPos safeColumn(ServerLevel level, int x, int z) {
        if (level.dimensionType().hasCeiling()) {
            int ceiling = Math.min(level.getMaxBuildHeight() - 2, level.getMinBuildHeight() + level.dimensionType().logicalHeight() - 2);
            for (int y = ceiling; y > level.getMinBuildHeight(); y--) {
                BlockPos feet = new BlockPos(x, y, z);
                if (safeFeet(level, feet)) return feet;
            }
            return null;
        }
        BlockPos feet = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        return safeFeet(level, feet) ? feet : null;
    }

    private static BlockPos safeNear(ServerLevel level, BlockPos center, int radius) {
        if (safeFeet(level, center)) return center;
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    BlockPos feet = safeColumn(level, center.getX() + dx, center.getZ() + dz);
                    if (feet != null) return feet;
                }
            }
        }
        return null;
    }

    private boolean evacuate(ServerPlayer player) {
        Visit visit = storageFailed || state == null || state.visits == null ? null : state.visits.get(player.getUUID().toString());
        ReturnPoint point = visit == null ? null : visit.point;
        ServerLevel destination = point == null ? null : server.getLevel(key(point.dimension));
        BlockPos feet = null;
        if (destination != null && destination != player.level() && !isLocked(destination.dimension())
                && !state.worlds.containsKey(point.dimension)) {
            feet = safeNear(destination, BlockPos.containing(point.x, point.y, point.z), 8);
        }
        if (feet == null) {
            destination = server.overworld();
            feet = safeNear(destination, destination.getSharedSpawnPos(), 16);
        }
        if (feet == null) throw new IllegalStateException("ไม่พบจุดกลับที่ปลอดภัยใน Overworld");
        player.teleportTo(destination, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                point == null ? 0 : point.yaw, point == null ? 0 : point.pitch);
        player.fallDistance = 0;
        return player.level() == destination;
    }

    /** Reset jobs are serialized; chunk draining advances a little each server tick. */
    public void tick() {
        if (stopping || storageFailed || advancing) return;
        advancing = true;
        try {
            tickRandom();
            ticks++;
            long now = System.currentTimeMillis();
            long second = now / 1000;
            if (second != lastScheduleSecond) {
                lastScheduleSecond = second;
                for (String id : List.copyOf(state.worlds.keySet())) {
                    ResetEntry entry = state.worlds.get(id);
                    if (entry.pendingReset != 0 || entry.intervalDays == 0 || failures.contains(id)) continue;
                    if (now >= entry.nextReset) {
                        try {
                            requestReset(ResourceLocation.parse(id));
                        } catch (RuntimeException exception) {
                            failWorld(id, exception);
                        }
                    } else {
                        warnReset(id, entry.nextReset, now);
                    }
                }
            }
            if (storageFailed) return;
            if (resetJob == null) {
                state.worlds.entrySet().stream().filter(item -> item.getValue().pendingReset != 0 && !failures.contains(item.getKey()))
                        .sorted(Map.Entry.comparingByKey()).findFirst().ifPresent(item -> resetJob = new ResetJob(key(item.getKey())));
            }
            if (resetJob != null) advanceReset();
        } finally {
            advancing = false;
        }
    }

    private void warnReset(String id, long scheduledReset, long now) {
        long remaining = (scheduledReset - now + 999) / 1000;
        long stage = remaining <= 10 ? remaining : remaining <= 30 ? 30 : remaining <= 180 ? 180 : remaining <= 300 ? 300 : 0;
        if (stage <= 0) return;
        ResetWarning previous = warnings.get(id);
        if (previous != null && previous.scheduledReset == scheduledReset && stage >= previous.seconds) return;
        warnings.put(id, new ResetWarning(scheduledReset, stage));
        notifyWorld(key(id), WorldMessageConfig.warning(stage));
    }

    private Path dimensionPath(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.OVERWORLD)) throw new IllegalArgumentException("รี Overworld หลักไม่ได้");
        String namespace = dimension.location().getNamespace();
        if (namespace.equals(".") || namespace.equals("..")) throw new IllegalArgumentException("ชื่อโลกมี namespace ที่ไม่ปลอดภัย");
        String pathPart = dimension.location().getPath();
        for (String part : pathPart.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IllegalArgumentException("ชื่อโลกมี path ที่ไม่ปลอดภัย");
        }
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path path = ((WorldServerAccessor) server).neoworld$worldStorage().getDimensionPath(dimension).toAbsolutePath().normalize();
        Path expected = DimensionType.getStorageFolder(dimension, root).toAbsolutePath().normalize();
        if (!path.equals(expected) || !path.startsWith(root) || path.equals(root)) throw new IllegalStateException("Unexpected world path: " + path);
        for (Path cursor = path; !cursor.equals(root); cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) throw new IllegalStateException("Refusing symbolic world path: " + path);
        }
        try {
            // toRealPath also catches Windows directory junctions that are not symbolic links.
            if (!path.toRealPath().equals(root.toRealPath().resolve(root.relativize(path)))) {
                throw new IllegalStateException("Refusing redirected world path: " + path);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("ตรวจสอบเส้นทางไฟล์โลกไม่สำเร็จ", exception);
        }
        for (String other : worldIds()) {
            ResourceKey<Level> otherKey = key(other);
            if (otherKey.equals(dimension) || otherKey.equals(Level.OVERWORLD)) continue;
            Path otherPath = DimensionType.getStorageFolder(otherKey, root).toAbsolutePath().normalize();
            if (otherPath.startsWith(path) || path.startsWith(otherPath)) {
                throw new IllegalArgumentException("เส้นทางโลกซ้อนกับโลกอื่น จึงรีไม่ได้: " + other);
            }
        }
        return path;
    }

    private void advanceReset() {
        ResetJob job = resetJob;
        String id = job.key.location().toString();
        ResetEntry entry = state.worlds.get(id);
        try {
            Path path = dimensionPath(job.key);
            Path backup = path.resolveSibling(path.getFileName() + "-backup-" + entry.pendingReset);
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            if (worldIds().stream().map(WorldManagementManager::key)
                    .anyMatch(other -> DimensionType.getStorageFolder(other, root).toAbsolutePath().normalize().equals(backup))) {
                throw new IOException("Backup path is owned by another world");
            }
            if (Files.exists(backup)) {
                if (job.draining || Files.isSymbolicLink(backup) || !Files.isDirectory(backup)
                        || !backup.toRealPath().equals(path.getParent().toRealPath().resolve(backup.getFileName()))) {
                    throw new IOException("Invalid reset backup state");
                }
                ServerLevel recovered = server.getLevel(job.key);
                if (recovered == null) throw new IllegalStateException("World was not loaded after interrupted reset");
                resetEndFight(recovered);
                finishReset(id);
                return;
            }
            if (!job.draining) {
                job.old = server.getLevel(job.key);
                if (job.old == null) throw new IllegalStateException("World is not loaded: " + id);
                job.stem = new LevelStem(job.old.dimensionTypeRegistration(), job.old.getChunkSource().getGenerator());
                notifyWorld(job.key, WorldMessageConfig.message(WorldMessageConfig.STARTING));
                for (ServerPlayer player : List.copyOf(job.old.players())) {
                    if (!evacuate(player)) throw new IllegalStateException("Could not evacuate " + player.getUUID());
                    job.evacuated.add(player.getUUID());
                }
                server.getPlayerList().saveAll();
                job.old.save(null, true, false);
                server.forgeGetWorldMap().remove(job.key);
                server.markWorldsDirty();
                detached.add(job.old);
                job.draining = true;
                job.deadline = System.currentTimeMillis() + Duration.ofSeconds(60).toMillis();
                return;
            }
            job.old.getChunkSource().removeTicketsOnClosing();
            job.old.getChunkSource().tick(() -> true, false);
            long pollUntil = System.nanoTime() + 5_000_000;
            while (System.nanoTime() < pollUntil && job.old.getChunkSource().pollTask()) {}
            if (job.old.getChunkSource().chunkMap.hasWork()) {
                if (System.currentTimeMillis() >= job.deadline) throw new IllegalStateException("World chunk drain timed out");
                return;
            }
            NeoForge.EVENT_BUS.post(new LevelEvent.Unload(job.old));
            job.old.close();
            detached.remove(job.old);
            Files.move(path, backup, StandardCopyOption.ATOMIC_MOVE);
            if (job.key.equals(Level.END)) server.getWorldData().setEndDragonFightData(EndDragonFight.Data.DEFAULT);
            WorldServerAccessor access = (WorldServerAccessor) server;
            ServerLevel fresh = new ServerLevel(server, access.neoworld$worldExecutor(), access.neoworld$worldStorage(),
                    new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()), job.key, job.stem,
                    new ChunkProgressListener() {
                        public void updateSpawnPos(ChunkPos position) {}
                        public void onStatusChange(ChunkPos position, ChunkStatus status) {}
                        public void start() {}
                        public void stop() {}
                    }, server.getWorldData().isDebugWorld(), BiomeManager.obfuscateSeed(server.getWorldData().worldGenOptions().seed()),
                    List.of(), false, null);
            fresh.getWorldBorder().applySettings(job.old.getWorldBorder().createSettings());
            transferBorderListener(job.old, fresh);
            server.forgeGetWorldMap().put(job.key, fresh);
            server.markWorldsDirty();
            NeoForge.EVENT_BUS.post(new LevelEvent.Load(fresh));
            finishReset(id);
        } catch (IOException | RuntimeException exception) {
            failWorld(id, exception);
            resetJob = null;
        }
    }

    private void resetEndFight(ServerLevel level) {
        if (!level.dimension().equals(Level.END)) return;
        server.getWorldData().setEndDragonFightData(EndDragonFight.Data.DEFAULT);
        if (level.getDragonFight() != null) {
            level.setDragonFight(new EndDragonFight(level, server.getWorldData().worldGenOptions().seed(), EndDragonFight.Data.DEFAULT));
        }
    }

    private void transferBorderListener(ServerLevel old, ServerLevel fresh) {
        WorldBorder overworld = server.overworld().getWorldBorder();
        boolean followed = false;
        for (BorderChangeListener listener : List.copyOf(((WorldBorderAccessor) overworld).neoworld$borderListeners())) {
            if (listener instanceof BorderChangeListener.DelegateBorderChangeListener
                    && ((WorldBorderDelegateAccessor) listener).neoworld$delegateBorder() == old.getWorldBorder()) {
                overworld.removeListener(listener);
                followed = true;
            }
        }
        if (followed) overworld.addListener(new BorderChangeListener.DelegateBorderChangeListener(fresh.getWorldBorder()));
    }

    private void finishReset(String id) {
        if (key(id).equals(Level.END)) persistEndFight();
        State previous = snapshot();
        ResetEntry entry = state.worlds.get(id);
        entry.lastReset = entry.pendingReset;
        entry.pendingReset = 0;
        entry.nextReset = entry.intervalDays == 0 ? 0 : System.currentTimeMillis() + entry.intervalDays * DAY;
        commit(previous);
        if (resetJob != null) notifyPlayers(resetJob.evacuated, WorldMessageConfig.message(WorldMessageConfig.SUCCESS));
        resetJob = null;
    }

    private void persistEndFight() {
        // Dragon fight data lives in level.dat, outside the archived End dimension.
        var expected = server.getWorldData().createTag(server.registryAccess(), null).getCompound("DragonFight");
        ((WorldServerAccessor) server).neoworld$worldStorage()
                .saveDataTag(server.registryAccess(), server.getWorldData(), server.getPlayerList().getSingleplayerData());
        try {
            var saved = NbtIo.readCompressed(server.getWorldPath(LevelResource.ROOT).resolve("level.dat"), NbtAccounter.unlimitedHeap());
            if (!saved.getCompound("Data").getCompound("DragonFight").equals(expected)) {
                throw new IOException("End dragon fight data was not saved");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("บันทึกข้อมูล Ender Dragon ไม่สำเร็จ", exception);
        }
    }

    private void failWorld(String id, Exception exception) {
        failures.add(id);
        NeoWorld.LOGGER.error("Live world reset failed for {}; entry remains locked until restart", id, exception);
        Set<UUID> recipients = new HashSet<>();
        ServerLevel level = server.getLevel(key(id));
        if (level != null) level.players().forEach(player -> recipients.add(player.getUUID()));
        if (resetJob != null && resetJob.key.equals(key(id))) recipients.addAll(resetJob.evacuated);
        notifyPlayers(recipients, WorldMessageConfig.message(WorldMessageConfig.FAILURE));
    }

    private void notifyWorld(ResourceKey<Level> dimension, String message) {
        if (message.isBlank()) return;
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return;
        Component text = Component.literal(message);
        for (ServerPlayer player : List.copyOf(level.players())) player.sendSystemMessage(text);
    }

    private void notifyPlayers(Set<UUID> recipients, String message) {
        if (message.isBlank()) return;
        Component text = Component.literal(message);
        for (UUID id : recipients) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(text);
        }
    }
}
