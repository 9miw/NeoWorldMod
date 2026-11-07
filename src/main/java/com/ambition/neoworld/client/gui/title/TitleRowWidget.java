package com.ambition.neoworld.client.gui.title;

import java.util.function.BooleanSupplier;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoProgressBar;
import com.ambition.neoworld.network.title.TitleGuiPagePayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Custom list-card rendering; vanilla Button retains keyboard, click, sound, focus and narration. */
final class TitleRowWidget extends Button {
    private static final int W = 314, H = 32;
    private final TitleGuiPagePayload.Entry entry;
    private final BooleanSupplier selected, equipped;
    private final NeoProgressBar progress = new NeoProgressBar().showPercentage(false);

    TitleRowWidget(int x, int y, int width, int height, TitleGuiPagePayload.Entry entry,
            BooleanSupplier selected, BooleanSupplier equipped, Runnable action) {
        super(Button.builder(Component.literal(entry.name() + " • " + (entry.unlocked() ? "ปลดล็อกแล้ว" : "ยังไม่ปลดล็อก")),
                button -> action.run()).bounds(x, y, width, height));
        this.entry = entry; this.selected = selected; this.equipped = equipped;
        setTooltip(Tooltip.create(Component.literal(entry.name() + " [" + entry.id() + "]\n" + entry.requirement()
                + (!entry.unlocked() && entry.target() > 0 ? "\n" + entry.progress() + " / " + entry.target() : ""))));
    }
    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(getX(), getY(), 0);
        g.pose().scale(getWidth() / (float) W, getHeight() / (float) H, 1);
        try {
            int state = !active ? 3 : selected.getAsBoolean() ? 2 : isHoveredOrFocused() ? 1 : 0;
            TitleTextures.row(g, state, W, H);
            boolean counting = !entry.unlocked() && entry.target() > 0;
            String status = equipped.getAsBoolean() ? "กำลังใส่" : entry.unlocked() ? "ปลดล็อกแล้ว"
                    : counting ? "ยังไม่ปลดล็อก • " + entry.progress() + " / " + entry.target() : "ยังไม่ปลดล็อก";
            if (counting) status = entry.progress() + " / " + entry.target();
            int statusWidth = Math.clamp(font.width(status), 56, 90);
            int statusX = W - 10 - statusWidth;
            int nameWidth = statusX - 18;
            int color = !active ? NeoGuiStyle.COLOR_DISABLED : entry.rare() ? NeoGuiStyle.COLOR_GOLD : NeoGuiStyle.COLOR_TEXT;
            g.drawString(font, font.plainSubstrByWidth(entry.name(), nameWidth), 12, 6, color, false);
            g.drawString(font, entry.rare() ? "หายาก" : entry.category() == 1 ? "เชิงขำ" : "ทั่วไป", 12, 19,
                    entry.rare() ? 0xFF78D6E0 : 0xFF95A6B6, false);
            if (equipped.getAsBoolean()) {
                TitleTextures.control(g, 2, statusX - 4, 6, statusWidth + 8, 18);
            }
            int statusColor = !active ? NeoGuiStyle.COLOR_DISABLED : equipped.getAsBoolean()
                    ? NeoGuiStyle.COLOR_ACCENT : NeoGuiStyle.COLOR_TEXT_MUTED;
            boolean chosen = selected.getAsBoolean() && !equipped.getAsBoolean();
            g.drawString(font, font.plainSubstrByWidth(status, statusWidth), statusX, counting || chosen ? 6 : 11, statusColor, false);
            if (chosen && !counting) g.drawString(font, "เลือกดู ›", W - 48, 21, 0xFF28D9D9, false);
            if (counting) progress.bounds(W - 100, 22, 88, 4).color(NeoGuiStyle.COLOR_ACCENT)
                    .enabled(active).progress(entry.progress() / (double) entry.target()).render(g, font);
        } finally { g.pose().popPose(); }
    }
}
