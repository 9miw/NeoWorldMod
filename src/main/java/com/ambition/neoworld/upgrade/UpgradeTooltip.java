package com.ambition.neoworld.upgrade;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class UpgradeTooltip {
    private UpgradeTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (!stack.has(ModDataComponents.UPGRADE_LEVEL.get())) return;
        event.getToolTip().add(Component.literal("ตีบวก +" + UpgradeData.level(stack))
                .withStyle(ChatFormatting.GOLD));
        int failures = UpgradeData.failures(stack);
        if (failures > 0) {
            event.getToolTip().add(Component.literal("แต้มสะสมจากการพลาด: " + failures)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
