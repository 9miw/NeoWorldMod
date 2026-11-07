package com.ambition.neoworld.mixin;

import com.ambition.neoworld.killer.CombatLogoutManager;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A closed channel must not accumulate a minute of entity updates in Connection's outbound queue. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class CombatLogoutConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void neoworld$dropRetainedPackets(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game && CombatLogoutManager.isRetained(game.player)
                && !game.isAcceptingMessages()) ci.cancel();
    }
}
