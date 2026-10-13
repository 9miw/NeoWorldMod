package com.ambition.neoworld;

import com.ambition.neoworld.client.render.FashionLayer;
import com.ambition.neoworld.client.gui.overlay.EconomyOverlay;
import com.ambition.neoworld.client.gui.overlay.LevelOverlay;
import com.ambition.neoworld.client.gui.overlay.PartyOverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// คลาสจัดการระบบฝั่ง Client สำหรับมอด NeoWorld
@Mod(value = NeoWorld.MODID, dist = Dist.CLIENT)
public class NeoWorldClient {

    public NeoWorldClient(IEventBus modEventBus, ModContainer container) {
        if (net.neoforged.fml.ModList.get().isLoaded("epicfight")) {
            com.ambition.neoworld.client.render.EpicFightFashionLayer.register();
        }
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(com.ambition.neoworld.client.input.PlayerActionKeyHandler::register);
        modEventBus.addListener(com.ambition.neoworld.client.input.PvpQueueKeyHandler::register);
        modEventBus.addListener(this::registerGuiLayers);
        modEventBus.addListener(this::addEntityLayers);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        NeoWorld.LOGGER.info("HELLO FROM CLIENT SETUP");
        NeoWorld.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    private void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH,
                ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID,"pvp_queue_overlay"),new com.ambition.neoworld.client.gui.overlay.PvpQueueOverlay());
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH,
                ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "party_overlay"), new PartyOverlay());
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

    private void addEntityLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new FashionLayer(renderer));
            }
        }
    }
}
