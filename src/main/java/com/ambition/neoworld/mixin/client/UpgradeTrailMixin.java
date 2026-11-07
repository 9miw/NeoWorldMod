package com.ambition.neoworld.mixin.client;

import com.ambition.neoworld.client.particle.UpgradeEffects;
import com.ambition.neoworld.upgrade.UpgradeData;
import com.ambition.neoworld.upgrade.UpgradeEffectStyle;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import yesman.epicfight.api.client.animation.property.TrailInfo;
import yesman.epicfight.client.particle.AbstractTrailParticle;
import yesman.epicfight.client.particle.AnimationTrailParticle;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import java.util.List;

/** Tint only per-particle fields; never alter shared TrailInfo or weapon capabilities. */
@Mixin(value = AbstractTrailParticle.class, remap = false)
public abstract class UpgradeTrailMixin extends TextureSheetParticle {
    @Shadow @Final @Mutable protected TrailInfo trailInfo;
    @Shadow @Final protected EntityPatch<?> owner;
    @Shadow @Final protected List<AbstractTrailParticle.TrailEdge> trailEdges;
    protected UpgradeTrailMixin(ClientLevel level, double x, double y, double z) { super(level, x, y, z); }

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;Lyesman/epicfight/world/capabilities/entitypatch/EntityPatch;Lyesman/epicfight/api/client/animation/property/TrailInfo;)V", at = @At("RETURN"))
    private void neoworld$trailLength(ClientLevel world, EntityPatch<?> patch, TrailInfo original, CallbackInfo ci) {
        // Constructor runs before AnimationTrailParticle's own fields are initialized; use only the owner and item.
        if (!((Object) this instanceof AnimationTrailParticle) || !(patch.getOriginal() instanceof Player player) || original.hand() != InteractionHand.MAIN_HAND
                || player.isInvisible() || player.isSpectator()) return;
        int upgrade = UpgradeData.level(player.getMainHandItem());
        if (upgrade < 10 || upgrade > 15) return;
        int lifetime = Math.min(12, Math.max(original.trailLifetime(), upgrade == 15 ? 10 : upgrade >= 13 ? 8 : 6));
        trailInfo = new TrailInfo(original.start(), original.end(), original.joint(), original.particle(),
                original.startTime(), original.endTime(), original.fadeTime(), original.rCol(), original.gCol(), original.bCol(),
                original.interpolateCount(), lifetime, original.updateInterval(), original.blockLight(), original.skyLight(),
                original.texturePath(), original.hand());
    }

    private UpgradeEffectStyle neoworld$style() {
        if (!((Object) this instanceof AnimationTrailParticle) || !(owner.getOriginal() instanceof Player player)
                || player.isInvisible() || player.isSpectator() || !player.isAlive() || trailInfo.hand() != InteractionHand.MAIN_HAND) {
            return UpgradeEffectStyle.forLevel(0);
        }
        return UpgradeEffectStyle.forLevel(UpgradeData.level(player.getMainHandItem()));
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void neoworld$tint(VertexConsumer consumer, Camera camera, float partialTick, CallbackInfo ci) {
        var style = neoworld$style();
        rCol = style.trail() ? style.red() : Math.max(0, trailInfo.rCol());
        gCol = style.trail() ? style.green() : Math.max(0, trailInfo.gCol());
        bCol = style.trail() ? style.blue() : Math.max(0, trailInfo.bCol());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void neoworld$spark(CallbackInfo ci) {
        var style = neoworld$style();
        if (style.interval() == 0 || age % style.interval() != 0 || trailEdges.isEmpty() || removed) return;
        var edge = trailEdges.get(trailEdges.size() - 1);
        UpgradeEffects.emit(level, edge.end, style, false);
        if (style.runes()) UpgradeEffects.emit(level, edge.end, style, true);
    }
}
