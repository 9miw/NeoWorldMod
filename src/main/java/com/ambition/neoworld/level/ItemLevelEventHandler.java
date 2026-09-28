package com.ambition.neoworld.level;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = NeoWorld.MODID)
public class ItemLevelEventHandler {
    private static final Map<Player, Long> LAST_DENIAL_TICK = new WeakHashMap<>();

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (rejectUse(event.getEntity(), event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (rejectUse(event.getEntity(), event.getItemStack())) {
            // Keep the block interaction available (for example, opening a chest),
            // but prevent the under-level item from acting on the block.
            event.setUseItem(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (rejectUse(event.getEntity(), event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (rejectUse(event.getEntity(), event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (rejectUse(event.getEntity(), event.getEntity().getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onIncomingMeleeDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof Player player
                && rejectUse(player, player.getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && rejectUse(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        }) {
            ItemStack armor = player.getItemBySlot(slot);
            if (armor.isEmpty() || getRequiredLevel(armor) <= 0 || canUse(player, armor)) {
                continue;
            }

            ItemStack removed = armor.copy();
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(removed)) {
                player.drop(removed, false);
            }
            rejectUse(player, removed);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        int requiredLevel = getRequiredLevel(event.getItemStack());
        if (requiredLevel > 0) {
            event.getToolTip().add(Component.literal("ต้องการเลเวล " + requiredLevel).withStyle(ChatFormatting.GOLD));
        }
    }

    private static boolean canUse(Player player, ItemStack stack) {
        int requiredLevel = getRequiredLevel(stack);
        if (requiredLevel <= 0) {
            return true;
        }

        if (!(player instanceof ServerPlayer) || !player.hasData(ModAttachments.PLAYER_DATA)) {
            return false;
        }

        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        return data.getLevel() >= requiredLevel;
    }

    private static boolean rejectUse(Player player, ItemStack stack) {
        if (player.level().isClientSide() || stack.isEmpty() || getRequiredLevel(stack) <= 0 || canUse(player, stack)) {
            return false;
        }

        long currentTick = player.level().getGameTime();
        Long lastTick = LAST_DENIAL_TICK.put(player, currentTick);
        if (lastTick == null || lastTick != currentTick) {
            int requiredLevel = getRequiredLevel(stack);
            player.sendSystemMessage(Component.literal("§c[NeoWorld] §fต้องมีเลเวล §e" + requiredLevel + " §fเพื่อใช้ไอเทมนี้"));
        }
        return true;
    }

    public static int getRequiredLevel(ItemStack stack) {
        Integer stackRequiredLevel = stack.get(ModDataComponents.REQUIRED_LEVEL);
        return stackRequiredLevel != null ? stackRequiredLevel : Config.getRequiredLevel(stack.getItem());
    }
}
