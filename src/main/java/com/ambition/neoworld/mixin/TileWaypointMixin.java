package com.ambition.neoworld.mixin;

import com.ambition.neoworld.navigation.NavigationLocationDirectory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import noppes.npcs.blocks.tiles.TileWaypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TileWaypoint.class, remap = false)
public abstract class TileWaypointMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void neoworld$observeLocation(Level level, BlockPos pos, BlockState state,
            TileWaypoint waypoint, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel && level.getGameTime() % 20 == 0)
            NavigationLocationDirectory.observe(serverLevel, waypoint);
    }
}
