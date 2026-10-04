package com.ambition.neoworld.client.gui.navigation;

import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public record NeoScreenDestination(
        String id,
        Component title,
        Component description,
        Supplier<ItemStack> icon,
        boolean enabled,
        Runnable opener) {
}
