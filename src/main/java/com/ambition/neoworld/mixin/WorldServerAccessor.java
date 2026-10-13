package com.ambition.neoworld.mixin;

import java.util.concurrent.Executor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Inputs required to recreate a registered dimension without restarting the server. */
@Mixin(MinecraftServer.class)
public interface WorldServerAccessor {
    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess neoworld$worldStorage();

    @Accessor("executor")
    Executor neoworld$worldExecutor();
}
