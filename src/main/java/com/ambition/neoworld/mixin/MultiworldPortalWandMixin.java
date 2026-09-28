package com.ambition.neoworld.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "me.isaiah.multiworld.portal.WandEventHandler", remap = false)
public final class MultiworldPortalWandMixin {
    private static final String PORTAL_WAND_NAME = "Multiworld Portal Wand";

    @Inject(method = "getItemStack", at = @At("RETURN"), cancellable = true)
    private static void neoworld$markPortalWand(CallbackInfoReturnable<ItemStack> callback) {
        ItemStack wand = callback.getReturnValue().copy();
        wand.set(DataComponents.CUSTOM_NAME, Component.literal(PORTAL_WAND_NAME));
        callback.setReturnValue(wand);
    }

    @Inject(method = "isHoldingWand", at = @At("HEAD"), cancellable = true)
    private static void neoworld$requireMarkedPortalWand(
            Player player,
            CallbackInfoReturnable<Boolean> callback
    ) {
        ItemStack held = player.getMainHandItem();
        Component name = held.get(DataComponents.CUSTOM_NAME);
        callback.setReturnValue(held.is(Items.WOODEN_AXE)
                && name != null
                && PORTAL_WAND_NAME.equals(name.getString()));
    }
}
