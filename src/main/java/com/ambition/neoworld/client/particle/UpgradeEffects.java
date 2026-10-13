package com.ambition.neoworld.client.particle;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModParticles;
import com.ambition.neoworld.upgrade.UpgradeData;
import com.ambition.neoworld.upgrade.UpgradeEffectStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class UpgradeEffects {
    private UpgradeEffects() {}

    public static void emit(ClientLevel level, Vec3 point, UpgradeEffectStyle style, boolean rune) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || mc.player.distanceToSqr(point) > 24 * 24) return;
        SimpleParticleType type = rune ? ModParticles.UPGRADE_RUNE.get() : ModParticles.UPGRADE_SPARK.get();
        level.addParticle(type, point.x, point.y, point.z, style.red(), style.green(), style.blue());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        for (Player player : mc.level.players()) {
            if (!player.isAlive() || player.isInvisible() || player.isSpectator() || player.distanceToSqr(mc.player) > 24 * 24) continue;
            // Ambient third-person approximation. Exact attack points are supplied by Epic Fight's trail edges.
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            var style = UpgradeEffectStyle.forLevel(UpgradeData.level(player.getMainHandItem()));
            if (style.interval() == 0 || (player.tickCount + player.getId()) % style.interval() != 0) continue;
            Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
            Vec3 side = new Vec3(-forward.z, 0, forward.x)
                    .scale(player.getMainArm() == HumanoidArm.RIGHT ? 0.35 : -0.35);
            Vec3 point = player.position().add(0, 1.05, 0).add(side).add(forward.scale(0.45));
            emit(mc.level, point, style, false);
            if (style.runes() && player.tickCount % 3 == 0) emit(mc.level, point.add(0, 0.12, 0), style, true);
        }
    }
}
