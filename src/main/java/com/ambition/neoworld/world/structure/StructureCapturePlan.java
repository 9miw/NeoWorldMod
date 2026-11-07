package com.ambition.neoworld.world.structure;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Inclusive selection partitioned into non-overlapping, standard-size structure templates. */
public record StructureCapturePlan(BlockPos minimum, BlockPos maximum, long volume, List<Part> parts) {
    public static final int EDGE = 48, MAX_PARTS = 512;
    public static final long MAX_VOLUME = 8_388_608L;
    public record Part(ResourceLocation id, BlockPos origin, BlockPos size, BlockPos offset) {}
    public StructureCapturePlan { parts = List.copyOf(parts); }

    public static StructureCapturePlan create(BlockPos first, BlockPos second, ResourceLocation prefix) {
        if (prefix.getPath().contains("..") || prefix.getPath().contains("//") || prefix.getPath().endsWith(".nbt")
                || prefix.toString().length() > 230) throw new IllegalArgumentException("Invalid structure name/prefix");
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        long sx = (long) max.getX() - min.getX() + 1, sy = (long) max.getY() - min.getY() + 1, sz = (long) max.getZ() - min.getZ() + 1;
        if (sx > MAX_VOLUME || sy > MAX_VOLUME || sz > MAX_VOLUME || sx * sy > MAX_VOLUME || sx * sy * sz > MAX_VOLUME)
            throw new IllegalArgumentException("Selection exceeds " + MAX_VOLUME + " blocks");
        long count = ((sx + EDGE - 1) / EDGE) * ((sy + EDGE - 1) / EDGE) * ((sz + EDGE - 1) / EDGE);
        if (count > MAX_PARTS) throw new IllegalArgumentException("Selection exceeds " + MAX_PARTS + " structure pieces");
        List<Part> parts = new ArrayList<>();
        for (int x = 0; x < sx; x += EDGE) for (int y = 0; y < sy; y += EDGE) for (int z = 0; z < sz; z += EDGE) {
            BlockPos offset = new BlockPos(x, y, z);
            parts.add(new Part(ResourceLocation.fromNamespaceAndPath(prefix.getNamespace(), prefix.getPath()
                    + String.format(java.util.Locale.ROOT, "_%02d", parts.size() + 1)), min.offset(offset),
                    new BlockPos((int) Math.min(EDGE, sx - x), (int) Math.min(EDGE, sy - y), (int) Math.min(EDGE, sz - z)), offset));
        }
        return new StructureCapturePlan(min, max, sx * sy * sz, parts);
    }
}
