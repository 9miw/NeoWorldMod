package com.ambition.neoworld;

import com.ambition.neoworld.client.gui.EconomyOverlay;
import com.ambition.neoworld.client.gui.LevelOverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// คลาสจัดการระบบฝั่ง Client สำหรับมอด NeoWorld
@Mod(value = NeoWorld.MODID, dist = Dist.CLIENT)
public class NeoWorldClient {

    public NeoWorldClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::registerGuiLayers);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        NeoWorld.LOGGER.info("HELLO FROM CLIENT SETUP");
        NeoWorld.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.PLAYER_HEALTH,
                ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "level_overlay"),
                new LevelOverlay()
        );

        event.registerAbove(
                VanillaGuiLayers.PLAYER_HEALTH,
                ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "economy_overlay"),
                new EconomyOverlay()
        );
    }
}
