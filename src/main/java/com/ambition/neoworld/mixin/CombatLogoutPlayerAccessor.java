package com.ambition.neoworld.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerPlayer.class)
public interface CombatLogoutPlayerAccessor {
    @Accessor("spawnInvulnerableTime") int neoworld$getSpawnInvulnerableTime();
    @Accessor("spawnInvulnerableTime") void neoworld$setSpawnInvulnerableTime(int ticks);
}
