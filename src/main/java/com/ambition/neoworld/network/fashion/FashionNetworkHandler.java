package com.ambition.neoworld.network.fashion;

import com.ambition.neoworld.fashion.data.FashionData;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.ambition.neoworld.fashion.FashionSync;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.registry.ModDataComponents;

import net.minecraft.server.level.ServerPlayer;
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
        FashionData data = player.getData(ModAttachments.FASHION_DATA);
        ItemStack source = data.owned(payload.fashionId());
        if (source.isEmpty()) {
            context.reply(new FashionActionResultPayload(false, "คุณยังไม่ได้ปลดล็อกแฟชั่นนี้"));
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
        data.set(payload.slot(), equipped);
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
        FashionSync.syncToTrackingAndSelf(player);
        context.reply(new FashionActionResultPayload(true, "ถอดแฟชั่นช่อง " + payload.slot().serializedName() + " แล้ว"));
    }

}
