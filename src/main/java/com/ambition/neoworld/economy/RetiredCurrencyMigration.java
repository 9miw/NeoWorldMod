package com.ambition.neoworld.economy;

import com.ambition.neoworld.economy.analytics.EconomyAnalyticsRepository;
import com.ambition.neoworld.shop.ShopCurrency;
import com.ambition.neoworld.shop.ShopRules;
import com.google.gson.JsonObject;
import java.sql.*;
import java.util.*;

/** Compatibility-only migration. Historical amounts and player balances are never converted. */
public final class RetiredCurrencyMigration {
    private RetiredCurrencyMigration() {}
    private record Offer(long id, byte[] item, long buy, long sell, String currency) {}

    public static void apply(Connection c, String prefix, net.minecraft.core.HolderLookup.Provider registries) throws SQLException {
        if (!prefix.matches("[A-Za-z_][A-Za-z0-9_]{0,31}")) throw new SQLException("Invalid table prefix");
        List<Offer> offers = new ArrayList<>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT id,item_nbt,buy_price,sell_price,currency FROM " + prefix + "shop_offers")) {
            while (r.next()) offers.add(new Offer(r.getLong(1),r.getBytes(2),r.getLong(3),r.getLong(4),r.getString(5)));
        }
        for (Offer retired : offers) if (retired.currency().equals("GOLD")) {
            for (Offer other : offers) if (other.id()!=retired.id()
                    && (other.currency().equals("SILVER") || other.currency().equals("GOLD"))
                    && ShopRules.conflicts(retired.buy(),retired.sell(),ShopCurrency.SILVER,other.buy(),other.sell(),ShopCurrency.SILVER)
                    && sameItem(retired.item(),other.item(),registries)) {
                throw new SQLException("Currency retirement would create shop arbitrage between offers "
                        + retired.id() + " and " + other.id() + "; review prices before retrying migration 17");
            }
        }
        for (Offer retired : offers) if (retired.currency().equals("GOLD")) {
            JsonObject detail = new JsonObject();
            detail.addProperty("old_currency","GOLD"); detail.addProperty("new_currency","SILVER");
            detail.addProperty("buy",retired.buy()); detail.addProperty("sell",retired.sell());
            detail.addProperty("item_nbt_base64",Base64.getEncoder().encodeToString(retired.item()));
            EconomyAnalyticsRepository.insert(c,prefix+"economy_events",new EconomyAnalyticsRepository.Event(
                    UUID.nameUUIDFromBytes((prefix+"retire-currency/"+retired.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                    System.currentTimeMillis(),"CONFIG",null,0,true,"currency_retirement",Long.toString(retired.id()),"NONE","",0,0,0,detail.toString()));
        }
        try (var s=c.createStatement()) {
            s.executeUpdate("UPDATE " + prefix + "shop_transactions SET status='NEEDS_REVIEW' WHERE currency='GOLD' AND status='PENDING'");
            s.executeUpdate("UPDATE " + prefix + "shop_offers SET currency='SILVER' WHERE currency='GOLD'");
        }
    }
    private static boolean sameItem(byte[] first, byte[] second, net.minecraft.core.HolderLookup.Provider registries) throws SQLException {
        try {
            if (registries != null) {
                var a=com.ambition.neoworld.market.storage.MarketItemCodec.decode(first,registries);
                var b=com.ambition.neoworld.market.storage.MarketItemCodec.decode(second,registries);
                return net.minecraft.world.item.ItemStack.isSameItemSameComponents(a,b);
            }
            // Registry-free JDBC fixtures compare structural NBT, never compressed bytes.
            var a=net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(first),net.minecraft.nbt.NbtAccounter.create(2L*1024*1024));
            var b=net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(second),net.minecraft.nbt.NbtAccounter.create(2L*1024*1024));
            return a.equals(b);
        } catch (java.io.IOException | RuntimeException e) {
            throw new SQLException("Cannot validate retired-currency item identity; migration left unapplied",e);
        }
    }
}
