package com.ambition.neoworld.economy.analytics;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.activity.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.concurrent.atomic.AtomicBoolean;

@EventBusSubscriber(modid=NeoWorld.MODID)
public final class EconomyAnalyticsEvents {
    private static final AtomicBoolean EXPORTING=new AtomicBoolean();
    private EconomyAnalyticsEvents() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        EconomyAnalytics.daily();
        var runtime=ActivityEvents.runtime(event.getServer());
        if(runtime!=null) {
            String detail=ActivityRewards.toJson(runtime.rewards).toString();
            EconomyAnalytics.enqueue(EconomyAnalytics.event("CONFIG",null,"activity_rewards","startup","NONE","",0,0,0,detail));
        }
        try {
            for(var shop:com.ambition.neoworld.shop.ShopRepository.shops())
                for(var offer:com.ambition.neoworld.shop.ShopRepository.offers(shop.id(),event.getServer().registryAccess())) {
                    var detail=AnalyticsHistory.item(offer.item(),event.getServer().registryAccess());
                    detail.addProperty("buy",offer.buy());detail.addProperty("sell",offer.sell());detail.addProperty("currency",offer.currency().name());
                    EconomyAnalytics.enqueue(EconomyAnalytics.event("CONFIG",null,"shop_offer_baseline",shop.id()+"/"+offer.id(),"NONE","",0,0,0,detail.toString()));
                }
        } catch(Exception e) { NeoWorld.LOGGER.error("Could not record economy catalog baseline",e); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(event.getServer().getTickCount()%1200==0)EconomyAnalytics.daily();
    }
    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("economyreport").requires(source->source.hasPermission(2))
                .then(Commands.literal("status").executes(ctx->{
                    ctx.getSource().sendSuccess(()->Component.literal("Economy analysis: pending="+AnalyticsWriter.pending()+", lost="+AnalyticsWriter.dropped()),false);return 1;
                }))
                .then(Commands.literal("export").executes(ctx->export(ctx.getSource(),28))
                        .then(Commands.argument("days",IntegerArgumentType.integer(1,365)).executes(ctx->export(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"days")))));
    }
    private static int export(net.minecraft.commands.CommandSourceStack source,int days) {
        if(!EXPORTING.compareAndSet(false,true)){source.sendFailure(Component.literal("มีรายงานกำลังสร้างอยู่ กรุณารอสักครู่"));return 0;}
        // Take current wallet observations on the owning thread, then stream SQL/files off-thread.
        for(var player:source.getServer().getPlayerList().getPlayers())EconomyAnalytics.snapshot(player);
        source.sendSuccess(()->Component.literal("กำลังสร้างรายงานเศรษฐกิจ "+days+" วัน"),false);
        var thread=new Thread(()->{
            try {
                var path=EconomyReport.export(FMLPaths.CONFIGDIR.get().resolve("neoworld/reports"),days);
                source.getServer().execute(()->source.sendSuccess(()->Component.literal("รายงานพร้อม: "+path),false));
            } catch(Exception e) {
                NeoWorld.LOGGER.error("Economy report export failed",e);
                source.getServer().execute(()->source.sendFailure(Component.literal("สร้างรายงานไม่สำเร็จ ตรวจ server log")));
            } finally {EXPORTING.set(false);}
        },"NeoWorld-economy-report");thread.setDaemon(true);thread.start();return 1;
    }
}
