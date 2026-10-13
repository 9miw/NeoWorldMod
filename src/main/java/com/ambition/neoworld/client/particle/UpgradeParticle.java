package com.ambition.neoworld.client.particle;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = NeoWorld.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class UpgradeParticle extends TextureSheetParticle {
    private final float baseAlpha;
    private final boolean rune;

    private UpgradeParticle(ClientLevel level, double x, double y, double z, double r, double g, double b,
                            SpriteSet sprites, boolean rune) {
        super(level, x, y, z);
        this.rune = rune;
        setSprite(sprites.get(random));
        setColor((float) r, (float) g, (float) b);
        lifetime = rune ? 24 : 12;
        quadSize = rune ? 0.085F : 0.035F;
        baseAlpha = rune ? 0.65F : 0.85F;
        alpha = 0;
        hasPhysics = false;
        xd = (random.nextDouble() - 0.5) * 0.01;
        yd = rune ? 0.012 : 0.02;
        zd = (random.nextDouble() - 0.5) * 0.01;
    }

    @Override public void tick() {
        super.tick();
        float progress = age / (float) lifetime;
        alpha = baseAlpha * (float) Math.sin(Math.PI * progress);
        if (rune) alpha *= 0.85F + 0.15F * (float) Math.sin(age * 0.8F);
    }
    @Override public int getLightColor(float partialTick) { return 0xF000F0; }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.UPGRADE_SPARK.get(), sprites ->
                (type, level, x, y, z, r, g, b) -> new UpgradeParticle(level, x, y, z, r, g, b, sprites, false));
        event.registerSpriteSet(ModParticles.UPGRADE_RUNE.get(), sprites ->
                (type, level, x, y, z, r, g, b) -> new UpgradeParticle(level, x, y, z, r, g, b, sprites, true));
    }
}
