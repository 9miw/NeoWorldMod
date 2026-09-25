package com.ambition.neoworld.level;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public class ItemLevelEventHandler {

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!player.level().isClientSide() && !canUse(player, stack)) {
            int requiredLevel = Config.getRequiredLevel(stack.getItem());
            player.sendSystemMessage(Component.literal("§c[NeoWorld] §fต้องมีเลเวล §e" + requiredLevel + " §fเพื่อใช้ไอเทมนี้"));
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        int requiredLevel = Config.getRequiredLevel(event.getItemStack().getItem());
        if (requiredLevel > 0) {
            event.getToolTip().add(Component.literal("ต้องการเลเวล " + requiredLevel).withStyle(ChatFormatting.GOLD));
        }
    }

    private static boolean canUse(Player player, ItemStack stack) {
        int requiredLevel = Config.getRequiredLevel(stack.getItem());
        if (requiredLevel <= 0) {
            return true;
        }

        if (!(player instanceof ServerPlayer) || !player.hasData(ModAttachments.PLAYER_DATA)) {
            return false;
        }

        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        return data.getLevel() >= requiredLevel;
    }
}
