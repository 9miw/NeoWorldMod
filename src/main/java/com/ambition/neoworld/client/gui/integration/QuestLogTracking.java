package com.ambition.neoworld.client.gui.integration;

import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.command.QuestNavigationCommand;
import com.ambition.neoworld.client.navigation.ClientNavigationController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.player.GuiQuestLog;

/** Adds one action below the native quest list, without replacing Custom NPCs quest handling. */
public final class QuestLogTracking {
    private static final int LIST_WIDTH = 134;
    private static final int LIST_HEIGHT = 150;
    private static final Component LABEL = Component.translatable("gui.neoworld.quest_navigation.track");
    private static final Component STOP_LABEL = Component.translatable("gui.neoworld.quest_navigation.untrack");
    private static final Tooltip STOP_HINT = Tooltip.create(Component.translatable("gui.neoworld.quest_navigation.untrack_hint"));
    private static final Tooltip READY = Tooltip.create(Component.translatable("gui.neoworld.quest_navigation.track_hint"));
    private static final Tooltip SELECT_QUEST = Tooltip.create(Component.translatable("gui.neoworld.quest_navigation.select_hint"));

    private QuestLogTracking() {}

    public static NeoButton createButton(GuiQuestLog screen) {
        // Native list: x + 5, y + 15, 134 x 174. Reserve its final 24 pixels for a H20 action.
        var scroll = screen.getScroll(0);
        if (scroll != null) scroll.setSize(LIST_WIDTH, LIST_HEIGHT);
        NeoButton button = new NeoButton(Button.builder(LABEL, ignored -> track(screen))
                .bounds(screen.guiLeft + 5, screen.guiTop + 169, LIST_WIDTH, 20))
                .style(NeoButton.Style.PRIMARY);
        update(screen, button);
        return button;
    }

    public static void update(GuiQuestLog screen, NeoButton button) {
        var player = Minecraft.getInstance().player;
        boolean selected = selectedId(screen) >= 0;
        boolean tracking = selected && selectedId(screen) == ClientNavigationController.trackedQuestId();
        button.setMessage(tracking ? STOP_LABEL : LABEL);
        button.visible = !screen.hasSubGui();
        button.active = button.visible && selected && player != null && player.isAlive() && !player.isSpectator();
        button.setTooltip(selected ? (tracking ? STOP_HINT : READY) : SELECT_QUEST);
    }

    private static int selectedId(GuiQuestLog screen) {
        if (screen.selectedQuest == null || screen.selectedQuest.id < 0) return -1;
        int id = screen.selectedQuest.id;
        // Native selection can survive a refresh: do not enable a stale quest from another list.
        return screen.activeQuests.values().stream().flatMap(java.util.Collection::stream)
                .anyMatch(quest -> quest.id == id) ? id : -1;
    }

    private static void track(GuiQuestLog screen) {
        Minecraft minecraft = Minecraft.getInstance();
        int questId = selectedId(screen);
        if (minecraft.screen != screen || screen.hasSubGui() || questId < 0
                || minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.isSpectator()) return;
        String command = questId == ClientNavigationController.trackedQuestId()
                ? "neow questnav stop" : QuestNavigationCommand.trackingCommand(questId);
        minecraft.player.connection.sendCommand(command);
    }
}
