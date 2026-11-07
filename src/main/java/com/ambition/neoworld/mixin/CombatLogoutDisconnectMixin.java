package com.ambition.neoworld.mixin;

import com.ambition.neoworld.killer.CombatLogoutManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CombatLogoutDisconnectMixin {
    @Shadow public ServerPlayer player;
    @Inject(method = "removePlayerFromWorld", at = @At("HEAD"))
    private void neoworld$startCombatBody(CallbackInfo ci) { CombatLogoutManager.begin(player); }

    @Redirect(method = "removePlayerFromWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;disconnect()V"))
    private void neoworld$keepCombatBodyTicking(ServerPlayer player) {
        if (!CombatLogoutManager.isRetained(player)) player.disconnect();
    }

    @Redirect(method = "removePlayerFromWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;remove(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void neoworld$deferCombatRemoval(PlayerList players, ServerPlayer player) {
        if (!CombatLogoutManager.isRetained(player)) players.remove(player);
    }
}
