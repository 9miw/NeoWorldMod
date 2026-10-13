package com.ambition.neoworld.client.gui.dungeon;

import java.util.ArrayList;
import java.util.List;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.network.dungeon.DungeonBrowserDataPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Shows the dungeon's configured capacity before server-validated room creation. */
public final class DungeonCreateRoomScreen extends Screen {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/dungeon/dungeon_create_frame.png");
    private static final String[] MODES = {"solo", "trio", "raid"};
    private static final int[] CAPACITIES = {1, 3, 6};
    private final Screen parent;
    private final DungeonBrowserDataPayload.Entry dungeon;
    private final List<NeoButton> buttons = new ArrayList<>();
    private NeoButton create;
    private int selected = -1;
    private int x, y, w, h, contentX, contentWidth, contentY;
    private boolean preview;

    public DungeonCreateRoomScreen(Screen parent, DungeonBrowserDataPayload.Entry dungeon) {
        super(Component.literal("สร้างห้องดันเจี้ยน"));
        this.parent = parent;
        this.dungeon = dungeon;
        // Older definitions can contain multiple modes; do not silently pick one.
        if (Integer.bitCount(dungeon.modeMask()) == 1 && (dungeon.modeMask() & ~7) == 0) {
            selected = Integer.numberOfTrailingZeros(dungeon.modeMask());
        }
    }

    @Override protected void init() {
        buttons.clear();
        w = Math.min(520, width - 12);
        h = Math.min(310, height - 12);
        x = (width - w) / 2; y = (height - h) / 2;
        preview = w >= 430 && h >= 270;
        contentX = x + (preview ? Math.round(w * .54F) : 24);
        contentWidth = x + w - 24 - contentX;
        contentY = y + 62;
        add(Button.builder(Component.literal("ย้อนกลับ"), ignored -> onClose())
                .bounds(x + 24, y + h - 44, Math.min(156, (w - 60) / 2), 26), NeoButton.Style.STANDARD);
        add(Button.builder(Component.literal("X"), ignored -> onClose())
                .bounds(x + w - 40, y + 38, 16, 16)
                .tooltip(Tooltip.create(Component.literal("ย้อนกลับ"))), NeoButton.Style.STANDARD);
        create = add(Button.builder(Component.literal("สร้างห้อง"), ignored -> createRoom())
                .bounds(x + w - 24 - Math.min(156, (w - 60) / 2), y + h - 44,
                        Math.min(156, (w - 60) / 2), 26), NeoButton.Style.PRIMARY);
        refresh();
    }

    private NeoButton add(Button.Builder builder, NeoButton.Style style) {
        var button = addRenderableWidget(new NeoButton(builder).style(style));
        buttons.add(button);
        return button;
    }

    private void refresh() {
        if (create != null) create.active = selected >= 0;
    }

    private void createRoom() {
        if (selected < 0 || minecraft.getConnection() == null) return;
        // Command identifiers come from the server, but still reject token injection.
        if (!dungeon.id().matches("[a-z0-9_]+")) return;
        create.active = false;
        minecraft.getConnection().sendCommand("neoworld dungeon room create " + dungeon.id() + " " + MODES[selected]);
        // Failure messages remain visible in chat; success opens the waiting room from the server.
        minecraft.setScreen(null);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);
        g.blit(FRAME, x, y, w, h, 110, 0, 1450, 915, 1672, 941);
        g.drawCenteredString(font, title, x + w / 2, y + Math.round(h * .075F), 0xE7ECEF);
        if (preview) {
            var image = ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/dungeon/previews/"
                    + (dungeon.structureId().contains("forgotten_mine") ? "forgotten_mine" : "default_dungeon") + ".png");
            int pw = contentX - x - 38;
            int ph = h - 142;
            // Center-crop the image instead of stretching its aspect ratio.
            int sourceHeight = image.getPath().contains("default_dungeon") ? 941 : 940;
            int cropWidth = Math.min(1672, Math.round(sourceHeight * pw / (float) ph));
            int cropHeight = Math.min(sourceHeight, Math.round(1672 * ph / (float) pw));
            g.blit(image, x + 24, contentY, pw, ph, (1672 - cropWidth) / 2F,
                    (sourceHeight - cropHeight) / 2F, cropWidth, cropHeight, 1672, sourceHeight);
            g.renderOutline(x + 23, contentY - 1, pw + 2, ph + 2, 0xFF70787D);
            g.drawCenteredString(font, "Wave " + dungeon.waveCount(), x + 24 + pw / 2, contentY + ph + 10, 0xD1D8DD);
        }
        line(g, dungeon.displayName(), contentY, 0xF2C14E);
        String time = dungeon.timeLimitSeconds() <= 0 ? "ไม่จำกัด" : "%d:%02d".formatted(dungeon.timeLimitSeconds() / 60, dungeon.timeLimitSeconds() % 60);
        line(g, "Lv. " + dungeon.minimumLevel() + "+  | เวลา " + time, contentY + 18, 0xABB4BA);
        g.drawString(font, NeoGuiIcons.label(NeoGuiIcons.MEMBER, "จำนวนผู้เล่น"), contentX, contentY + 54, 0xE7ECEF, false);
        line(g, selected < 0 ? "ต้องกำหนดโหมดเดียวให้ดันเจี้ยน" : "ดันเจี้ยน " + CAPACITIES[selected] + " คน", contentY + 74, 0x8FE5DE);
        int requirementY = contentY + 98;
        if (requirementY + 30 < y + h - 48) {
            g.renderOutline(contentX, requirementY - 4, contentWidth, 35, 0xFF394247);
            String requirement = selected < 0 ? "ไม่มีโหมดที่รองรับ" : selected == 0 ? "เล่นคนเดียว" : "ต้องเป็นหัวหน้าปาร์ตี้";
            Component heading = NeoGuiIcons.label(selected > 0 ? NeoGuiIcons.LEADER : NeoGuiIcons.MEMBER, requirement);
            g.drawString(font, heading, contentX + Math.max(3, (contentWidth - font.width(heading)) / 2), requirementY, 0xE7ECEF, false);
            String detail = selected < 0 ? "" : selected == 0 ? "ไม่ต้องมีปาร์ตี้" : "ปาร์ตี้ขนาด " + CAPACITIES[selected] + " คน";
            g.drawCenteredString(font, detail, contentX + contentWidth / 2, requirementY + 15, 0xABB4BA);
        }
        for (NeoButton button : buttons) button.render(g, mx, my, delta);
    }
    private void line(GuiGraphics g, String text, int yy, int color) {
        g.drawString(font, font.plainSubstrByWidth(text, contentWidth), contentX, yy, color, false);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
