package com.ambition.neoworld.economy.analytics;

import com.google.gson.JsonObject;
import com.ambition.neoworld.market.storage.MarketItemCodec;
import java.sql.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** History written in the same database transaction as the owning market/catalog operation. */
public final class AnalyticsHistory {
    private AnalyticsHistory() {}
    public static JsonObject item(ItemStack item, HolderLookup.Provider registries) throws java.io.IOException {
        var data=new JsonObject();
        data.addProperty("item_id",BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
        data.addProperty("quantity",item.getCount());
        data.addProperty("item_nbt_base64",Base64.getEncoder().encodeToString(MarketItemCodec.encode(item,registries)));
        return data;
    }
    public static void record(Connection c,String type,UUID player,String source,String ref,String flow,long amount,JsonObject detail) throws SQLException {
        var server=ServerLifecycleHooks.getCurrentServer();
        var online=server==null||player==null?null:server.getPlayerList().getPlayer(player);
        boolean admin=online!=null&&EconomyAnalytics.admin(online);
        if(detail.has("buyer_id") && server!=null) {
            var buyer=server.getPlayerList().getPlayer(UUID.fromString(detail.get("buyer_id").getAsString()));
            admin|=buyer!=null&&EconomyAnalytics.admin(buyer);
        }
        EconomyAnalyticsRepository.insert(c,new EconomyAnalyticsRepository.Event(UUID.randomUUID().toString(),System.currentTimeMillis(),type,
                player==null?null:player.toString(),online==null?0:online.getData(com.ambition.neoworld.registry.ModAttachments.PLAYER_DATA).getLevel(),
                admin,source,ref,flow,type.equals("CONFIG")?"":"SILVER",0,0,amount,detail.toString()));
    }
    public static void market(Connection c,String type,long listing,UUID seller,UUID buyer,ItemStack item,
            long unit,long total,long tax,long gameTime,HolderLookup.Provider registries) throws SQLException,java.io.IOException {
        var data=item(item,registries);
        data.addProperty("seller_id",seller.toString());
        data.addProperty("correlation",EconomyAnalytics.correlation(buyer==null?seller:buyer));
        if(buyer!=null)data.addProperty("buyer_id",buyer.toString());
        data.addProperty("unit_price",unit);data.addProperty("total",total);data.addProperty("tax",tax);data.addProperty("game_time",gameTime);
        record(c,"MARKET_"+type,seller,"market",Long.toString(listing),"TRANSFER",total,data);
        if(tax>0)record(c,"MARKET_TAX",seller,"market_tax",Long.toString(listing),"SINK",tax,data);
    }
    public static void config(Connection c,String source,String ref,JsonObject data) throws SQLException {
        record(c,"CONFIG",null,source,ref,"NONE",0,data);
    }
}
