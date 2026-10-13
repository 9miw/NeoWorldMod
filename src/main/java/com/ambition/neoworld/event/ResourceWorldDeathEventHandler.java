package com.ambition.neoworld.event;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModDimensionRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Resource-world deaths drop inventory even when the shared keepInventory rule is enabled. */
@EventBusSubscriber(modid = NeoWorld.MODID)
public final class ResourceWorldDeathEventHandler {
    private ResourceWorldDeathEventHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.level().dimension().equals(ModDimensionRegistries.RESOURCE_WORLD_LEVEL)
                || player.isSpectator()
                || !player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        // With keepInventory disabled, vanilla already drops and clears these slots.
        // Clearing the original also prevents respawn from copying the dropped items.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().removeItemNoUpdate(slot);
            if (stack.isEmpty()
                    || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) continue;
            ItemEntity drop = new ItemEntity(player.level(), player.getX(),
                    player.getY() + 0.3D, player.getZ(), stack);
            drop.setDefaultPickUpDelay();
            event.getDrops().add(drop);
        }
    }
}
