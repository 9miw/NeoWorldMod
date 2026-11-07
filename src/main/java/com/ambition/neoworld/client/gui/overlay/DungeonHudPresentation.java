package com.ambition.neoworld.client.gui.overlay;

import com.ambition.neoworld.network.dungeon.DungeonHudPayload;

/** Read-only HUD labels and progress derived from the authoritative snapshot. */
public record DungeonHudPresentation(String status, String objective, String remaining,
        float progress, String time, String survivors) {
    public static DungeonHudPresentation from(DungeonHudPayload state) {
        String status = switch (state.phase()) {
            case WAITING_WAVE -> "เตรียมต่อสู้";
            case WAVE -> "กำลังต่อสู้";
            case WAITING_BOSS -> "เตรียมสู้บอส";
            case BOSS -> "สู้บอส";
            case REWARD -> "สำเร็จ";
        };
        boolean boss = state.phase() == DungeonHudPayload.Phase.BOSS;
        String objective = boss ? state.bossName().isBlank() ? "บอส" : state.bossName()
                : state.phase() == DungeonHudPayload.Phase.REWARD ? "รับรางวัลและไปจุดออก"
                : state.phase() == DungeonHudPayload.Phase.WAITING_BOSS || state.totalWaves() == 0 ? "เตรียมบอส"
                : "Wave " + state.wave() + " / " + state.totalWaves();
        String remaining = boss ? "HP " + state.bossPercent() + "%"
                : state.phase() == DungeonHudPayload.Phase.WAVE ? "ศัตรูเหลือ " + state.enemies() + " ตัว" : "";
        float progress = boss ? state.bossPercent() / 100F
                : state.phase() == DungeonHudPayload.Phase.REWARD || state.phase() == DungeonHudPayload.Phase.WAITING_BOSS ? 1F
                : state.totalWaves() == 0 ? 0F : Math.max(0, state.wave()-1) / (float) state.totalWaves();
        return new DungeonHudPresentation(status, objective, remaining, Math.clamp(progress,0F,1F),
                state.unlimited() ? "ไม่จำกัด" : "%02d:%02d".formatted(state.secondsRemaining()/60, state.secondsRemaining()%60),
                state.alive() + " / " + state.members());
    }
}
