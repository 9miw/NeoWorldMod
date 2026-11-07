package com.ambition.neoworld.killer;

import dev.ftb.mods.ftbchunks.data.PvPMode;
import com.mojang.serialization.JsonOps;

final class KillerSafeZoneRegressionTest {
    static void run() {
        var data = new KillerData(100, 11_999);
        for (int i = 0; i < 24_000; i++)
            check(!data.tickOnline(false), "A protected zone never triggers sin decay");
        check(data.getSin() == 100 && data.getOnlineTicks() == 11_999,
                "Entering protection just before a decay boundary must pause without resetting progress");
        var saved = KillerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var restored = KillerData.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        check(!restored.tickOnline(false) && restored.getOnlineTicks() == 11_999,
                "Reloading a protected player preserves the paused partial timer");
        check(restored.tickOnline(true) && restored.getSin() == 90 && restored.getOnlineTicks() == 0,
                "First eligible tick after leaving protection resumes at the exact stored boundary");
        var partial = new KillerData(100, 500);
        partial.tickOnline(false);
        partial.tickOnline(true);
        partial.tickOnline(false);
        check(partial.getSin() == 100 && partial.getOnlineTicks() == 501,
                "Repeated border crossings count only ticks outside protection");

        for (PvPMode mode : PvPMode.values())
            check(!FtbChunksKillerZones.isPvpProtected(mode, false, false), "Unclaimed land is never protected by the claim PvP policy");
        check(!FtbChunksKillerZones.isPvpProtected(PvPMode.ALWAYS, true, false), "ALWAYS ignores a team's disabled PvP setting");
        check(FtbChunksKillerZones.isPvpProtected(PvPMode.NEVER, true, true), "NEVER protects claims even if their team allows PvP");
        check(FtbChunksKillerZones.isPvpProtected(PvPMode.PER_TEAM, true, false), "PER_TEAM pauses decay when the claim team forbids PvP");
        check(!FtbChunksKillerZones.isPvpProtected(PvPMode.PER_TEAM, true, true), "PER_TEAM permits decay in a PvP-enabled claim");
        System.out.println("Killer FTB safe-zone pause/resume regression checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
