package com.ambition.neoworld.client.gui.trade;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModMenus;
import com.ambition.neoworld.client.gui.market.MarketSellScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = NeoWorld.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TradeMenuScreens {
    private TradeMenuScreens() {}
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TRADE.get(), TradeScreen::new);
        event.register(ModMenus.MARKET_SELL.get(), MarketSellScreen::new);
    }
}
