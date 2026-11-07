package com.ambition.neoworld.dungeon.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class DungeonOverlapRegressionTest {
    private static final BlockPos POSITION = new BlockPos(12, 65, -7);
    private static final String FIRST = "test:mine_01", SECOND = "test:mine_02";

    public static void main(String[] args) {
        identicalBlocks();
        airInEitherOrder();
        incompatibleBlocks();
        blockEntityData();
        markerRules();
        System.out.println("Dungeon overlap regression checks passed");
    }

    private static void identicalBlocks() {
        var plan = new DungeonOverlapPlan<String>();
        check(plan.addBlock(0, FIRST, POSITION, "stone", false, null) == null, "First stone accepted");
        check(plan.addBlock(1, SECOND, POSITION, "stone", false, null) == null, "Matching duplicate accepted");
        check(plan.allows(0, POSITION) && !plan.allows(1, POSITION), "Identical stone is placed once");
        check(plan.allows(1, POSITION.offset(1, 0, 0)), "Unclaimed cells retain normal placement");
    }

    private static void airInEitherOrder() {
        for (boolean airFirst : new boolean[] {false, true}) {
            var plan = new DungeonOverlapPlan<String>();
            int air = airFirst ? 0 : 1, solid = airFirst ? 1 : 0;
            if (airFirst) check(plan.addBlock(air, FIRST, POSITION, "air", true, null) == null, "Air accepted");
            check(plan.addBlock(solid, SECOND, POSITION, "stone", false, null) == null, "Solid accepted");
            if (!airFirst) check(plan.addBlock(air, FIRST, POSITION, "air", true, null) == null, "Later air accepted");
            check(plan.allows(solid, POSITION) && !plan.allows(air, POSITION), "Solid wins regardless of order");
        }
        var air = new DungeonOverlapPlan<String>();
        air.addBlock(0, FIRST, POSITION, "air", true, null);
        air.addBlock(1, SECOND, POSITION, "cave_air", true, null);
        check(air.allows(0, POSITION) && !air.allows(1, POSITION), "Air-only overlap clears once");
    }

    private static void incompatibleBlocks() {
        var plan = new DungeonOverlapPlan<String>();
        plan.addBlock(0, FIRST, POSITION, "stairs[facing=north]", false, null);
        String error = plan.addBlock(1, SECOND, POSITION, "stairs[facing=south]", false, null);
        check(error != null && error.contains(FIRST) && error.contains(SECOND) && error.contains(POSITION.toShortString()),
                "Different properties fail with both names and coordinates");
        check(plan.allows(0, POSITION) && !plan.allows(1, POSITION), "Conflict does not replace first claim");
    }

    private static void blockEntityData() {
        var plan = new DungeonOverlapPlan<String>();
        CompoundTag first = new CompoundTag(); first.putString("Text", "Keep me"); first.putInt("x", 12);
        CompoundTag second = first.copy(); second.putInt("x", 100);
        check(plan.addBlock(0, FIRST, POSITION, "sign", false, first) == null, "First sign accepted");
        first.putString("Text", "Changed after planning");
        check(plan.addBlock(1, SECOND, POSITION, "sign", false, second) == null, "Position tags do not create false NBT conflicts");
        second.putString("Text", "Different text");
        check(plan.addBlock(2, "test:mine_03", POSITION, "sign", false, second) != null, "Different sign/chest content must fail");
        check(second.contains("x"), "Normalization must not mutate source NBT");
    }

    private static void markerRules() {
        var plan = new DungeonOverlapPlan<String>();
        check(plan.addMarker(FIRST, POSITION, "player_spawn") == null, "First marker accepted");
        check(plan.addMarker(SECOND, POSITION, "player_spawn") == null, "Same-position duplicate accepted");
        check(plan.markers().get("player_spawn").size() == 1, "Same-position marker counts once");
        plan.addMarker(SECOND, POSITION.offset(1, 0, 0), "player_spawn");
        check(plan.markers().get("player_spawn").size() == 2, "Same name at different positions remains distinct");
        check(plan.addMarker(SECOND, POSITION, "exit") != null, "Different markers at the same position fail");
        check(plan.addBlock(0, FIRST, POSITION, "air", true, null) == null, "Marker may overlap air");
        check(plan.addBlock(1, SECOND, POSITION, "stone", false, null) != null, "Marker cannot end inside a solid block");
        var reversed = new DungeonOverlapPlan<String>();
        reversed.addBlock(0, FIRST, POSITION, "stone", false, null);
        check(reversed.addMarker(SECOND, POSITION, "exit") != null, "Solid/marker conflict is order independent");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
