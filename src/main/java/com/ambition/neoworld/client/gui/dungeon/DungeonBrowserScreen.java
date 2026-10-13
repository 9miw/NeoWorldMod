package com.ambition.neoworld.client.gui.dungeon;

import com.ambition.neoworld.client.gui.style.NeoGuiRender;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoButton;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.dungeon.DungeonBrowserDataPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class DungeonBrowserScreen extends Screen {
    private static final int SOURCE_WIDTH = 1672;
    private static final int SOURCE_HEIGHT = 940;
    private static final int VISIBLE_ROWS = 3;
    private static final ResourceLocation FRAME = texture("textures/gui/dungeon/dungeon_browser_frame.png");
    private static final ResourceLocation WIDGETS = texture("textures/gui/dungeon/dungeon_browser_widgets.png");
    private static final int WIDGET_TEXTURE_SIZE = 1254;
    // Tight alpha bounds for each generated atlas sprite: [row][state][x,y,width,height].
    private static final int[][][] WIDGET_BOUNDS = {
            {{89, 23, 186, 268}, {345, 55, 237, 238}, {668, 47, 259, 257}, {972, 20, 200, 293}},
            {{92, 326, 183, 300}, {335, 343, 286, 283}, {636, 326, 264, 300}, {976, 313, 248, 313}},
            {{0, 626, 293, 313}, {328, 626, 297, 313}, {627, 626, 300, 313}, {1002, 626, 174, 313}},
            {{0, 939, 313, 301}, {313, 939, 313, 307}, {626, 939, 309, 307}, {994, 939, 250, 296}}
    };
    private static final ResourceLocation FORGOTTEN_MINE = texture(
            "textures/gui/dungeon/previews/forgotten_mine.png");
    private static final ResourceLocation DEFAULT_PREVIEW = texture(
            "textures/gui/dungeon/previews/default_dungeon.png");

    private List<DungeonBrowserDataPayload.Entry> entries = List.of();
    private String selectedDungeonId = "";
    private int scrollOffset;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int levelFilter;
    private int modeFilter;
    private boolean draggingScrollbar;
    private int scrollbarDragOffset;
    private boolean dataReceived;
    private String status = "กำลังโหลดข้อมูล...";
    private final List<NeoButton> buttons = new ArrayList<>();

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
        createButtons();
    }

    private void createButtons() {
        buttons.clear();
        int[] levels = {0, 10, 20, 30, 50, 70};
        String[] levelLabels = {"ALL", "10+", "20+", "30+", "50+", "70+"};
        int chipX = sx(91);
        int chipY = sy(210);
        int chipWidth = sxSize(85);
        int chipHeight = sySize(60);
        int chipGap = sxSize(6);
        for (int i = 0; i < levels.length; i++) {
            int level = levels[i];
            NeoButton button = addNeoButton(Button.builder(Component.literal(levelLabels[i])
                    .withStyle(style -> style.withColor(0xF2C14E)), ignored -> {
                        levelFilter = level;
                        refreshFilterSelection();
                    }).bounds(chipX + i * (chipWidth + chipGap), chipY, chipWidth, chipHeight),
                    NeoButton.Style.STANDARD);
            button.selected(levelFilter == level);
        }

        int[] modes = {0, 1, 2, 4};
        String[] modeLabels = {"ALL", "1 คน", "3 คน", "6 คน"};
        int modeX = sx(91);
        int modeWidth = sxSize(130);
        for (int i = 0; i < modes.length; i++) {
            int mode = modes[i];
            NeoButton button = addNeoButton(Button.builder(Component.literal(modeLabels[i])
                    .withStyle(style -> style.withColor(0x8FE5DE)), ignored -> {
                        modeFilter = mode;
                        refreshFilterSelection();
                    }).bounds(modeX + i * (modeWidth + chipGap), sy(335), modeWidth, chipHeight),
                    NeoButton.Style.PRIMARY);
            button.selected(modeFilter == mode);
        }

        addNeoButton(Button.builder(Component.literal("X"), ignored -> onClose())
                        .bounds(sx(1560), sy(90), sxSize(60), sySize(55))
                        .tooltip(Tooltip.create(Component.literal("ปิดหน้าต่าง"))),
                NeoButton.Style.DESTRUCTIVE).textScrolling(false);
        NeoButton select = addNeoButton(Button.builder(Component.literal("เลือกดันเจี้ยน"), ignored -> {
                            var entry = selected();
                            if (entry != null) minecraft.setScreen(new DungeonCreateRoomScreen(this, entry));
                        })
                        .bounds(sx(1180), sy(780), sxSize(417), sySize(90))
                        .tooltip(Tooltip.create(Component.literal("เลือกโหมดและสร้างห้องดันเจี้ยน"))),
                NeoButton.Style.PRIMARY);
        select.active = selected() != null;
    }

    private NeoButton addNeoButton(Button.Builder builder, NeoButton.Style style) {
        NeoButton button = addRenderableWidget(new NeoButton(builder).style(style));
        buttons.add(button);
        return button;
    }

    void receiveData(DungeonBrowserDataPayload payload) {
        entries = payload.entries();
        dataReceived = true;
        if (entries.stream().noneMatch(entry -> entry.id().equals(selectedDungeonId))) {
            selectedDungeonId = entries.isEmpty() ? "" : entries.getFirst().id();
        }
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
        if (!status.isEmpty()) drawCentered(graphics, status, 1180, 1597, 746, NeoGuiStyle.COLOR_TEXT_MUTED);
        if (!buttons.isEmpty()) buttons.getLast().active = selected() != null;
        for (NeoButton button : buttons) button.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderFilters(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "เลเวลที่แนะนำ", sx(92), sy(168), NeoGuiStyle.COLOR_GOLD, false);
        graphics.drawString(font, "โหมดผู้เล่น", sx(92), sy(290), 0xFF8FE5DE, false);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<DungeonBrowserDataPayload.Entry> visibleEntries = visibleEntries();
        int listX = sx(80);
        int listY = sy(420);
        int listWidth = sxSize(535);
        int listHeight = sySize(440);
        int gap = Math.max(2, sySize(8));
        int rowHeight = (listHeight - gap * (VISIBLE_ROWS - 1)) / VISIBLE_ROWS;

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scrollOffset + row;
            int y = listY + row * (rowHeight + gap);
            boolean hover = NeoGuiRender.inside(mouseX, mouseY, listX, y, listWidth, rowHeight);
            boolean selected = index < visibleEntries.size()
                    && visibleEntries.get(index).id().equals(selectedDungeonId);
            int color = selected ? 0xE0184B48
                    : hover ? NeoGuiStyle.COLOR_SLOT_HOVER : NeoGuiStyle.COLOR_SLOT;
            graphics.fill(listX, y, listX + listWidth, y + rowHeight, color);
            graphics.renderOutline(listX, y, listWidth, rowHeight,
                    selected ? NeoGuiStyle.COLOR_ACCENT : NeoGuiStyle.COLOR_FRAME);
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
            int textY = y + Math.max(3, (rowHeight - 24) / 2);
            int textX = thumbX + thumbWidth + 9;
            graphics.drawString(font, NeoGuiRender.trim(font, entry.displayName(), listX + listWidth - textX - 5),
                    textX, textY - 3, NeoGuiStyle.COLOR_TEXT, false);
            graphics.drawString(font, NeoGuiRender.trim(font, "Lv. " + entry.minimumLevel() + "+ | " + modeText(entry.modeMask()), listX + listWidth - textX - 5), textX, textY + 12,
                    NeoGuiStyle.COLOR_TEXT_MUTED, false);
        }
        if (visibleEntries.isEmpty()) {
            String empty = dataReceived ? "ไม่พบดันเจี้ยนตามตัวกรอง" : "กำลังโหลดข้อมูล...";
            NeoGuiRender.centered(graphics, font, empty, listX, listWidth,
                    listY + listHeight / 2 - 4, NeoGuiStyle.COLOR_TEXT_MUTED);
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
        // Source sprites are not on an equal grid. Use measured visible regions.
        int[] columns = {98, 393, 690, 987};
        int[] bounds = row < 2 ? new int[] {columns[state], row == 0 ? 110 : 376, 165, 164}
                : new int[] {columns[state] + 49, row == 2 ? 615 : 892, 64, row == 2 ? 216 : 258};
        graphics.blit(WIDGETS, x, y, width, height, bounds[0], bounds[1],
                bounds[2], bounds[3], WIDGET_TEXTURE_SIZE, WIDGET_TEXTURE_SIZE);
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
            int iconHeight = Math.max(24, sySize(82));
            graphics.fill(x, y, x + nodeWidth, y + iconHeight, NeoGuiStyle.COLOR_SLOT);
            graphics.renderOutline(x, y, nodeWidth, iconHeight, NeoGuiStyle.COLOR_FRAME_LIGHT);
            boolean boss = i == nodes - 1;
            ItemStack icon = new ItemStack(boss ? Items.SKELETON_SKULL
                    : i == 0 ? Items.CHEST_MINECART : Items.RAW_GOLD);
            graphics.renderItem(icon, x + (nodeWidth - 16) / 2, y + 7);
            String label = boss ? "บอส" : i == 0 ? "เริ่มต้น" : entry.waveCount() > 2 && i == nodes - 2 ? "Wave 2-" + entry.waveCount() : "Wave " + i;
            NeoGuiRender.centered(graphics, font, NeoGuiRender.trim(font, label, nodeWidth + gap / 2), x, nodeWidth, y + iconHeight + 5, NeoGuiStyle.COLOR_TEXT);
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
        int line = Math.max(font.lineHeight + 5, sySize(39));
        if (entry == null) {
            graphics.drawString(font, "ไม่พบดันเจี้ยน", x, y, NeoGuiStyle.COLOR_TEXT_MUTED, false);
            return;
        }
        graphics.renderItem(new ItemStack(Items.CHEST_MINECART), x, y - 3);
        graphics.drawString(font, NeoGuiRender.trim(font, entry.displayName(), sxSize(320)), x + 22, y,
                NeoGuiStyle.COLOR_GOLD, false);
        y += Math.max(22, line);
        NeoGuiRender.separator(graphics, x, sx(1568), y);
        y += 7;
        drawDetail(graphics, "เลเวลที่แนะนำ", "Lv. " + entry.minimumLevel() + "+", x, y); y += line;
        String time = entry.timeLimitSeconds() <= 0 ? "ไม่จำกัดเวลา" : formatTime(entry.timeLimitSeconds());
        drawDetail(graphics, "เวลาประมาณ", time, x, y); y += line;
        drawDetail(graphics, "โหมดที่รองรับ", modeText(entry.modeMask()), x, y); y += line;
        drawDetail(graphics, "จำนวนเวฟ", entry.waveCount() + " เวฟ", x, y); y += line;
        drawDetail(graphics, "บอสสุดท้าย", entry.bossName().isBlank() ? "ไม่มี" : entry.bossName(), x, y); y += line;
        NeoGuiRender.separator(graphics, x, sx(1568), y);
        y += 7;
        graphics.drawString(font, "รางวัลที่อาจได้รับ", x, y, NeoGuiStyle.COLOR_GOLD, false); y += line;
        drawDetail(graphics, "EXP", Integer.toString(entry.experience()), x, y); y += line;
        drawDetail(graphics, "Silver", Long.toString(entry.money()), x, y); y += line;
        renderRewardIcons(graphics, x, y, entry);
    }

    private void renderRewardIcons(GuiGraphics graphics, int x, int y, DungeonBrowserDataPayload.Entry entry) {
        List<ItemStack> rewards = new ArrayList<>();
        if (entry.experience() > 0) rewards.add(new ItemStack(Items.EXPERIENCE_BOTTLE));
        if (entry.money() > 0) rewards.add(new ItemStack(Items.GOLD_NUGGET));
        if (!entry.lootTableId().isBlank()) rewards.add(new ItemStack(Items.CHEST));
        int slot = Math.max(20, sxSize(48));
        for (int i = 0; i < rewards.size(); i++) {
            int slotX = x + i * (slot + 3);
            graphics.fill(slotX, y, slotX + slot, y + slot, NeoGuiStyle.COLOR_SLOT);
            graphics.renderOutline(slotX, y, slot, slot, NeoGuiStyle.COLOR_FRAME_LIGHT);
            graphics.renderItem(rewards.get(i), slotX + (slot - 16) / 2, y + (slot - 16) / 2);
        }
    }

    private void drawDetail(GuiGraphics graphics, String label, String value, int x, int y) {
        int available = sx(1568) - x;
        graphics.drawString(font, NeoGuiRender.trim(font, label, available / 2 - 3), x, y, NeoGuiStyle.COLOR_TEXT_MUTED, false);
        int right = sx(1568);
        NeoGuiRender.right(graphics, font, NeoGuiRender.trim(font, value, available / 2 - 3), right, y,
                NeoGuiStyle.COLOR_TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        int listX = sx(80);
        int listY = sy(420);
        int listWidth = sxSize(535);
        int listHeight = sySize(440);
        int gap = Math.max(2, sySize(8));
        int rowHeight = (listHeight - gap * (VISIBLE_ROWS - 1)) / VISIBLE_ROWS;
        if (NeoGuiRender.inside(mouseX, mouseY, listX, listY, listWidth, listHeight)) {
            int row = (int) (mouseY - listY) / (rowHeight + gap);
            int index = scrollOffset + row;
            if (row < VISIBLE_ROWS && index < visibleEntries().size()) {
                selectedDungeonId = visibleEntries().get(index).id();
            }
            return true;
        }
        if (handleScrollbarClick(mouseX, mouseY, listY, listHeight)) return true;
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
            int listY = sy(420);
            int buttonHeight = Math.max(12, sySize(52));
            int trackY = listY + buttonHeight;
            int trackHeight = sySize(440) - buttonHeight * 2;
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

    private void refreshFilterSelection() {
        List<DungeonBrowserDataPayload.Entry> visibleEntries = visibleEntries();
        if (visibleEntries.stream().noneMatch(entry -> entry.id().equals(selectedDungeonId))) {
            selectedDungeonId = visibleEntries.isEmpty() ? "" : visibleEntries.getFirst().id();
        }
        scrollOffset = 0;
        status = visibleEntries.isEmpty() ? "ไม่พบดันเจี้ยนตามตัวกรอง" : "";
        int[] levels = {0, 10, 20, 30, 50, 70};
        int[] modes = {0, 1, 2, 4};
        for (int i = 0; i < levels.length; i++) buttons.get(i).selected(levelFilter == levels[i]);
        for (int i = 0; i < modes.length; i++) buttons.get(levels.length + i).selected(modeFilter == modes[i]);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (NeoGuiRender.inside(mouseX, mouseY, sx(80), sy(420), sxSize(580), sySize(440))) {
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
        return visibleEntries().stream()
                .filter(entry -> entry.id().equals(selectedDungeonId))
                .findFirst()
                .orElse(null);
    }

    private int maxScrollOffset() {
        return Math.max(0, visibleEntries().size() - VISIBLE_ROWS);
    }

    private List<DungeonBrowserDataPayload.Entry> visibleEntries() {
        return entries.stream()
                .filter(entry -> levelFilter == 0 || entry.minimumLevel() >= levelFilter)
                .filter(entry -> modeFilter == 0 || (entry.modeMask() & modeFilter) != 0)
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

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, path);
    }
}
