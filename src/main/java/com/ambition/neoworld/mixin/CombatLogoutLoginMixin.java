package com.ambition.neoworld.mixin;

import com.ambition.neoworld.killer.CombatLogoutManager;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Duplicate online logins still leave a combat body while the new connection configures. */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class CombatLogoutLoginMixin {
    @Shadow @Final private MinecraftServer server;
    @Inject(method = "isPlayerAlreadyInWorld", at = @At("HEAD"), cancellable = true)
    private void neoworld$allowConfigurationAlongsideBody(GameProfile profile, CallbackInfoReturnable<Boolean> cir) {
        if (CombatLogoutManager.hasBody(server, profile.getId())) cir.setReturnValue(false);
    }
}
