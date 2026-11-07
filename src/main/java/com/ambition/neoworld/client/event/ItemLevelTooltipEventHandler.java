package com.ambition.neoworld.client.event;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.level.ItemLevelEventHandler;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class ItemLevelTooltipEventHandler {
    private ItemLevelTooltipEventHandler() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        int requiredLevel = ItemLevelEventHandler.getRequiredLevel(stack);
        if (requiredLevel > 0) {
            event.getToolTip().add(Component.literal("ต้องการเลเวล " + requiredLevel).withStyle(ChatFormatting.GOLD));
        }
    }
}
