package com.ambition.neoworld.economy;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.item.CoinItem;
import com.ambition.neoworld.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// คลาสจัดการ Event ของระบบ Economy: การเก็บเหรียญอัตโนมัติ (Auto Pickup) และการดรอปเงินจากมอนสเตอร์
@EventBusSubscriber(modid = NeoWorld.MODID)
public class EconomyEventHandler {

    /**
     * ดักจับการทำงานของผู้เล่นฝั่ง Server ทุกๆ 20 Ticks (~1 วินาที) เพื่อสแกนและแปลงเหรียญในตัวเป็นเงินอัตโนมัติ
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.tickCount % 20 == 0) {
                PlayerEconomyManager.processInventoryCoins(player);
            }
        }
    }

    /**
     * สแกนเหรียญทันทีเมื่อผู้เล่นปิดหน้าต่าง Container Menu (เช่น กล่อง หรือ Crafting Table)
     */
    @SubscribeEvent
    public static void onContainerClose(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerEconomyManager.processInventoryCoins(player);
        }
    }

    /**
     * ดักจับเมื่อผู้เล่นเดินชน / พยายามเก็บเหรียญจากพื้น
     * ทำการคำนวณมูลค่าและฝากเงินเข้าตัวผู้เล่นทันที พร้อมทำลาย ItemEntity ทิ้ง ไม่ให้เข้าช่องเก็บของ
     */
    @SubscribeEvent
    public static void onItemPickupPre(ItemEntityPickupEvent.Pre event) {
        try (var analyticsScope = com.ambition.neoworld.economy.analytics.EconomyAnalytics.observe(event.getPlayer() instanceof ServerPlayer p ? p : null, "coin_redemption_unknown", "pickup", "UNKNOWN")) {
            ItemEntity itemEntity = event.getItemEntity();
            if (itemEntity == null || !itemEntity.isAlive()) return;
            if (itemEntity.hasPickUpDelay()) return;

            ItemStack stack = itemEntity.getItem();
            if (stack.getItem() instanceof CoinItem coinItem) {
                if (event.getPlayer() instanceof ServerPlayer player) {
                    if (player.isCreative() || player.isSpectator()) {
                        return;
                    }
                    long totalAmount = CoinItem.getTotalValue(stack);

                    PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
                    String typeName = coinItem.getCoinType().getDisplayName();
                    ChatFormatting format = coinItem.getCoinType().getFormat();

                    if (!PlayerEconomyManager.deposit(data, coinItem.getCoinType(), totalAmount)) {
                        event.setCanPickup(TriState.FALSE);
                        return;
                    }

                    // ซิงค์ข้อมูลยอดเงินไปยัง Client ทันที
                    PlayerEconomyManager.syncToClient(player);

                    // แสดงข้อความบน Actionbar กลางล่างจอ
                    player.displayClientMessage(
                            Component.literal("§a+" + String.format("%,d", totalAmount) + " ")
                                    .append(Component.literal(typeName).withStyle(format))
                                    .append(Component.literal(" (อัตโนมัติ)").withStyle(ChatFormatting.DARK_GRAY)),
                            true
                    );

                    // เล่นเสียงเอฟเฟกต์รับเหรียญ
                    player.serverLevel().playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP,
                            SoundSource.PLAYERS,
                            0.6F,
                            1.2F
                    );

                    // ทำลายไอเทมบนพื้นทิ้ง และยกเลิกการเก็บเข้าช่องกระเป๋า
                    itemEntity.discard();
                    event.setCanPickup(TriState.FALSE);
                }
            }
        }
    }

}
