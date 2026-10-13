package com.ambition.neoworld.client.input;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.player.PlayerActionScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class PlayerActionKeyHandler {
    private static final KeyMapping OPEN = new KeyMapping("key.neoworld.player_actions", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.neoworld");
    private PlayerActionKeyHandler() {}
    public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.screen != null || mc.player == null || mc.player.isSpectator() || !mc.player.isAlive()) continue;
            if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player target
                    && target != mc.player && target.isAlive() && !target.isSpectator()
                    && target.distanceToSqr(mc.player) <= 64) {
                mc.setScreen(new PlayerActionScreen(target.getUUID(), target.getGameProfile().getName()));
            }
        }
    }
}
