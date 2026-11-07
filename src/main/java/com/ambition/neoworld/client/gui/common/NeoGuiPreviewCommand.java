package com.ambition.neoworld.client.gui.common;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Local preview entry point, deliberately absent from normal gameplay menus. */
@EventBusSubscriber(modid = "neoworld", value = Dist.CLIENT)
public final class NeoGuiPreviewCommand {
    private NeoGuiPreviewCommand() {}
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("neogui").executes(context -> {
            Minecraft client = Minecraft.getInstance();
            // Queue after ChatScreen finishes submitting/closing the command.
            client.tell(() -> client.setScreen(new NeoComponentShowcaseScreen(null)));
            return 1;
        }));
    }
}
