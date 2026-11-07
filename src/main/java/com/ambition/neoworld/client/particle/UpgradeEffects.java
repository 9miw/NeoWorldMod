package com.ambition.neoworld.client.particle;

import com.ambition.neoworld.registry.ModParticles;
import com.ambition.neoworld.upgrade.UpgradeEffectStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;

public final class UpgradeEffects {
    private UpgradeEffects() {}

    public static void emit(ClientLevel level, Vec3 point, UpgradeEffectStyle style, boolean rune) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || mc.player.distanceToSqr(point) > 24 * 24) return;
        SimpleParticleType type = rune ? ModParticles.UPGRADE_RUNE.get() : ModParticles.UPGRADE_SPARK.get();
        level.addParticle(type, point.x, point.y, point.z, style.red(), style.green(), style.blue());
    }

}
