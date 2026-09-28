package com.ambition.neoworld.dungeon.structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/** Clears dungeon template bounds incrementally to avoid doing large edits in one tick. */
public final class DungeonRemovalManager {
    private static final int BLOCKS_PER_TICK = 4_096;
    private static final Map<MinecraftServer, DungeonRemovalManager> MANAGERS = new WeakHashMap<>();

    private final Map<String, RemovalJob> jobs = new LinkedHashMap<>();

    private DungeonRemovalManager() {
    }

    public static synchronized DungeonRemovalManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, ignored -> new DungeonRemovalManager());
    }

    public StartResult start(
            ServerLevel level,
            DungeonTemplateLayout layout,
            int slotIndex,
            BlockPos slotOrigin,
            Consumer<Component> completionMessage
    ) {
        String jobId = layout.dungeonId() + ":" + slotIndex;
        if (jobs.containsKey(jobId)) {
            return StartResult.failure("กำลังล้าง slot นี้อยู่แล้ว");
        }

        List<BoundingBox> bounds = new ArrayList<>();
        long totalBlocks = 0L;
        for (DungeonTemplatePiece piece : layout.pieces()) {
            StructureTemplate template = level.getStructureManager().get(piece.templateId()).orElse(null);
            if (template == null) {
                return StartResult.failure("Missing structure template: " + piece.templateId());
            }
            BlockPos pieceOrigin = slotOrigin.offset(piece.offset());
            BoundingBox box = template.getBoundingBox(new StructurePlaceSettings(), pieceOrigin);
            bounds.add(box);
            totalBlocks += (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        }

        boolean hasPlayers = bounds.stream().anyMatch(box ->
                !level.getEntitiesOfClass(ServerPlayer.class, AABB.of(box)).isEmpty());
        if (hasPlayers) {
            return StartResult.failure("มีผู้เล่นอยู่ภายในพื้นที่ดันเจี้ยน");
        }

        int removedEntities = removeNonPlayerEntities(level, bounds);
        jobs.put(jobId, new RemovalJob(
                jobId,
                level,
                bounds,
                totalBlocks,
                completionMessage
        ));
        return StartResult.success(totalBlocks, removedEntities);
    }

    public void tick() {
        int remainingBudget = BLOCKS_PER_TICK;
        var iterator = jobs.values().iterator();
        while (iterator.hasNext() && remainingBudget > 0) {
            RemovalJob job = iterator.next();
            int cleared = job.clear(remainingBudget);
            remainingBudget -= cleared;
            if (job.isComplete()) {
                removeNonPlayerEntities(job.level(), job.originalBounds());
                job.completionMessage().accept(Component.literal(String.format(
                        "§a[NeoWorld] ล้างดันเจี้ยน %s สำเร็จ: %,d บล็อก",
                        job.id(),
                        job.totalBlocks()
                )));
                iterator.remove();
            }
        }
    }

    private static int removeNonPlayerEntities(ServerLevel level, List<BoundingBox> bounds) {
        int removed = 0;
        for (BoundingBox box : bounds) {
            for (Entity entity : level.getEntities(
                    (Entity) null,
                    AABB.of(box),
                    entity -> !(entity instanceof ServerPlayer)
            )) {
                if (!entity.isRemoved()) {
                    entity.discard();
                    removed++;
                }
            }
        }
        return removed;
    }

    public record StartResult(boolean started, long blockCount, int removedEntityCount, String error) {
        private static StartResult success(long blockCount, int removedEntityCount) {
            return new StartResult(true, blockCount, removedEntityCount, "");
        }

        private static StartResult failure(String error) {
            return new StartResult(false, 0L, 0, error);
        }
    }

    private static final class RemovalJob {
        private final String id;
        private final ServerLevel level;
        private final List<BoundingBox> originalBounds;
        private final Deque<BoundingBox> remainingBounds;
        private final long totalBlocks;
        private final Consumer<Component> completionMessage;
        private BoundingBox current;
        private int x;
        private int y;
        private int z;

        private RemovalJob(
                String id,
                ServerLevel level,
                List<BoundingBox> bounds,
                long totalBlocks,
                Consumer<Component> completionMessage
        ) {
            this.id = id;
            this.level = level;
            this.originalBounds = List.copyOf(bounds);
            this.remainingBounds = new ArrayDeque<>(bounds);
            this.totalBlocks = totalBlocks;
            this.completionMessage = completionMessage;
            loadNextBounds();
        }

        private int clear(int budget) {
            int cleared = 0;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            while (current != null && cleared < budget) {
                level.setBlock(cursor.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                cleared++;
                advanceCursor();
            }
            return cleared;
        }

        private void advanceCursor() {
            if (++x <= current.maxX()) return;
            x = current.minX();
            if (++z <= current.maxZ()) return;
            z = current.minZ();
            if (++y <= current.maxY()) return;
            loadNextBounds();
        }

        private void loadNextBounds() {
            current = remainingBounds.pollFirst();
            if (current != null) {
                x = current.minX();
                y = current.minY();
                z = current.minZ();
            }
        }

        private boolean isComplete() {
            return current == null;
        }

        private String id() {
            return id;
        }

        private ServerLevel level() {
            return level;
        }

        private List<BoundingBox> originalBounds() {
            return originalBounds;
        }

        private long totalBlocks() {
            return totalBlocks;
        }

        private Consumer<Component> completionMessage() {
            return completionMessage;
        }
    }
}
