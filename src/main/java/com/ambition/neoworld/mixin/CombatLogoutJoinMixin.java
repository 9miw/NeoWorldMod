package com.ambition.neoworld.mixin;

import java.net.SocketAddress;
import com.mojang.authlib.GameProfile;
import com.ambition.neoworld.killer.CombatLogoutManager;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerList.class)
public abstract class CombatLogoutJoinMixin {
    @Shadow @Final private MinecraftServer server;

    @Inject(method = "disconnectAllPlayersWithProfile", at = @At("HEAD"), cancellable = true)
    private void neoworld$allowRetainedReconnect(GameProfile profile, CallbackInfoReturnable<Boolean> cir) {
        if (CombatLogoutManager.hasBody(server, profile.getId())) cir.setReturnValue(false);
    }

    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void neoworld$replaceOwnSlot(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
        Component rejection = cir.getReturnValue();
        // Preserve bans, whitelist, and all other admission decisions. The body already occupies this one slot.
        if (CombatLogoutManager.hasBody(server, profile.getId()) && rejection != null
                && rejection.getContents() instanceof TranslatableContents text
                && text.getKey().equals("multiplayer.disconnect.server_full")) cir.setReturnValue(null);
    }

    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void neoworld$saveBodyBeforeLoading(Connection connection, ServerPlayer incoming, CommonListenerCookie cookie, CallbackInfo ci) {
        CombatLogoutManager.prepareJoin(server, incoming);
    }
}
