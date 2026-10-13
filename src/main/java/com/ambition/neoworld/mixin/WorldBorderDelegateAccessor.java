package com.ambition.neoworld.mixin;

import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BorderChangeListener.DelegateBorderChangeListener.class)
public interface WorldBorderDelegateAccessor {
    @Accessor("worldBorder")
    WorldBorder neoworld$delegateBorder();
}
