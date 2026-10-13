package com.ambition.neoworld.client.input;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.state.ClientPvpQueueData;
import com.ambition.neoworld.network.pvp.PvpQueueCancelPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid=NeoWorld.MODID,value=Dist.CLIENT)
public final class PvpQueueKeyHandler {
    public static final KeyMapping CANCEL=new KeyMapping("key.neoworld.pvp_cancel_queue",KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,"key.categories.neoworld");
    private static int cooldown;
    private PvpQueueKeyHandler() { }
    public static void register(RegisterKeyMappingsEvent event) { event.register(CANCEL); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(cooldown>0) cooldown--;
        var mc=Minecraft.getInstance();
        while(CANCEL.consumeClick()) {
            if(mc.player==null || mc.screen!=null || !ClientPvpQueueData.state().queued() || cooldown>0) continue;
            PacketDistributor.sendToServer(PvpQueueCancelPayload.INSTANCE); cooldown=10;
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientPvpQueueData.reset(); com.ambition.neoworld.client.gui.pvp.PvpMatchScreenController.reset(); cooldown=0; }
}
