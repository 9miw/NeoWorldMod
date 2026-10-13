package com.ambition.neoworld.client.gui.common;

import java.util.List;
import java.util.Objects;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.widget.NeoProgressBar;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Shared modal notification screen for NeoWorld client interfaces. */
public final class NeoPopupScreen extends Screen {
    private static final float POPUP_Z = 500.0F;
    private static final int MAX_MESSAGE_LINES = 6;
    private static final int MESSAGE_TOP = 36;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_PADDING = 10;
    private static final int COUNTDOWN_HEIGHT = 28;
    private static final int PLAQUE_OVERHANG = 14;
    private static final ResourceLocation PLAQUE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/common/buttons.png");
    private static final int COLOR_OVERLAY = 0xB0000000;
    private static final int COLOR_PANEL = 0xFF171C20;
    private static final int COLOR_PANEL_INNER = 0xFF20272C;
    private static final int COLOR_FRAME = 0xFF4B555C;
    private static final int COLOR_FRAME_LIGHT = 0xFF8B969D;
    private static final int COLOR_TEXT = 0xFFE7ECEF;
    private static final int COLOR_TEAL = 0xFF2FB5A7;
    private static final int COLOR_GOLD = 0xFFF2C14E;
    private static final Runnable NO_ACTION = () -> {};

    private enum Mode { ALERT, COUNTDOWN }

    private final Screen parent;
    private final Component message;
    private final Component actionLabel;
    private final Component secondaryLabel;
    private final Runnable onAction;
    private final Runnable onSecondary;
    private final Runnable onTimeout;
    private final Mode mode;
    private final int durationTicks;
    private int remainingTicks;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int visibleMessageLines;
    private int messageLineHeight;
    private int countdownY;
    private final NeoProgressBar countdownBar = new NeoProgressBar(0, 0, 0, 0)
            .showPercentage(false).loading(true);
    private List<FormattedCharSequence> messageLines = List.of();
    private boolean finished;

    private NeoPopupScreen(Screen parent, Component title, Component message, Component actionLabel,
                           Component secondaryLabel, Mode mode, int durationTicks, Runnable onAction,
                           Runnable onSecondary, Runnable onTimeout) {
        super(Objects.requireNonNull(title));
        this.parent = parent;
        this.message = Objects.requireNonNull(message);
        this.actionLabel = Objects.requireNonNull(actionLabel);
        this.secondaryLabel = secondaryLabel;
        this.mode = mode;
        this.durationTicks = durationTicks;
        this.remainingTicks = durationTicks;
        this.onAction = onAction == null ? NO_ACTION : onAction;
        this.onSecondary = onSecondary == null ? NO_ACTION : onSecondary;
        this.onTimeout = onTimeout == null ? NO_ACTION : onTimeout;
    }

    public static void showAlert(Component title, Component message, Runnable onAccept) {
        open(parent -> new NeoPopupScreen(parent, title, message, Component.literal("ตกลง"),
                null, Mode.ALERT, 0, onAccept, NO_ACTION, NO_ACTION));
    }

    public static void showChoice(Component title, Component message,
                                  Component actionLabel, Runnable onAction,
                                  Component secondaryLabel, Runnable onSecondary) {
        open(parent -> new NeoPopupScreen(parent, title, message, actionLabel, secondaryLabel,
                Mode.ALERT, 0, onAction, onSecondary, NO_ACTION));
    }

    public static void showCountdown(Component title, Component message, int seconds, Component actionLabel,
                                     Runnable onAction, Runnable onTimeout) {
        int safeSeconds = Math.max(1, Math.min(seconds, 86_400));
        open(parent -> new NeoPopupScreen(parent, title, message, actionLabel, null, Mode.COUNTDOWN,
                safeSeconds * 20, onAction, NO_ACTION, onTimeout));
    }

    public static void showCountdownChoice(Component title, Component message, int seconds,
                                            Component actionLabel, Runnable onAction,
                                            Component secondaryLabel, Runnable onSecondary,
                                            Runnable onTimeout) {
        int safeSeconds = Math.max(1, Math.min(seconds, 86_400));
        open(parent -> new NeoPopupScreen(parent, title, message, actionLabel, secondaryLabel,
                Mode.COUNTDOWN, safeSeconds * 20, onAction, onSecondary, onTimeout));
    }

    public static <T extends Screen> T findScreen(Class<T> screenType) {
        Screen screen = Minecraft.getInstance().screen;
        while (screen instanceof NeoPopupScreen popup) screen = popup.parent;
        return screenType.isInstance(screen) ? screenType.cast(screen) : null;
    }

