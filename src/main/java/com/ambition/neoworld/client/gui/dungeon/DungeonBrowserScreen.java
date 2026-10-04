package com.ambition.neoworld.client.gui.dungeon;

import com.ambition.neoworld.client.gui.style.NeoGuiRender;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.DungeonBrowserDataPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class DungeonBrowserScreen extends Screen {
    private static final int SOURCE_WIDTH = 1672;
    private static final int SOURCE_HEIGHT = 940;
    private static final int VISIBLE_ROWS = 6;
    private static final ResourceLocation FRAME = texture("textures/gui/dungeon/dungeon_browser_frame.png");
    private static final ResourceLocation WIDGETS = texture("textures/gui/dungeon/dungeon_browser_widgets.png");
    private static final int WIDGET_TEXTURE_SIZE = 1254;
    private static final int WIDGET_CELL = 313;
    private static final ResourceLocation FORGOTTEN_MINE = texture(
            "textures/gui/dungeon/previews/forgotten_mine.png");
    private static final ResourceLocation DEFAULT_PREVIEW = texture(
            "textures/gui/dungeon/previews/default_dungeon.png");

    private List<DungeonBrowserDataPayload.Entry> entries = List.of();
    private int selectedIndex = -1;
    private int scrollOffset;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int levelFilter;
    private int difficultyFilter = -1;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private String status = "";

    public DungeonBrowserScreen() {
        super(Component.literal("เลือกดันเจี้ยน"));
    }

    @Override
    protected void init() {
        float aspect = SOURCE_WIDTH / (float) SOURCE_HEIGHT;
        int availableWidth = Math.max(1, Math.min(width - 24, 840));
        int availableHeight = Math.max(1, Math.min(height - 20, 472));
        if (availableWidth / (float) availableHeight > aspect) {
            panelHeight = availableHeight;
            panelWidth = Math.round(panelHeight * aspect);
        } else {
            panelWidth = availableWidth;
            panelHeight = Math.round(panelWidth / aspect);
        }
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
    }

    void receiveData(DungeonBrowserDataPayload payload) {
        entries = payload.entries();
        selectedIndex = entries.isEmpty() ? -1 : 0;
        scrollOffset = 0;
        status = entries.isEmpty() ? "ยังไม่มีดันเจี้ยนที่เปิดใช้งาน" : "";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(FRAME, panelX, panelY, panelWidth, panelHeight, 0, 0,
                SOURCE_WIDTH, SOURCE_HEIGHT, SOURCE_WIDTH, SOURCE_HEIGHT);

        drawCentered(graphics, "เลือกดันเจี้ยน", 560, 1120, 61, NeoGuiStyle.COLOR_TEXT);
        renderFilters(graphics, mouseX, mouseY);
        renderList(graphics, mouseX, mouseY);
        renderPreview(graphics);
        renderDetails(graphics);
        drawCentered(graphics, "X", 1560, 1620, 108, NeoGuiStyle.COLOR_TEXT);
        if (!status.isEmpty()) drawCentered(graphics, status, 1180, 1597, 746, NeoGuiStyle.COLOR_TEXT_MUTED);
    }

    private void renderFilters(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "เลเวลที่แนะนำ", sx(92), sy(168), NeoGuiStyle.COLOR_TEXT, false);
        int[] levels = {0, 10, 20, 30, 50, 70};
        String[] labels = {"ทั้งหมด", "10+", "20+", "30+", "50+", "70+"};
        int chipX = sx(91);
        int chipY = sy(197);
        int chipWidth = sxSize(49);
        int chipGap = sxSize(6);
        for (int i = 0; i < levels.length; i++) {
            drawChip(graphics, mouseX, mouseY, chipX + i * (chipWidth + chipGap), chipY,
                    chipWidth, sySize(38), labels[i], levelFilter == levels[i]);
        }

        graphics.drawString(font, "ระดับความยาก", sx(446), sy(168), NeoGuiStyle.COLOR_TEXT, false);
        String[] difficulties = {"ทั้งหมด", "ง่าย", "ปกติ", "ยาก"};
        int difficultyX = sx(445);
        int difficultyWidth = sxSize(48);
        for (int i = 0; i < difficulties.length; i++) {
            drawChip(graphics, mouseX, mouseY, difficultyX + i * (difficultyWidth + chipGap), chipY,
                    difficultyWidth, sySize(38), difficulties[i], difficultyFilter == i - 1);
        }
    }

    private void drawChip(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height,
                          String label, boolean selected) {
        boolean hover = NeoGuiRender.inside(mouseX, mouseY, x, y, width, height);
        graphics.fill(x, y, x + width, y + height, selected ? NeoGuiStyle.COLOR_ACCENT_DARK
                : hover ? NeoGuiStyle.COLOR_SLOT_HOVER : NeoGuiStyle.COLOR_SLOT);
        graphics.renderOutline(x, y, width, height,
                selected ? NeoGuiStyle.COLOR_ACCENT : NeoGuiStyle.COLOR_FRAME);
        NeoGuiRender.centered(graphics, font, NeoGuiRender.trim(font, label, width - 4), x, width,
                y + (height - 8) / 2, selected ? NeoGuiStyle.COLOR_GOLD : NeoGuiStyle.COLOR_TEXT);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<DungeonBrowserDataPayload.Entry> visibleEntries = visibleEntries();
        int listX = sx(80);
        int listY = sy(250);
        int listWidth = sxSize(535);
        int listHeight = sySize(620);
        int gap = Math.max(2, sySize(8));
        int rowHeight = (listHeight - gap * (VISIBLE_ROWS - 1)) / VISIBLE_ROWS;

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scrollOffset + row;
            int y = listY + row * (rowHeight + gap);
            boolean hover = NeoGuiRender.inside(mouseX, mouseY, listX, y, listWidth, rowHeight);
            int color = index == selectedIndex ? 0xE0184B48
                    : hover ? NeoGuiStyle.COLOR_SLOT_HOVER : NeoGuiStyle.COLOR_SLOT;
            graphics.fill(listX, y, listX + listWidth, y + rowHeight, color);
            graphics.renderOutline(listX, y, listWidth, rowHeight,
                    index == selectedIndex ? NeoGuiStyle.COLOR_ACCENT : NeoGuiStyle.COLOR_FRAME);
            if (index >= visibleEntries.size()) continue;
            DungeonBrowserDataPayload.Entry entry = visibleEntries.get(index);
            int thumbX = listX + 4;
            int thumbY = y + 4;
            int thumbWidth = Math.max(24, sxSize(138));
            int thumbHeight = Math.max(12, rowHeight - 8);
            ResourceLocation thumbnail = entry.structureId().contains("forgotten_mine")
                    ? FORGOTTEN_MINE : DEFAULT_PREVIEW;
            graphics.blit(thumbnail, thumbX, thumbY, thumbWidth, thumbHeight, 0, 120,
                    SOURCE_WIDTH, 620, SOURCE_WIDTH, SOURCE_HEIGHT);
            graphics.renderOutline(thumbX, thumbY, thumbWidth, thumbHeight, NeoGuiStyle.COLOR_FRAME_LIGHT);
            int textY = y + Math.max(3, (rowHeight - 20) / 2);
            int textX = thumbX + thumbWidth + 9;
            graphics.drawString(font, NeoGuiRender.trim(font, entry.displayName(), listWidth - thumbWidth - 88),
                    textX, textY - 3, NeoGuiStyle.COLOR_TEXT, false);
            graphics.drawString(font, "Lv. " + entry.minimumLevel() + "+", textX, textY + 10,
                    NeoGuiStyle.COLOR_TEXT_MUTED, false);
            NeoGuiRender.right(graphics, font, difficultyText(entry.minimumLevel()),
                    listX + listWidth - 9, textY - 3, difficultyColor(entry.minimumLevel()));
            NeoGuiRender.right(graphics, font, "พร้อมเล่น", listX + listWidth - 9, textY + 10,
                    0xFF42E889);
        }
        renderScrollbar(graphics, mouseX, mouseY, listY, listHeight);
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY, int listY, int listHeight) {
        int trackX = sx(620);
        int width = Math.max(12, sxSize(40));
        int buttonHeight = Math.max(12, sySize(52));
        int trackY = listY + buttonHeight;
        int trackHeight = listHeight - buttonHeight * 2;
        int maxOffset = maxScrollOffset();
        boolean enabled = maxOffset > 0;
        int upState = !enabled ? 3 : NeoGuiRender.inside(mouseX, mouseY, trackX, listY, width, buttonHeight) ? 1 : 0;
        int downY = listY + listHeight - buttonHeight;
        int downState = !enabled ? 3 : NeoGuiRender.inside(mouseX, mouseY, trackX, downY, width, buttonHeight) ? 1 : 0;
        blitWidget(graphics, trackX, listY, width, buttonHeight, upState, 0);
        blitWidget(graphics, trackX, downY, width, buttonHeight, downState, 1);
        blitWidget(graphics, trackX, trackY, width, trackHeight, enabled ? 0 : 3, 3);

        int thumbHeight = scrollbarThumbHeight(trackHeight);
        int thumbY = scrollbarThumbY(trackY, trackHeight, thumbHeight);
        int thumbState = !enabled ? 3 : draggingScrollbar ? 2
                : NeoGuiRender.inside(mouseX, mouseY, trackX, thumbY, width, thumbHeight) ? 1 : 0;
        blitWidget(graphics, trackX, thumbY, width, thumbHeight, thumbState, 2);
    }

    private void blitWidget(GuiGraphics graphics, int x, int y, int width, int height, int state, int row) {
        graphics.blit(WIDGETS, x, y, width, height, state * WIDGET_CELL, row * WIDGET_CELL,
                WIDGET_CELL, WIDGET_CELL, WIDGET_TEXTURE_SIZE, WIDGET_TEXTURE_SIZE);
    }

    private void renderPreview(GuiGraphics graphics) {
        int x = sx(676);
        int y = sy(152);
        int w = sxSize(488);
        int h = sySize(442);
        ResourceLocation preview = selected() != null && selected().structureId().contains("forgotten_mine")
                ? FORGOTTEN_MINE : DEFAULT_PREVIEW;
        graphics.blit(preview, x, y, w, h, 0, 0, SOURCE_WIDTH, SOURCE_HEIGHT, SOURCE_WIDTH, SOURCE_HEIGHT);
        graphics.renderOutline(x, y, w, h, NeoGuiStyle.COLOR_FRAME_LIGHT);

        DungeonBrowserDataPayload.Entry entry = selected();
        if (entry != null) {
            graphics.drawString(font, "เส้นทางการเล่น", sx(693), sy(620), NeoGuiStyle.COLOR_GOLD, false);
            renderRoute(graphics, entry);
        }
    }

    private void renderRoute(GuiGraphics graphics, DungeonBrowserDataPayload.Entry entry) {
        int nodes = Math.min(4, entry.waveCount() + 2);
        int areaX = sx(696);
        int areaWidth = sxSize(448);
        int nodeWidth = Math.min(sxSize(84), (areaWidth - sxSize(24) * (nodes - 1)) / nodes);
        int gap = nodes == 1 ? 0 : (areaWidth - nodeWidth * nodes) / (nodes - 1);
        int y = sy(660);
        for (int i = 0; i < nodes; i++) {
            int x = areaX + i * (nodeWidth + gap);
            graphics.fill(x, y, x + nodeWidth, y + sySize(82), NeoGuiStyle.COLOR_SLOT);
            graphics.renderOutline(x, y, nodeWidth, sySize(82), NeoGuiStyle.COLOR_FRAME_LIGHT);
            boolean boss = i == nodes - 1;
            ItemStack icon = new ItemStack(boss ? Items.SKELETON_SKULL
                    : i == 0 ? Items.CHEST_MINECART : Items.RAW_GOLD);
            graphics.renderItem(icon, x + (nodeWidth - 16) / 2, y + 7);
            String label = boss ? "บอส" : i == 0 ? "เริ่มต้น" : "Wave " + i;
            NeoGuiRender.centered(graphics, font, label, x, nodeWidth, y + sySize(54), NeoGuiStyle.COLOR_TEXT);
            if (i < nodes - 1) {
                graphics.drawString(font, ">", x + nodeWidth + Math.max(1, (gap - font.width(">")) / 2),
                        y + sySize(35), NeoGuiStyle.COLOR_TEXT, false);
            }
        }
    }

    private void renderDetails(GuiGraphics graphics) {
        DungeonBrowserDataPayload.Entry entry = selected();
        int x = sx(1210);
        int y = sy(178);
        int line = Math.max(11, sySize(39));
        if (entry == null) {
            graphics.drawString(font, "ไม่พบดันเจี้ยน", x, y, NeoGuiStyle.COLOR_TEXT_MUTED, false);
            return;
        }
        graphics.renderItem(new ItemStack(Items.CHEST_MINECART), x, y - 3);
        graphics.drawString(font, NeoGuiRender.trim(font, entry.displayName(), sxSize(320)), x + 22, y,
                NeoGuiStyle.COLOR_GOLD, false);
        y += line;
        graphics.drawString(font, difficultyText(entry.minimumLevel()), x + 22, y,
                difficultyColor(entry.minimumLevel()), false);
        y += line;
        NeoGuiRender.separator(graphics, x, sx(1568), y - 7);
        drawDetail(graphics, "เลเวลที่แนะนำ", "Lv. " + entry.minimumLevel() + "+", x, y); y += line;
        String time = entry.timeLimitSeconds() <= 0 ? "ไม่จำกัดเวลา" : formatTime(entry.timeLimitSeconds());
        drawDetail(graphics, "เวลาประมาณ", time, x, y); y += line;
        drawDetail(graphics, "โหมดที่รองรับ", modeText(entry.modeMask()), x, y); y += line;
        drawDetail(graphics, "จำนวนเวฟ", entry.waveCount() + " เวฟ", x, y); y += line;
        drawDetail(graphics, "บอสสุดท้าย", entry.bossName().isBlank() ? "ไม่มี" : entry.bossName(), x, y); y += line;
        NeoGuiRender.separator(graphics, x, sx(1568), y - 7);
        graphics.drawString(font, "รางวัลที่อาจได้รับ", x, y, NeoGuiStyle.COLOR_GOLD, false); y += line;
        drawDetail(graphics, "EXP", Integer.toString(entry.experience()), x, y); y += line;
        drawDetail(graphics, "Silver", Long.toString(entry.money()), x, y); y += line;
        renderRewardIcons(graphics, x, y, entry);
        drawCentered(graphics, "เลือกดันเจี้ยน", 1180, 1597, 818, NeoGuiStyle.COLOR_TEXT);
    }

    private void renderRewardIcons(GuiGraphics graphics, int x, int y, DungeonBrowserDataPayload.Entry entry) {
        ItemStack[] rewards = entry.lootTableId().isBlank()
                ? new ItemStack[] {new ItemStack(Items.EXPERIENCE_BOTTLE), new ItemStack(Items.GOLD_NUGGET)}
                : new ItemStack[] {new ItemStack(Items.DIAMOND), new ItemStack(Items.GOLD_INGOT),
                        new ItemStack(Items.REDSTONE), new ItemStack(Items.LAPIS_LAZULI)};
        int slot = Math.max(20, sxSize(48));
        for (int i = 0; i < rewards.length; i++) {
            int slotX = x + i * (slot + 3);
            graphics.fill(slotX, y, slotX + slot, y + slot, NeoGuiStyle.COLOR_SLOT);
            graphics.renderOutline(slotX, y, slot, slot, NeoGuiStyle.COLOR_FRAME_LIGHT);
            graphics.renderItem(rewards[i], slotX + (slot - 16) / 2, y + (slot - 16) / 2);
        }
    }

    private void drawDetail(GuiGraphics graphics, String label, String value, int x, int y) {
        graphics.drawString(font, label, x, y, NeoGuiStyle.COLOR_TEXT_MUTED, false);
        int right = sx(1568);
        NeoGuiRender.right(graphics, font, NeoGuiRender.trim(font, value, sxSize(190)), right, y,
                NeoGuiStyle.COLOR_TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (NeoGuiRender.inside(mouseX, mouseY, sx(1560), sy(90), sxSize(60), sySize(55))) {
            onClose();
            return true;
        }
        if (handleFilterClick(mouseX, mouseY)) return true;
        int listX = sx(80);
        int listY = sy(250);
        int listWidth = sxSize(535);
        int listHeight = sySize(620);
        int gap = Math.max(2, sySize(8));
        int rowHeight = (listHeight - gap * (VISIBLE_ROWS - 1)) / VISIBLE_ROWS;
        if (NeoGuiRender.inside(mouseX, mouseY, listX, listY, listWidth, listHeight)) {
            int row = (int) (mouseY - listY) / (rowHeight + gap);
            int index = scrollOffset + row;
            if (row < VISIBLE_ROWS && index < visibleEntries().size()) selectedIndex = index;
            return true;
        }
        if (handleScrollbarClick(mouseX, mouseY, listY, listHeight)) return true;
        if (selected() != null && NeoGuiRender.inside(mouseX, mouseY, sx(1180), sy(780),
                sxSize(417), sySize(90))) {
            status = "หน้าสร้างห้องจะเพิ่มในขั้นถัดไป";
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleScrollbarClick(double mouseX, double mouseY, int listY, int listHeight) {
        int x = sx(620);
        int width = Math.max(12, sxSize(40));
        if (!NeoGuiRender.inside(mouseX, mouseY, x, listY, width, listHeight)) return false;
        int maxOffset = maxScrollOffset();
        if (maxOffset == 0) return true;
        int buttonHeight = Math.max(12, sySize(52));
        if (mouseY < listY + buttonHeight) {
            scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        }
        if (mouseY >= listY + listHeight - buttonHeight) {
            scrollOffset = Math.min(maxOffset, scrollOffset + 1);
            return true;
        }
        int trackY = listY + buttonHeight;
        int trackHeight = listHeight - buttonHeight * 2;
        int thumbHeight = scrollbarThumbHeight(trackHeight);
        int thumbY = scrollbarThumbY(trackY, trackHeight, thumbHeight);
        if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
            draggingScrollbar = true;
            scrollbarDragOffset = (int) mouseY - thumbY;
        } else {
            setScrollFromThumb((int) mouseY - thumbHeight / 2, trackY, trackHeight, thumbHeight);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int listY = sy(250);
            int buttonHeight = Math.max(12, sySize(52));
            int trackY = listY + buttonHeight;
            int trackHeight = sySize(620) - buttonHeight * 2;
            int thumbHeight = scrollbarThumbHeight(trackHeight);
            setScrollFromThumb((int) mouseY - scrollbarDragOffset, trackY, trackHeight, thumbHeight);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int scrollbarThumbHeight(int trackHeight) {
        int count = visibleEntries().size();
        return maxScrollOffset() == 0 ? trackHeight
                : Math.max(sySize(72), trackHeight * VISIBLE_ROWS / Math.max(VISIBLE_ROWS, count));
    }

    private int scrollbarThumbY(int trackY, int trackHeight, int thumbHeight) {
        int maxOffset = maxScrollOffset();
        return maxOffset == 0 ? trackY : trackY + (trackHeight - thumbHeight) * scrollOffset / maxOffset;
    }

    private void setScrollFromThumb(int thumbY, int trackY, int trackHeight, int thumbHeight) {
        int travel = Math.max(1, trackHeight - thumbHeight);
        int clamped = Math.max(trackY, Math.min(trackY + travel, thumbY));
        scrollOffset = Math.round((clamped - trackY) * maxScrollOffset() / (float) travel);
    }

    private boolean handleFilterClick(double mouseX, double mouseY) {
        int chipY = sy(197);
        int chipHeight = sySize(38);
        int chipGap = sxSize(6);
        int[] levels = {0, 10, 20, 30, 50, 70};
        int chipWidth = sxSize(49);
        int startX = sx(91);
        for (int i = 0; i < levels.length; i++) {
            if (NeoGuiRender.inside(mouseX, mouseY, startX + i * (chipWidth + chipGap), chipY,
                    chipWidth, chipHeight)) {
                levelFilter = levels[i];
                selectedIndex = visibleEntries().isEmpty() ? -1 : 0;
                scrollOffset = 0;
                status = levelFilter == 0 ? "แสดงทุกเลเวล" : "กรองเลเวล " + levelFilter + "+";
                return true;
            }
        }
        int difficultyWidth = sxSize(48);
        startX = sx(445);
        for (int i = 0; i < 4; i++) {
            if (NeoGuiRender.inside(mouseX, mouseY, startX + i * (difficultyWidth + chipGap), chipY,
                    difficultyWidth, chipHeight)) {
                difficultyFilter = i - 1;
                selectedIndex = visibleEntries().isEmpty() ? -1 : 0;
                scrollOffset = 0;
                status = difficultyFilter < 0 ? "แสดงทุกระดับความยาก" : "เลือกระดับ " + (i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (NeoGuiRender.inside(mouseX, mouseY, sx(80), sy(250), sxSize(580), sySize(620))) {
            scrollOffset = Math.max(0, Math.min(maxScrollOffset(), scrollOffset - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private DungeonBrowserDataPayload.Entry selected() {
        List<DungeonBrowserDataPayload.Entry> visibleEntries = visibleEntries();
        return selectedIndex >= 0 && selectedIndex < visibleEntries.size() ? visibleEntries.get(selectedIndex) : null;
    }

    private int maxScrollOffset() {
        return Math.max(0, visibleEntries().size() - VISIBLE_ROWS);
    }

    private List<DungeonBrowserDataPayload.Entry> visibleEntries() {
        return entries.stream()
                .filter(entry -> levelFilter == 0 || entry.minimumLevel() >= levelFilter)
                .filter(entry -> difficultyFilter < 0 || difficultyIndex(entry.minimumLevel()) == difficultyFilter)
                .toList();
    }

    private int sx(int sourceX) { return panelX + Math.round(sourceX * panelWidth / (float) SOURCE_WIDTH); }
    private int sy(int sourceY) { return panelY + Math.round(sourceY * panelHeight / (float) SOURCE_HEIGHT); }
    private int sxSize(int sourceWidth) { return Math.max(1, Math.round(sourceWidth * panelWidth / (float) SOURCE_WIDTH)); }
    private int sySize(int sourceHeight) { return Math.max(1, Math.round(sourceHeight * panelHeight / (float) SOURCE_HEIGHT)); }

    private void drawCentered(GuiGraphics graphics, String text, int left, int right, int y, int color) {
        NeoGuiRender.centered(graphics, font, NeoGuiRender.trim(font, text, sxSize(right - left - 12)),
                sx(left), sxSize(right - left), sy(y), color);
    }

    private static String modeText(int mask) {
        StringBuilder result = new StringBuilder();
        if ((mask & 1) != 0) result.append("1 คน");
        if ((mask & 2) != 0) appendMode(result, "3 คน");
        if ((mask & 4) != 0) appendMode(result, "6 คน");
        return result.isEmpty() ? "ไม่มี" : result.toString();
    }

    private static void appendMode(StringBuilder result, String mode) {
        if (!result.isEmpty()) result.append(" / ");
        result.append(mode);
    }

    private static String formatTime(int seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    private static String difficultyText(int level) {
        if (level >= 50) return "โหดมาก";
        if (level >= 30) return "ยาก";
        if (level >= 20) return "ปกติ";
        return "ง่าย";
    }

    private static int difficultyColor(int level) {
        if (level >= 50) return 0xFFFF5268;
        if (level >= 30) return 0xFFFF7A32;
        if (level >= 20) return NeoGuiStyle.COLOR_GOLD;
        return 0xFF42E889;
    }

    private static int difficultyIndex(int level) {
        if (level >= 30) return 2;
        if (level >= 20) return 1;
        return 0;
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, path);
    }
}
