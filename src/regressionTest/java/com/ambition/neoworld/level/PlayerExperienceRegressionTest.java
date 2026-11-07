package com.ambition.neoworld.level;

import com.ambition.neoworld.data.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

public final class PlayerExperienceRegressionTest {
    public static void main(String[] args) throws java.io.IOException {
        PlayerData reduced = new PlayerData();
        PlayerLevelManager.applyExp(reduced, 500);
        PlayerLevelManager.applyExpReduction(reduced, 200);
        check(reduced.getLevel() == 2 && reduced.getExp() == 200 && reduced.getTotalExp() == 300,
                "Removing EXP must reduce level/current EXP and cumulative EXP together");
        PlayerLevelManager.applyExpReduction(reduced, 200);
        check(reduced.getLevel() == 2 && reduced.getExp() == 0 && reduced.getTotalExp() == 100,
                "Exact level boundary must retain that level with zero current EXP");
        PlayerLevelManager.applyExpReduction(reduced, 1);
        check(reduced.getLevel() == 1 && reduced.getExp() == 99 && reduced.getTotalExp() == 99,
                "Crossing a level boundary by one EXP must reverse the level cost exactly");
        PlayerLevelManager.applyExpReduction(reduced, 0);
        PlayerLevelManager.applyExpReduction(reduced, -10);
        check(reduced.getExp() == 99 && reduced.getTotalExp() == 99, "Invalid reductions must be ignored");
        PlayerLevelManager.applyExpReduction(reduced, Long.MAX_VALUE);
        check(reduced.getLevel() == 1 && reduced.getExp() == 0 && reduced.getTotalExp() == 0,
                "Over-removal must stop at level one and zero EXP without underflow");
        PlayerData separatelySet = new PlayerData(50, 0, 0, 0, 0);
        PlayerLevelManager.applyExpReduction(separatelySet, 1);
        check(separatelySet.getLevel() == 49 && separatelySet.getExp() == LevelFormula.getRequiredExpForNextLevel(49) - 1
                        && separatelySet.getTotalExp() == 0,
                "Reduction after an admin set-level must use progression rather than infer level from cumulative EXP");
        PlayerData cappedReduction = new PlayerData(100, 0, 0, 0, 0, Long.MAX_VALUE);
        PlayerLevelManager.applyExpReduction(cappedReduction, 1);
        check(cappedReduction.getLevel() == 99 && cappedReduction.getExp() == LevelFormula.getRequiredExpForNextLevel(99) - 1
                        && cappedReduction.getTotalExp() == Long.MAX_VALUE - 1,
                "Capped level with zero current EXP must drop a level when progression EXP is removed");
        PlayerLevelManager.applyExpReduction(cappedReduction, Long.MAX_VALUE);
        check(cappedReduction.getLevel() == 1 && cappedReduction.getExp() == 0 && cappedReduction.getTotalExp() == 0,
                "Maximum reduction must safely cross all levels");
        PlayerData admin = new PlayerData();
        PlayerLevelManager.applyExp(admin, 500);
        check(admin.getLevel() == 3 && admin.getExp() == 118 && admin.getTotalExp() == 500,
                "Admin EXP must advance levels and cumulative EXP together");
        PlayerLevelManager.applyExp(admin, 7);
        check(admin.getTotalExp() == 507, "Gameplay and admin grants must both count");
        PlayerData adminCapped = new PlayerData(99, 0, 0, 0, 0);
        PlayerLevelManager.applyExp(adminCapped, Long.MAX_VALUE);
        check(adminCapped.getLevel() == 100 && adminCapped.getExp() == 0 && adminCapped.getTotalExp() == Long.MAX_VALUE,
                "Admin grants reaching cap must retain cumulative EXP");
        PlayerLevelManager.applyExp(adminCapped, 100);
        PlayerLevelManager.applyExp(adminCapped, 9);
        check(adminCapped.getTotalExp() == Long.MAX_VALUE, "Combined grants at cap must saturate safely");
        PlayerData data = new PlayerData();
        data.addExp(250);
        CompoundTag saved = (CompoundTag) PlayerData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        check(saved.getLong("total_exp") == 250, "Awarded EXP must be saved in a separate cumulative counter");
        check(!saved.contains("total_exp_tracking_started_at"), "Player saves must no longer store a tracking date");
        check(data.getTotalExp() == 250, "Online getter must expose received EXP");
        PlayerData restored = PlayerData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        check(restored.getTotalExp() == 250, "Offline decode must preserve total without re-awarding EXP");
        PlayerData.CODEC.encodeStart(NbtOps.INSTANCE, restored).getOrThrow();
        check(restored.getTotalExp() == 250, "Saving again must not count EXP twice");

        PlayerData progressing = new PlayerData();
        check(PlayerLevelManager.applyExp(progressing, 500) == 2, "One grant must advance multiple levels");
        check(progressing.getLevel() == 3 && progressing.getExp() == 118 && progressing.getTotalExp() == 500,
                "Level costs must reduce only current EXP, not total received");
        progressing.setLevel(1);
        progressing.setExp(0);
        check(progressing.getTotalExp() == 500, "Admin level/current-EXP reset must preserve cumulative EXP");
        PlayerLevelManager.applyExp(progressing, 100);
        check(progressing.getTotalExp() == 600, "Later grants must count exactly once after a reset");

        PlayerData capped = new PlayerData(99, 0, 0, 0, 0);
        long lastCost = LevelFormula.getRequiredExpForNextLevel(99);
        PlayerLevelManager.applyExp(capped, lastCost + 77);
        check(capped.getLevel() == 100 && capped.getExp() == 0 && capped.getTotalExp() == lastCost + 77,
                "Full grant including excess EXP must be counted when reaching cap");
        PlayerLevelManager.applyExp(capped, 123);
        check(capped.getLevel() == 100 && capped.getExp() == 0 && capped.getTotalExp() == lastCost + 200,
                "Capped players must continue accumulating without gaining levels or filling the EXP bar");
        PlayerLevelManager.applyExp(capped, Long.MAX_VALUE);
        PlayerLevelManager.applyExp(capped, 1);
        check(capped.getTotalExp() == Long.MAX_VALUE && capped.getExp() == 0, "Cumulative overflow must saturate safely");
        PlayerLevelManager.applyExp(capped, 0);
        PlayerLevelManager.applyExp(capped, -1);
        check(capped.getTotalExp() == Long.MAX_VALUE, "Invalid amounts must not remove EXP");

        CompoundTag legacy = new CompoundTag();
        legacy.putInt("level", 40);
        legacy.putLong("exp", 999);
        legacy.putLong("silver", 55);
        PlayerData old = PlayerData.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        check(old.getLevel() == 40 && old.getExp() == 999 && old.getSilver() == 55,
                "Migration must preserve existing progression and money");
        check(old.getTotalExp() == 0, "Reading a legacy offline save must not invent past EXP");
        PlayerLevelManager.applyExp(old, 7);
        check(old.getTotalExp() == 7, "Legacy current EXP must not be counted as newly received EXP");
        legacy.putLong("total_exp", -1);
        check(PlayerData.CODEC.parse(NbtOps.INSTANCE, legacy).error().isPresent(), "Negative saved totals must be rejected");
        var offlineDirectory = java.nio.file.Files.createDirectories(java.nio.file.Path.of("build", "player-exp-regression"));
        var offlineFile = java.nio.file.Files.createTempFile(offlineDirectory, "player-", ".dat");
        var playerSave = new CompoundTag();
        var attachments = new CompoundTag();
        attachments.put("neoworld:player_data", saved);
        playerSave.put("neoforge:attachments", attachments);
        net.minecraft.nbt.NbtIo.writeCompressed(playerSave, offlineFile);
        var offlineRoot = net.minecraft.nbt.NbtIo.readCompressed(offlineFile, net.minecraft.nbt.NbtAccounter.create(1024 * 1024));
        var offlineData = PlayerData.CODEC.parse(NbtOps.INSTANCE,
                offlineRoot.getCompound("neoforge:attachments").getCompound("neoworld:player_data")).getOrThrow();
        check(offlineData.getTotalExp() == 250,
                "Compressed offline player save must expose the same total as online data");
        saved.putLong("total_exp_tracking_started_at", 123456789L);
        PlayerData previousFormat = PlayerData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        CompoundTag rewritten = (CompoundTag) PlayerData.CODEC.encodeStart(NbtOps.INSTANCE, previousFormat).getOrThrow();
        check(previousFormat.getTotalExp() == 250 && !rewritten.contains("total_exp_tracking_started_at"),
                "Previous format must retain EXP and discard its obsolete date on save");
        System.out.println("Player cumulative EXP regression checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
