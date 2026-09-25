package com.ambition.neoworld.mixin;

import com.ambition.neoworld.client.ClientEconomyData;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.economy.CurrencyMath;
import com.ambition.neoworld.item.CoinItem;
import com.ambition.neoworld.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.NoppesUtilPlayer;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ContainerNPCTrader.class, remap = false)
public class ContainerNPCTraderMixin {
    @Shadow public RoleTrader role;

    // ตรวจทั้งสองช่องก่อนหักเงิน รวมตามมูลค่าจริงแม้เหรียญสกุลเดียวกันมีราคาต่อชิ้นต่างกัน
    // Custom NPCs เรียก canBuy อีกครั้งหลังสคริปต์แก้ราคา ก่อน consumeItem ทั้งสองช่อง
    @Inject(method = "canBuy", at = @At("HEAD"), cancellable = true)
    private void neoworld$checkCoinCosts(ItemStack first, ItemStack second, Player player,
                                       CallbackInfoReturnable<Boolean> cir) {
        boolean firstCoin = first != null && !first.isEmpty() && first.getItem() instanceof CoinItem;
        boolean secondCoin = second != null && !second.isEmpty() && second.getItem() instanceof CoinItem;
        if (!firstCoin && !secondCoin) return;

        for (ItemStack cost : new ItemStack[]{first, second}) {
            if (cost != null && !cost.isEmpty()
                    && !NoppesUtilPlayer.compareItems(player, cost, role.ignoreDamage, role.ignoreNBT)) {
                cir.setReturnValue(false);
                return;
            }
        }

        if (firstCoin && secondCoin) {
            CoinItem.CoinType type = ((CoinItem) first.getItem()).getCoinType();
            if (type == ((CoinItem) second.getItem()).getCoinType()) {
                long balance;
                if (player.level().isClientSide()) {
                    balance = switch (type) {
                        case SILVER -> ClientEconomyData.getSilver();
                        case GOLD -> ClientEconomyData.getGold();
                        case DIAMOND -> ClientEconomyData.getDiamond();
                    };
                } else {
                    PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
                    balance = switch (type) {
                        case SILVER -> data.getSilver();
                        case GOLD -> data.getGold();
                        case DIAMOND -> data.getDiamond();
                    };
                }
                cir.setReturnValue(CurrencyMath.canAfford(balance,
                        CoinItem.getTotalValue(first), CoinItem.getTotalValue(second)));
                return;
            }
        }
        cir.setReturnValue(true);
    }
}
