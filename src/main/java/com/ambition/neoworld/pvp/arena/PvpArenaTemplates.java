package com.ambition.neoworld.pvp.arena;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Reads PvP DATA markers without depending on dungeon code. */
public final class PvpArenaTemplates {
    public static final List<String> MARKERS = List.of(
            "pvp_spawn_a", "pvp_spawn_b");

    private PvpArenaTemplates() { }

    public record Layout(StructureTemplate template, Map<String, BlockPos> markers) {
        public Layout { markers = Map.copyOf(markers); }
        public BlockPos point(String name, BlockPos origin) { return origin.offset(markers.get(name)); }
        public PvpArenaBounds bounds() { var size=template.getSize(); return new PvpArenaBounds(size.getX(),size.getY(),size.getZ()); }
        public boolean contains(BlockPos origin,double x,double y,double z) { return bounds().contains(x-origin.getX(),y-origin.getY(),z-origin.getZ()); }
    }

    public static Layout inspect(ServerLevel level, ResourceLocation id) {
        StructureTemplate template = level.getStructureManager().get(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing structure: " + id));
        var size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            throw new IllegalArgumentException("Structure is empty: " + id);
        }
        Map<String, BlockPos> markers = new LinkedHashMap<>();
        for (var info : template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.STRUCTURE_BLOCK)) {
            if (info.nbt() == null || !"DATA".equals(info.nbt().getString("mode"))) continue;
            String name = info.nbt().getString("metadata").trim();
            if (!MARKERS.contains(name)) continue;
            if (markers.putIfAbsent(name, info.pos().immutable()) != null) {
                throw new IllegalArgumentException("Duplicate DATA marker: " + name);
            }
        }
        for (String name : MARKERS) {
            if (!markers.containsKey(name)) throw new IllegalArgumentException("Missing DATA marker: " + name);
        }
        var bounds=new PvpArenaBounds(size.getX(),size.getY(),size.getZ());
        BlockPos a = markers.get("pvp_spawn_a"), b = markers.get("pvp_spawn_b");
        if (a.equals(b)) throw new IllegalArgumentException("Spawn points must be different");
        for (BlockPos spawn : List.of(a, b)) {
            if (!bounds.spawnFits(spawn.getX(),spawn.getY(),spawn.getZ())) {
                throw new IllegalArgumentException("Spawn points need a floor and two blocks of height inside the saved structure");
            }
        }
        return new Layout(template, markers);
    }

    /** For the future room allocator; caller owns the destination and slot lifecycle. */
    public static Layout place(ServerLevel level, ResourceLocation id, BlockPos origin) {
        Layout layout = inspect(level, id);
        if (origin.getY() < level.getMinBuildHeight()
                || (long) origin.getY() + layout.template().getSize().getY() > level.getMaxBuildHeight()) {
            throw new IllegalArgumentException("Arena exceeds dimension build height");
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setIgnoreEntities(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (!layout.template().placeInWorld(level, origin, origin, settings, level.getRandom(), 2)) {
            throw new IllegalStateException("Failed to place PvP arena: " + id);
        }
        // DATA blocks are metadata only; clear their cells so old terrain cannot obstruct spawn points.
        for (BlockPos marker : layout.markers().values()) level.setBlock(origin.offset(marker), Blocks.AIR.defaultBlockState(), 2);
        return layout;
    }
}
