package com.ambition.neoworld.dungeon.room;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModDimensionRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Keep vanilla inventory on dungeon deaths without changing global gamerules. */
@EventBusSubscriber(modid = NeoWorld.MODID)
public final class DungeonInventoryEventHandler {
    private static final String INVENTORY = "neoworld_dungeon_death_inventory";

    private DungeonInventoryEventHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getPersistentData().remove(INVENTORY);
        if (!player.level().dimension().equals(ModDimensionRegistries.DUNGEON_LEVEL)) return;
        // Snapshot before vanilla clears equipment and applies vanishing enchantments.
        player.getPersistentData().put(INVENTORY, player.getInventory().save(new ListTag()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getPersistentData().contains(INVENTORY, Tag.TAG_LIST)
                && player.level().dimension().equals(ModDimensionRegistries.DUNGEON_LEVEL)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var original = event.getOriginal().getPersistentData();
        if (!original.contains(INVENTORY, Tag.TAG_LIST)) return;
        // load replaces slots, including armor and offhand, rather than adding duplicate stacks.
        player.getInventory().load(original.getList(INVENTORY, Tag.TAG_COMPOUND));
        original.remove(INVENTORY);
        player.getPersistentData().remove(INVENTORY);
    }
}
