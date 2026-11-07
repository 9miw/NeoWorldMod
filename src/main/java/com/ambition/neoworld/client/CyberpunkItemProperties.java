package com.ambition.neoworld.client;

import com.ambition.neoworld.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ChargedProjectiles;

/** Vanilla model predicates for the custom Cyberpunk equipment. */
public final class CyberpunkItemProperties {
    private CyberpunkItemProperties() {}

    public static void register() {
        ItemProperties.register(ModItems.CYBERPUNK_BOW.get(), id("pull"),
                (stack, level, entity, seed) -> entity != null && entity.getUseItem() == stack
                        ? (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0f : 0.0f);
        ItemProperties.register(ModItems.CYBERPUNK_BOW.get(), id("pulling"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0);
        ItemProperties.register(ModItems.CYBERPUNK_CROSSBOW.get(), id("pull"),
                (stack, level, entity, seed) -> entity != null && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack)
                        ? (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / (float) CrossbowItem.getChargeDuration(stack, entity) : 0);
        ItemProperties.register(ModItems.CYBERPUNK_CROSSBOW.get(), id("pulling"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack) ? 1 : 0);
        ItemProperties.register(ModItems.CYBERPUNK_CROSSBOW.get(), id("charged"),
                (stack, level, entity, seed) -> CrossbowItem.isCharged(stack) ? 1 : 0);
        ItemProperties.register(ModItems.CYBERPUNK_CROSSBOW.get(), id("firework"),
                (stack, level, entity, seed) -> stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).contains(Items.FIREWORK_ROCKET) ? 1 : 0);
        ItemProperties.register(ModItems.CYBERPUNK_SHIELD.get(), id("blocking"),
                (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1 : 0);
        ItemProperties.register(ModItems.CYBERPUNK_FISHING_ROD.get(), id("cast"), (stack, level, entity, seed) -> {
            if (!(entity instanceof net.minecraft.world.entity.player.Player player) || player.fishing == null) return 0;
            boolean main = player.getMainHandItem() == stack;
            boolean off = player.getOffhandItem() == stack;
            if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem) off = false;
            return main || off ? 1 : 0;
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
}