    private static void open(java.util.function.Function<Screen, NeoPopupScreen> factory) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.setScreen(factory.apply(minecraft.screen)));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(240, Math.max(1, width - 24));
        messageLines = font.split(message, Math.max(1, panelWidth - 32));
        messageLineHeight = Math.max(12, font.lineHeight + 3);
        int countdownHeight = mode == Mode.COUNTDOWN ? COUNTDOWN_HEIGHT : 0;
        int footerHeight = countdownHeight + 8 + BUTTON_HEIGHT + BOTTOM_PADDING;
        int availableHeight = Math.max(1, height - 16 - PLAQUE_OVERHANG);
        int lineCapacity = Math.max(0, (availableHeight - MESSAGE_TOP - footerHeight) / messageLineHeight);
        visibleMessageLines = Math.min(Math.min(MAX_MESSAGE_LINES, messageLines.size()), lineCapacity);
        int contentHeight = MESSAGE_TOP + Math.max(1, visibleMessageLines) * messageLineHeight + footerHeight;
        panelHeight = Math.min(availableHeight, Math.max(mode == Mode.COUNTDOWN ? 134 : 112, contentHeight));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight + PLAQUE_OVERHANG) / 2;

        int buttonY = panelY + panelHeight - BOTTOM_PADDING - BUTTON_HEIGHT;
        countdownY = buttonY - COUNTDOWN_HEIGHT;
        if (secondaryLabel == null) {
            int buttonWidth = Math.max(1, Math.min(100, panelWidth - 36));
            addRenderableWidget(new NeoButton(Button.builder(actionLabel, ignored -> finish(onAction))
                    .bounds(panelX + (panelWidth - buttonWidth) / 2, buttonY, buttonWidth, BUTTON_HEIGHT))
                    .style(NeoButton.Style.PRIMARY));
        } else {
            int buttonWidth = Math.max(1, Math.min(80, (panelWidth - 44) / 2));
            int firstX = panelX + (panelWidth - buttonWidth * 2 - 8) / 2;
            addRenderableWidget(new NeoButton(Button.builder(actionLabel, ignored -> finish(onAction))
                    .bounds(firstX, buttonY, buttonWidth, BUTTON_HEIGHT)).style(NeoButton.Style.PRIMARY));
            addRenderableWidget(new NeoButton(Button.builder(secondaryLabel, ignored -> finish(onSecondary))
                    .bounds(firstX + buttonWidth + 8, buttonY, buttonWidth, BUTTON_HEIGHT)));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (mode == Mode.COUNTDOWN && !finished && remainingTicks > 0 && --remainingTicks == 0) {
            finish(onTimeout);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (parent != null) {
            parent.render(graphics, mouseX, mouseY, partialTick);
        } else {
            renderBackground(graphics, mouseX, mouseY, partialTick);
        }
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, POPUP_Z);
        try {
            graphics.fill(0, 0, width, height, COLOR_OVERLAY);
            renderPanel(graphics);
            // Screen.render redraws the background; only draw our registered widgets here.
            for (var renderable : renderables) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

    private void renderPanel(GuiGraphics graphics) {
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COLOR_PANEL);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight, COLOR_FRAME_LIGHT);
        graphics.renderOutline(panelX + 2, panelY + 2, panelWidth - 4, panelHeight - 4, COLOR_FRAME);
        graphics.fill(panelX + 5, panelY + 5, panelX + panelWidth - 5, panelY + panelHeight - 5,
                COLOR_PANEL_INNER);

        renderTitlePlaque(graphics);
        drawCentered(graphics, title, panelY + 18, COLOR_TEAL);
        int textY = panelY + MESSAGE_TOP;
        boolean truncated = messageLines.size() > visibleMessageLines;
        for (int index = 0; index < visibleMessageLines; index++) {
            FormattedCharSequence line = truncated && index == visibleMessageLines - 1
                    ? Component.literal("...").getVisualOrderText() : messageLines.get(index);
            graphics.drawString(font, line, panelX + 16, textY + index * messageLineHeight,
                    COLOR_TEXT, false);
        }

        if (mode == Mode.COUNTDOWN) {
            int seconds = Math.max(0, (remainingTicks + 19) / 20);
            drawCentered(graphics, Component.literal(seconds + " วินาที"), countdownY, COLOR_GOLD);
            int barX = panelX + 18;
            int barY = countdownY + 12;
            int barWidth = panelWidth - 36;
            countdownBar.bounds(barX, barY, barWidth, 6)
                    .progress(durationTicks == 0 ? 0F : remainingTicks / (float) durationTicks)
                    .render(graphics, 0, 0, 0F);
        }
    }

    private void renderTitlePlaque(GuiGraphics graphics) {
        int plaqueWidth = Math.min(110, panelWidth);
        int plaqueX = panelX + (panelWidth - plaqueWidth) / 2;
        int plaqueY = panelY - PLAQUE_OVERHANG;
        // Decorative primary-state PNG only: the plaque is not an input widget.
        RenderSystem.enableBlend();
        graphics.blit(PLAQUE_TEXTURE, plaqueX, plaqueY, plaqueWidth, 20,
                28, 441, 350, 144, 1536, 1024);
        FormattedCharSequence label = Language.getInstance().getVisualOrder(
                font.substrByWidth(Component.literal("แจ้งเตือน"), Math.max(1, plaqueWidth - 12)));
        graphics.drawString(font, label, plaqueX + (plaqueWidth - font.width(label)) / 2,
                plaqueY + (20 - font.lineHeight) / 2, COLOR_TEXT, false);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int y, int color) {
        FormattedCharSequence clipped = Language.getInstance().getVisualOrder(
                font.substrByWidth(text, Math.max(1, panelWidth - 32)));
        graphics.drawString(font, clipped, panelX + (panelWidth - font.width(clipped)) / 2, y, color, false);
    }

    private void finish(Runnable callback) {
        if (finished) return;
        finished = true;
        if (minecraft != null) minecraft.setScreen(parent);
        callback.run();
    }

    @Override
    public void onClose() {
        if (secondaryLabel != null) finish(onSecondary);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
