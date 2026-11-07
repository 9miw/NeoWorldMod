package com.ambition.neoworld.world.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Pure preflight for overlap cells; S is the block-state value compared for equality. */
public final class StructureOverlapPlan<S> {
    private record BlockClaim<S>(int piece, String source, S state, boolean air, CompoundTag data) {}
    private record MarkerClaim(String source, String name) {}
    private final Map<BlockPos, BlockClaim<S>> blocks = new HashMap<>();
    private final Map<BlockPos, MarkerClaim> markers = new LinkedHashMap<>();

    public String addMarker(String source, BlockPos position, String name) {
        if (name.isBlank()) return null;
        MarkerClaim previous = markers.get(position);
        if (previous != null && !previous.name.equals(name))
            return conflict(position, previous.source, source, "DATA markers '" + previous.name + "' / '" + name + "'");
        BlockClaim<S> block = blocks.get(position);
        if (block != null && !block.air) return conflict(position, block.source, source, "solid block / DATA marker '" + name + "'");
        markers.putIfAbsent(position.immutable(), new MarkerClaim(source, name));
        return null;
    }

    public String addBlock(int piece, String source, BlockPos position, S state, boolean air, CompoundTag data) {
        MarkerClaim marker = markers.get(position);
        if (!air && marker != null) return conflict(position, marker.source, source, "DATA marker '" + marker.name + "' / solid block");
        BlockClaim<S> previous = blocks.get(position);
        if (previous != null && air) return null;
        if (previous != null && !previous.air) {
            if (!Objects.equals(previous.state, state) || !Objects.equals(previous.data, normalized(data)))
                return conflict(position, previous.source, source, "different block states or block-entity NBT: " + previous.state + " / " + state);
            return null;
        }
        blocks.put(position.immutable(), new BlockClaim<>(piece, source, state, air, normalized(data)));
        return null;
    }

    public boolean allows(int piece, BlockPos position) {
        BlockClaim<S> claim = blocks.get(position);
        return claim == null || claim.piece == piece;
    }

    public Map<String, List<BlockPos>> markers() {
        Map<String, List<BlockPos>> result = new LinkedHashMap<>();
        markers.forEach((pos, marker) -> result.computeIfAbsent(marker.name, ignored -> new ArrayList<>()).add(pos));
        return result;
    }

    private static CompoundTag normalized(CompoundTag data) {
        if (data == null) return null;
        CompoundTag copy = data.copy();
        copy.remove("x"); copy.remove("y"); copy.remove("z");
        return copy;
    }

    private static String conflict(BlockPos pos, String first, String second, String detail) {
        return "Structure overlap conflict at " + pos.toShortString() + " between " + first + " and " + second + ": " + detail;
    }
}
