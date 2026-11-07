package com.ambition.neoworld.shop;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.market.service.MarketDurability;
import com.ambition.neoworld.market.storage.MarketSql;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Shop business mutations join the shared market/player checkpoint transaction. */
public final class ShopService {
    private ShopService() {}

    public static String trade(ServerPlayer player, ShopRepository.Offer displayed, int quantity, boolean sell) {
        return trade(player, UUID.randomUUID(), displayed, quantity, sell);
    }

    public static String trade(ServerPlayer player, UUID operation, ShopRepository.Offer displayed, int quantity, boolean sell) {
        if (displayed == null) return "ไม่พบสินค้า";
        return MarketDurability.executeShop(player, operation, sell, displayed.id(),
                () -> tradeUnchecked(player, operation, displayed, quantity, sell));
    }

    private static MarketDurability.ShopOutcome tradeUnchecked(ServerPlayer player, UUID operation,
            ShopRepository.Offer displayed, int quantity, boolean sell) throws Exception {
        try (var analytics = com.ambition.neoworld.economy.analytics.EconomyAnalytics.observe(player,
                sell ? "npc_sell" : "npc_buy", "shop:" + displayed.shopId() + "/offer:" + displayed.id(), sell ? "SOURCE" : "SINK")) {
            if (!player.isAlive() || player.isSpectator()) return rejected("ผู้เล่นไม่พร้อมซื้อขาย");
            ShopCurrency currency = displayed.currency();
            // Lock the complete offer in the same transaction as the audit and player after-image.
            var current = ShopRepository.lockedOffer(displayed.shopId(), displayed.id(), player.registryAccess());
            if (current == null || current.currency() != currency || current.buy() != displayed.buy() || current.sell() != displayed.sell()
                    || !ItemStack.matches(current.item(), displayed.item())) return rejected("รายการสินค้าเปลี่ยนแล้ว กรุณาเปิดร้านใหม่");
            var wallet = player.getData(ModAttachments.PLAYER_DATA);
            long beforeMoney = currency.balance(wallet);
            long total = ShopRules.total(sell ? current.sell() : current.buy(), quantity);
            if (!sell && beforeMoney < total) return rejected(currency.label() + " ไม่เพียงพอ");
            long afterMoney = ShopRules.balance(beforeMoney, total, sell);
            List<ItemStack> before = new ArrayList<>();
            for (int i = 0; i < 36; i++) before.add(player.getInventory().getItem(i).copy());
            List<ItemStack> after = plan(before, current.item(), quantity, sell);
            if (after == null) return rejected(sell ? "มีสินค้าที่ตรงกันไม่ครบจำนวน" : "พื้นที่กระเป๋าไม่เพียงพอ");
            var detail = new com.google.gson.JsonObject();
            detail.addProperty("item_id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(current.item().getItem()).toString());
            detail.addProperty("quantity", quantity); detail.addProperty("unit_price", sell ? current.sell() : current.buy());
            detail.addProperty("action", sell ? "SELL" : "BUY"); detail.addProperty("operation_id", operation.toString());
            analytics.detail(detail);
            MarketSql.transaction(c -> {
                ShopRepository.recordCommitted(c, operation, player.getUUID(), current.shopId(), current.id(), sell, quantity,
                        total, currency, com.ambition.neoworld.market.storage.MarketItemCodec.encode(current.item(), player.registryAccess()),
                        beforeMoney, afterMoney);
                return null;
            });
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, after.get(i));
            currency.setBalance(wallet, afterMoney);
            player.getInventory().setChanged();
            // The shared adapter publishes this message only after commit and verified player save.
            return new MarketDurability.ShopOutcome(true, (sell ? "ขาย" : "ซื้อ") + "สำเร็จ " + quantity
                    + " ชิ้น / " + String.format("%,d", total) + " " + currency.label());
        } catch (ArithmeticException | IllegalArgumentException e) {
            return rejected("ราคา จำนวน หรือยอดเงินเกินขอบเขตที่รองรับ");
        }
    }

    private static MarketDurability.ShopOutcome rejected(String message) {
        return new MarketDurability.ShopOutcome(false, message);
    }

    /** Historical pending intents have no trustworthy checkpoint; preserve them for review without granting/refunding. */
    public static void reviewLegacyOnLogin(ServerPlayer player) {
        if (player == null || MarketDurability.isQuarantined(player)) return;
        try {
            int count = MarketSql.transaction(c -> ShopRepository.markLegacyPendingForReview(c, player.getUUID()));
            if (count > 0) NeoWorld.LOGGER.warn("Preserved {} legacy shop intents for review for {}", count, player.getUUID());
        } catch (Exception failure) {
            NeoWorld.LOGGER.error("Could not review legacy shop intents for {}", player.getUUID(), failure);
        }
    }

    static List<ItemStack> plan(List<ItemStack> inventory, ItemStack unit, int quantity, boolean sell) {
        if (unit.isEmpty()) return null;
        try {
            var source = inventory.stream().map(stack -> new com.ambition.neoworld.trade.TradeInventoryPlan.Stack<>(
                    stack.isEmpty() ? null : stack.copyWithCount(1), stack.getCount(), stack.isEmpty() ? 64 : stack.getMaxStackSize())).toList();
            var reference = new com.ambition.neoworld.trade.TradeInventoryPlan.Stack<>(unit.copyWithCount(1),1,unit.getMaxStackSize());
            return ShopInventoryPlan.plan(source,reference,quantity,sell,ItemStack::isSameItemSameComponents).stream()
                    .map(stack -> stack.count() == 0 ? ItemStack.EMPTY : stack.identity().copyWithCount(stack.count())).toList();
        } catch (IllegalArgumentException e) { return null; }
    }
}
