package com.ambition.neoworld.client.input;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.player.PlayerActionScreen;
import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class PlayerActionKeyHandler {
    private static final KeyMapping OPEN = new KeyMapping("key.neoworld.player_actions", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_TAB, "key.categories.neoworld");
    private PlayerActionKeyHandler() {}
    public static void register(RegisterKeyMappingsEvent event) { event.register(OPEN); }
    public static boolean matches(int keyCode, int scanCode) { return OPEN.matches(keyCode, scanCode); }
    public static net.minecraft.network.chat.Component holdHint() {
        return net.minecraft.network.chat.Component.literal("กด ").append(OPEN.getTranslatedKeyMessage())
                .append(" ค้างเพื่อแสดง • ปล่อยเพื่อปิด • ระยะ 5 บล็อก");
    }
    /** Screens can reset KeyMapping state, so check the bound physical input. */
    public static boolean isHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isWindowActive()) return false;
        InputConstants.Key key = OPEN.getKey();
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE)
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM)
            return key.getValue() != GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(window, key.getValue());
        for (int code = GLFW.GLFW_KEY_SPACE; code <= GLFW.GLFW_KEY_LAST; code++) {
            if (GLFW.glfwGetKeyScancode(code) == key.getValue() && InputConstants.isKeyDown(window, code)) return true;
        }
        return false;
    }
    @SubscribeEvent public static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL
                && Minecraft.getInstance().screen instanceof PlayerActionScreen screen) {
            screen.updateAnchor(event);
        }
    }
    @SubscribeEvent public static void renderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (VanillaGuiLayers.TAB_LIST.equals(event.getName())
                && OPEN.getKey().equals(Minecraft.getInstance().options.keyPlayerList.getKey())
                && NeoPopupScreen.findScreen(PlayerActionScreen.class) != null) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        PlayerActionScreen actions = NeoPopupScreen.findScreen(PlayerActionScreen.class);
        if (actions != null && !isHeld()) actions.onClose();
        while (OPEN.consumeClick()) {
            if (!isHeld() || mc.screen != null || mc.player == null || mc.player.isSpectator() || !mc.player.isAlive()) continue;
            mc.setScreen(new PlayerActionScreen());
        }
    }
}
