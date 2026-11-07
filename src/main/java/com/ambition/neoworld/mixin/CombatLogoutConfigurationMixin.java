package com.ambition.neoworld.mixin;

import java.util.UUID;
import com.ambition.neoworld.killer.CombatLogoutManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the body vulnerable through configuration until placeNewPlayer can replace it atomically. */
@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class CombatLogoutConfigurationMixin {
    @Redirect(method = "handleConfigurationFinished", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;getPlayer(Ljava/util/UUID;)Lnet/minecraft/server/level/ServerPlayer;"))
    private ServerPlayer neoworld$ignoreOnlyRetainedDuplicate(PlayerList players, UUID id) {
        ServerPlayer existing = players.getPlayer(id);
        return existing != null && CombatLogoutManager.isRetained(existing) ? null : existing;
    }
}
