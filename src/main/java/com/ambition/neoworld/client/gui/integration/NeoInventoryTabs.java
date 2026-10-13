package com.ambition.neoworld.client.gui.integration;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.gui.player.tabs.AbstractTab;

/** Adds player shortcuts using the same inventory-tab component as CustomNPCs. */
@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class NeoInventoryTabs {
    private NeoInventoryTabs() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void init(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !CustomNpcs.InventoryGuiEnabled) return;
        event.addListener(new ShortcutTab(3, new ItemStack(Items.PLAYER_HEAD), "โปรไฟล์ของฉัน", "profile").init(screen));
        event.addListener(new ShortcutTab(4, new ItemStack(Items.LEATHER_CHESTPLATE), "แฟชั่น", "neoworld fashion open").init(screen));
    }

    private static final class ShortcutTab extends AbstractTab {
        private final String command;

        ShortcutTab(int index, ItemStack icon, String label, String command) {
            super(index, 0, 0, icon);
            this.command = command;
            setMessage(Component.literal(label));
            setTooltip(Tooltip.create(Component.literal(label)));
        }

        @Override public boolean shouldAddToList() { return true; }

        @Override public void onPress() { onTabClicked(); }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @Override public void onTabClicked() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()) return;
            // Close through the vanilla lifecycle before opening another server-owned feature.
            if (mc.screen != null) mc.screen.onClose();
            mc.player.connection.sendCommand(command);
        }
    }
}
