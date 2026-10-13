package com.ambition.neoworld.registry;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, NeoWorld.MODID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> UPGRADE_SPARK = PARTICLES.register("upgrade_spark", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> UPGRADE_RUNE = PARTICLES.register("upgrade_rune", () -> new SimpleParticleType(false));
    private ModParticles() {}
}
