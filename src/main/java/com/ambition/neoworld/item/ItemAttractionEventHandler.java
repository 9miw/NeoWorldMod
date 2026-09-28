package com.ambition.neoworld.item;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.permission.ModPermissions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class ItemAttractionEventHandler {
    private ItemAttractionEventHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 5 != 0
                || player.isSpectator()
                || (!player.isCreative() && !ModPermissions.hasItemAttractionPermission(player))) {
            return;
        }

        AABB searchBox = player.getBoundingBox().inflate(8.0D);
        for (ItemEntity itemEntity : player.level().getEntitiesOfClass(ItemEntity.class, searchBox,
                entity -> entity.isAlive() && !entity.hasPickUpDelay() && isAttractable(entity))) {
            Vec3 difference = player.position().add(0.0D, 0.6D, 0.0D).subtract(itemEntity.position());
            double distance = difference.length();
            if (distance <= 0.8D) continue;

            Vec3 velocity = difference.normalize().scale(Math.min(0.35D, 0.08D + distance * 0.025D));
            itemEntity.setDeltaMovement(itemEntity.getDeltaMovement().scale(0.75D).add(velocity));
            itemEntity.hasImpulse = true;
        }
    }

    private static boolean isAttractable(ItemEntity itemEntity) {
        return itemEntity.getItem().getItem() instanceof CoinItem
                || itemEntity.getItem().getItem() instanceof ExperienceItem;
    }
}
