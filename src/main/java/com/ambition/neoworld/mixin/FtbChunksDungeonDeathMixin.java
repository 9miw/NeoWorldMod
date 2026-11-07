package com.ambition.neoworld.mixin;

import com.ambition.neoworld.dungeon.room.DungeonDeathWaypoints;
import dev.ftb.mods.ftbchunks.FTBChunks;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FTBChunks.class, remap = false)
public abstract class FtbChunksDungeonDeathMixin {
    @Inject(method = "playerCloned(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerPlayer;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void neoworld$skipDungeonDeathWaypoint(ServerPlayer original, ServerPlayer player,
            boolean alive, CallbackInfo ci) {
        // FTB only sends a death-waypoint packet here. Keep vanilla death data intact.
        if (!alive && player.getLastDeathLocation().filter(DungeonDeathWaypoints::suppress).isPresent()) {
            ci.cancel();
        }
    }
}
