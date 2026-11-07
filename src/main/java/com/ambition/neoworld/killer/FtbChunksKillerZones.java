package com.ambition.neoworld.killer;

import dev.ftb.mods.ftbchunks.FTBChunksWorldConfig;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.data.PvPMode;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.server.level.ServerPlayer;

/** Reads the current claim policy; never changes FTB protection or player/team permissions. */
final class FtbChunksKillerZones {
    private FtbChunksKillerZones() {}

    static boolean isPvpProtected(ServerPlayer player) {
        PvPMode mode = FTBChunksWorldConfig.PVP_MODE.get();
        if (mode == PvPMode.ALWAYS) return false;
        var api = FTBChunksAPI.api();
        // Do not award decay time while the protection manager is not ready to answer.
        if (!api.isManagerLoaded()) return true;
        var claim = api.getManager().getChunk(new ChunkDimPos(player.level(), player.blockPosition()));
        if (claim == null) return false;
        return isPvpProtected(mode, true, mode != PvPMode.PER_TEAM || claim.getTeamData().allowPVP());
    }

    static boolean isPvpProtected(PvPMode mode, boolean claimed, boolean teamAllowsPvp) {
        return claimed && (mode == PvPMode.NEVER || mode == PvPMode.PER_TEAM && !teamAllowsPvp);
    }
}
