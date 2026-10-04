package com.ambition.neoworld.client.gui.integration;

import com.ambition.neoworld.client.gui.widget.EconomyBalanceWidget;

import com.ambition.neoworld.NeoWorld;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import noppes.npcs.client.gui.player.GuiNPCTrader;

// คลาสจัดการ Event ดักจับการเปิดหน้าต่าง GUI เพื่อแทรก EconomyBalanceWidget
@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public class TraderGuiEventHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof GuiNPCTrader traderGui) {
            int widgetWidth = 190;
            int widgetHeight = 12;
            int x = Math.max(8, traderGui.getWidth() - widgetWidth - 8);
            int y = 2;

            event.addListener(new EconomyBalanceWidget(x, y, widgetWidth, widgetHeight,
                    EconomyBalanceWidget.Alignment.RIGHT));
        }
    }
}
