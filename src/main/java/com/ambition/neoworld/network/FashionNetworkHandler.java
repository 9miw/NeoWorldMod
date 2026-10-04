package com.ambition.neoworld.network;

import com.ambition.neoworld.data.fashion.FashionData;
import com.ambition.neoworld.data.fashion.FashionSlot;
import com.ambition.neoworld.fashion.FashionSync;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.registry.ModDataComponents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class FashionNetworkHandler {
    private FashionNetworkHandler() {
    }

    public static void handleEquip(FashionEquipPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            context.reply(new FashionActionResultPayload(false, "ไม่พบผู้เล่น"));
            return;
        }
        Inventory inventory = player.getInventory();
        if (payload.inventorySlot() < 0 || payload.inventorySlot() >= inventory.getContainerSize()) {
            context.reply(new FashionActionResultPayload(false, "ช่องไอเทมไม่ถูกต้อง"));
            return;
        }
        ItemStack source = inventory.getItem(payload.inventorySlot());
        if (source.isEmpty()) {
            context.reply(new FashionActionResultPayload(false, "ไอเทมนี้ไม่มีอยู่แล้ว"));
            return;
        }
        FashionSlot allowedSlot = source.get(ModDataComponents.FASHION_SLOT.get());
        if (allowedSlot == null) {
            context.reply(new FashionActionResultPayload(false, "ไอเทมนี้ไม่ใช่ไอเทมแฟชั่น"));
            return;
        }
        if (allowedSlot != payload.slot()) {
            context.reply(new FashionActionResultPayload(false, "ไอเทมนี้ใส่ได้เฉพาะช่อง " + allowedSlot.serializedName()));
            return;
        }

        ItemStack equipped = source.copy();
        equipped.setCount(1);
        FashionData data = player.getData(ModAttachments.FASHION_DATA);
        ItemStack previous = data.set(payload.slot(), equipped);
        source.shrink(1);
        giveOrDrop(player, previous);
        FashionSync.syncToTrackingAndSelf(player);
        context.reply(new FashionActionResultPayload(true, "ใส่แฟชั่นช่อง " + payload.slot().serializedName() + " แล้ว"));
    }

    public static void handleClear(FashionClearPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            context.reply(new FashionActionResultPayload(false, "ไม่พบผู้เล่น"));
            return;
        }
        FashionData data = player.getData(ModAttachments.FASHION_DATA);
        ItemStack previous = data.clear(payload.slot());
        if (previous.isEmpty()) {
            context.reply(new FashionActionResultPayload(false, "ช่องนี้ว่างอยู่แล้ว"));
            return;
        }
        giveOrDrop(player, previous);
        FashionSync.syncToTrackingAndSelf(player);
        context.reply(new FashionActionResultPayload(true, "ถอดแฟชั่นช่อง " + payload.slot().serializedName() + " แล้ว"));
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
