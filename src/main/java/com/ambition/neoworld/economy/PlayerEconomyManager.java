package com.ambition.neoworld.economy;

import java.util.ArrayList;
import java.util.List;

import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.item.CoinItem;
import com.ambition.neoworld.network.SyncEconomyPayload;
import com.ambition.neoworld.registry.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// ตัวจัดการระบบเศรษฐกิจ (Economy) และการซิงค์ยอดเงินฝั่ง Server
public class PlayerEconomyManager {

    // ฝากทั้งกองสำเร็จเท่านั้นจึงอนุญาตให้ผู้เรียกลบไอเทม
    public static boolean deposit(PlayerData data, CoinItem.CoinType type, long amount) {
        return switch (type) {
            case SILVER -> data.addSilver(amount);
            case GOLD -> data.addGold(amount);
            case DIAMOND -> data.addDiamond(amount);
        };
    }

    /**
     * ซิงค์ข้อมูลยอดเงินทั้งหมด (Silver, Gold, Diamond) ไปยัง Client ของผู้เล่น
     */
    public static void syncToClient(ServerPlayer player) {
        if (player == null) return;
        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        PacketDistributor.sendToPlayer(
                player,
                new SyncEconomyPayload(data.getSilver(), data.getGold(), data.getDiamond())
        );
    }

    /**
     * ตรวจสอบไอเทมเหรียญในตัวผู้เล่น หากพบจะทำการลบไอเทมทิ้งและเพิ่มเงินเข้าตัวละครอัตโนมัติ
     * ยกเว้นเมื่อผู้เล่นอยู่ในโหมด Creative หรือ Spectator
     */
    public static void processInventoryCoins(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator()) return;

        Inventory inventory = player.getInventory();
        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        long addedSilver = 0L;
        long addedGold = 0L;
        long addedDiamond = 0L;
        boolean changed = false;

        // 1. ตรวจสอบทุกช่องใน Inventory (รวม Hotbar, กระเป๋าหลัก, ชุดเกราะ, Offhand)
        int size = inventory.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof CoinItem coinItem) {
                long totalValue = CoinItem.getTotalValue(stack);
                if (!deposit(data, coinItem.getCoinType(), totalValue)) continue;
                switch (coinItem.getCoinType()) {
                    case SILVER -> addedSilver += totalValue;
                    case GOLD -> addedGold += totalValue;
                    case DIAMOND -> addedDiamond += totalValue;
                }
                inventory.setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }

        // 2. ตรวจสอบไอเทมที่ถือค้างอยู่บนเมาส์ (Cursor Carried Stack) ใน Container Menu
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty() && carried.getItem() instanceof CoinItem coinItem) {
                long totalValue = CoinItem.getTotalValue(carried);
                if (deposit(data, coinItem.getCoinType(), totalValue)) {
                    switch (coinItem.getCoinType()) {
                        case SILVER -> addedSilver += totalValue;
                        case GOLD -> addedGold += totalValue;
                        case DIAMOND -> addedDiamond += totalValue;
                    }
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                    changed = true;
                }
            }
        }

        // หากมีการตรวจพบและลบเหรียญ
        if (changed) {
            // ซิงค์ยอดเงินไปยัง Client ทันที
            syncToClient(player);

            // อัปเดต Slot ให้ Client รับรู้ว่าไอเทมถูกลบแล้ว
            if (player.containerMenu != null) {
                player.containerMenu.broadcastChanges();
            }
            player.inventoryMenu.broadcastChanges();

            // แจ้งเตือนข้อความบน Actionbar
            List<Component> messages = new ArrayList<>();
            if (addedSilver > 0) {
                messages.add(Component.literal("§a+" + String.format("%,d", addedSilver) + " ")
                        .append(Component.literal("Silver").withStyle(ChatFormatting.GRAY)));
            }
            if (addedGold > 0) {
                messages.add(Component.literal("§a+" + String.format("%,d", addedGold) + " ")
                        .append(Component.literal("Gold").withStyle(ChatFormatting.GOLD)));
            }
            if (addedDiamond > 0) {
                messages.add(Component.literal("§a+" + String.format("%,d", addedDiamond) + " ")
                        .append(Component.literal("Diamond").withStyle(ChatFormatting.AQUA)));
            }

            if (!messages.isEmpty()) {
                MutableComponent resultText = Component.empty();
                for (int i = 0; i < messages.size(); i++) {
                    if (i > 0) resultText.append(Component.literal(" §f| "));
                    resultText.append(messages.get(i));
                }
                resultText.append(Component.literal(" (อัตโนมัติ)").withStyle(ChatFormatting.DARK_GRAY));
                player.displayClientMessage(resultText, true);
            }

            // เล่นเสียงเอฟเฟกต์รับเงิน
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
        }
    }
}
