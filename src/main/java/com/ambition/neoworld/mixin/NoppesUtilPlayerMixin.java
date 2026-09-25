package com.ambition.neoworld.mixin;

import com.ambition.neoworld.client.ClientEconomyData;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.economy.PlayerEconomyManager;
import com.ambition.neoworld.item.CoinItem;
import com.ambition.neoworld.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.NoppesUtilPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NoppesUtilPlayer.class, remap = false)
public class NoppesUtilPlayerMixin {

    /**
     * ดักจับการตรวจสอบไอเทมของผู้เล่นในระบบ Custom NPCs
     * หากไอเทมที่ใช้ตรวจสอบคือ CoinItem (Silver, Gold, Diamond) จะเปลี่ยนไปเช็คยอดเงินในระบบ Economy แทน
     */
    @Inject(
            method = "compareItems(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;ZZ)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void neoworld$onCompareItems(Player player, ItemStack item, boolean ignoreDamage, boolean ignoreNBT, CallbackInfoReturnable<Boolean> cir) {
        if (player == null || item == null || item.isEmpty()) {
            return;
        }

        if (item.getItem() instanceof CoinItem coinItem) {
            long requiredAmount = CoinItem.getTotalValue(item);
            if (requiredAmount <= 0) {
                cir.setReturnValue(false);
                return;
            }

            if (player.level().isClientSide()) {
                // ฝั่ง Client สำหรับเรนเดอร์สถานะใน GUI Trader
                boolean hasEnough = switch (coinItem.getCoinType()) {
                    case SILVER -> ClientEconomyData.getSilver() >= requiredAmount;
                    case GOLD -> ClientEconomyData.getGold() >= requiredAmount;
                    case DIAMOND -> ClientEconomyData.getDiamond() >= requiredAmount;
                };
                cir.setReturnValue(hasEnough);
            } else {
                // ฝั่ง Server สำหรับความถูกต้องของการแลกเปลี่ยน
                if (player.hasData(ModAttachments.PLAYER_DATA)) {
                    PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
                    boolean hasEnough = switch (coinItem.getCoinType()) {
                        case SILVER -> data.hasEnoughSilver(requiredAmount);
                        case GOLD -> data.hasEnoughGold(requiredAmount);
                        case DIAMOND -> data.hasEnoughDiamond(requiredAmount);
                    };
                    cir.setReturnValue(hasEnough);
                } else {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    /**
     * ดักจับการหักไอเทมของผู้เล่นในการแลกเปลี่ยนของ Custom NPCs
     * หากเป็น CoinItem จะทำการหักยอดเงินออกจากระบบ Economy และซิงค์ HUD ทันที โดยไม่ไปยุ่งกับไอเทมในกระเป๋า
     */
    @Inject(
            method = "consumeItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;ZZ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void neoworld$onConsumeItem(Player player, ItemStack item, boolean ignoreDamage, boolean ignoreNBT, CallbackInfo ci) {
        if (player == null || item == null || item.isEmpty()) {
            return;
        }

        if (item.getItem() instanceof CoinItem coinItem) {
            long deductAmount = CoinItem.getTotalValue(item);

            if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                if (player.hasData(ModAttachments.PLAYER_DATA)) {
                    PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
                    boolean paid = switch (coinItem.getCoinType()) {
                        case SILVER -> data.removeSilver(deductAmount);
                        case GOLD -> data.removeGold(deductAmount);
                        case DIAMOND -> data.removeDiamond(deductAmount);
                    };
                    if (!paid) {
                        NeoWorld.LOGGER.warn("Rejected invalid or unaffordable coin debit for {}", serverPlayer.getUUID());
                    }
                    PlayerEconomyManager.syncToClient(serverPlayer);
                }
            }
            ci.cancel();
        }
    }
}
