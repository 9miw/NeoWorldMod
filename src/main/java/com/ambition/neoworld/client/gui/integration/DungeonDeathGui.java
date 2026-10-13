package com.ambition.neoworld.client.gui.integration;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.dungeon.DungeonDeathExitPayload;
import com.ambition.neoworld.network.dungeon.DungeonDeathQueryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keep the vanilla death screen and its respawn delay; customize only dungeon deaths. */
@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class DungeonDeathGui {
    private static DeathScreen screen;
    private static Button respawn;
    private static Button exit;
    private static boolean submitted;

    private DungeonDeathGui() {}

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DeathScreen death)) return;
        reset();
        screen = death;
        respawn = event.getListenersList().stream().filter(Button.class::isInstance)
                .map(Button.class::cast).findFirst().orElse(null);
        if (respawn == null) return;
        exit = Button.builder(Component.literal("ออกดัน"), button -> {
            var player = Minecraft.getInstance().player;
            if (player == null || submitted || !respawn.active) return;
            submitted = true;
            respawn.active = false;
            button.active = false;
            // Ordered before vanilla's respawn packet on the same connection.
            PacketDistributor.sendToServer(DungeonDeathExitPayload.INSTANCE);
            player.respawn();
        }).bounds(respawn.getX() + 102, respawn.getY(), 98, 20).build();
        exit.visible = false;
        exit.active = false;
        event.addListener(exit);
        PacketDistributor.sendToServer(DungeonDeathQueryPayload.INSTANCE);
    }

    public static void receive(boolean dungeonDeath) {
        if (!dungeonDeath || screen == null || Minecraft.getInstance().screen != screen || respawn == null) return;
        respawn.setMessage(Component.literal("รับชม"));
        respawn.setWidth(98);
        exit.visible = true;
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Pre event) {
        if (event.getScreen() == screen && exit != null) exit.active = respawn.active && !submitted;
    }

    public static void reset() {
        screen = null;
        respawn = null;
        exit = null;
        submitted = false;
    }
}
