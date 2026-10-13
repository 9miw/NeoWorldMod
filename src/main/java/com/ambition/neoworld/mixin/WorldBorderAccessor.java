package com.ambition.neoworld.mixin;

import java.util.List;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldBorder.class)
public interface WorldBorderAccessor {
    @Accessor("listeners")
    List<BorderChangeListener> neoworld$borderListeners();
}
