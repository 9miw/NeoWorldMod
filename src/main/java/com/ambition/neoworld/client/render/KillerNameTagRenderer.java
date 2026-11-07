package com.ambition.neoworld.client.render;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.state.ClientKillerData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/** Renders player name in red if the player has positive sin (red name). */
@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class KillerNameTagRenderer {
    private KillerNameTagRenderer() {}

    @SubscribeEvent
    public static void render(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (ClientKillerData.isRed(player.getUUID())) {
            Component original = event.getContent();
            event.setContent(original.copy().withStyle(ChatFormatting.RED));
        }
    }
}
