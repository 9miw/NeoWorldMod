package com.ambition.neoworld.client.gui.integration;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.hub.NeoPauseScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class NeoPauseMenuEvents {
    private NeoPauseMenuEvents() {}

    @SubscribeEvent
    public static void hideTabList(RenderGuiLayerEvent.Pre event) {
        if (VanillaGuiLayers.TAB_LIST.equals(event.getName())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void opening(ScreenEvent.Opening event) {
        // Preserve F3+Esc's hidden pause screen and other mods' custom subclasses.
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == PauseScreen.class
                && ((PauseScreen) event.getNewScreen()).showsPauseMenu()) {
            event.setNewScreen(new NeoPauseScreen());
        }
    }
}
