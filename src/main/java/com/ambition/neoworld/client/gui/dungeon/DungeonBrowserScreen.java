package com.ambition.neoworld.client.gui.dungeon;

import java.util.List;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.client.gui.common.NeoSystemIcons;
import com.ambition.neoworld.client.gui.style.NeoGuiRender;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.widget.NeoScrollbar;
import com.ambition.neoworld.network.dungeon.DungeonBrowserDataPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Artwork fits the viewport; text, widgets and items use native Minecraft GUI units. */
public final class DungeonBrowserScreen extends Screen {
    private static final int TEXT = 0xFFE7ECEF, MUTED = 0xFFADB6BA, GOLD = 0xFFF2C14E;
    private List<DungeonBrowserDataPayload.Entry> entries = List.of();
    private String selectedId = "";
    private boolean dataReceived;
    private int panelX, panelY, panelW, panelH;
    private int listX, listY, listW, listH, rowH;
    private int rewardX, rewardY, rewardW;
    private int rewardSlot, rewardPitch;
    private int routeTop, routeSize, routeHeadingTop, bossTop, bossHeight, rewardDividerTop, rewardHeadingTop;
    private boolean inlineRouteCaptions;
    private int metadataBottom;
    private NeoButton selectButton;
    private NeoScrollbar listBar, rewardBar;
    private Component hoveredText;
    private ItemStack hoveredItem;

    public DungeonBrowserScreen() { super(Component.literal("เลือกดันเจี้ยน")); }

    @Override
    protected void init() {
        double oldScroll = listBar == null ? 0 : listBar.getValue();
        double oldRewards = rewardBar == null ? 0 : rewardBar.getValue();
        panelW = Math.max(1, Math.min(734, width - 48));
        // A taller compact shell preserves native text/slot height on small windows.
        panelH = Math.max(1, Math.min(height - 28, Math.max(280, Math.round(panelW * 417F / 734F))));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        listX = panelX + 12; listY = y(83);
        int listBarX = x(238) - 10;
        listW = Math.max(40, listBarX - 4 - listX);
        listH = panelY + panelH - 44 - listY;
        rowH = Math.max(43, font.lineHeight * 3 + 16);
        addRenderableWidget(new NeoButton(Button.builder(Component.literal("X"), b -> onClose())
                .bounds(panelX + panelW + 2, panelY + Math.round(panelW * 50F / 1468F), 18, 18)
                .tooltip(Tooltip.create(Component.literal("ปิดหน้าต่าง")))).textScrolling(false));
        int buttonY = panelY + panelH - 40;
        selectButton = addRenderableWidget(new NeoButton(Button.builder(
                NeoGuiIcons.label(NeoGuiIcons.LEAVE, "สร้างห้อง"), b -> createSelectedRoom())
                .bounds(x(550), buttonY, Math.max(60, x(700) - x(550)), 24)
                .tooltip(Tooltip.create(Component.literal("สร้างห้องและไปหน้ารอเริ่มดันเจี้ยน"))))
                .style(NeoButton.Style.PRIMARY).frameRenderer((g, b) -> {
                    g.setColor(1F, 1F, 1F, 1F);
                    DungeonBrowserV5Skin.socket(g, "primary", b.getX(), b.getY(), b.getWidth(), b.getHeight(), 1134, 280, 100, 8);
                    if (!b.active) g.fill(b.getX()+6, b.getY()+6, b.getX()+b.getWidth()-6, b.getY()+b.getHeight()-6, 0xAA192020);
                    else if (b.isHoveredOrFocused()) g.renderOutline(b.getX()+3, b.getY()+3, b.getWidth()-6, b.getHeight()-6, 0xFF65E4EA);
                    g.setColor(1F, 1F, 1F, 1F);
                }));
        listBar = addRenderableWidget(new NeoScrollbar(listBarX, listY, 6, listH, Component.literal("รายชื่อดันเจี้ยน"))
                .scrollStep(rowH / 2D));
        rewardX = x(258); rewardW = Math.max(18, x(533) - rewardX);
        metadataBottom = Math.max(y(187), y(168) + font.lineHeight + 2);
        var route = DungeonBrowserRouteLayout.fit(metadataBottom, panelH, font.lineHeight);
        rewardSlot = Math.clamp(Math.min((rewardW - 20) / 6, panelY + panelH - route.rewardY() - 26), 18, 40);
        route = route.fillTo(panelY + panelH - 16, rewardSlot);
        routeHeadingTop = route.headingTop(); routeTop = route.routeTop(); routeSize = route.iconSize();
        inlineRouteCaptions = route.inlineCaptions();
        bossTop = route.bossTop(); bossHeight = route.bossHeight();
        rewardHeadingTop = route.rewardHeadingTop(); rewardY = route.rewardY();
        rewardDividerTop = rewardHeadingTop - 5;
        rewardPitch = rewardSlot + 4;
        rewardBar = addRenderableWidget(new NeoScrollbar(rewardX, rewardY + rewardSlot + 4, rewardW, 6,
                Component.literal("รายการรางวัล")).orientation(NeoScrollbar.Orientation.HORIZONTAL).scrollStep(rewardPitch));
        refreshRanges();
        listBar.value(oldScroll); rewardBar.value(oldRewards);
    }

    private void refreshRanges() {
        if (listBar == null) return;
        listBar.range(listH, entries.size() * rowH);
        var entry = selected();
        int count = entry == null ? 0 : entry.rewardItems().size();
        rewardBar.range(rewardW, count == 0 ? 0 : count * rewardPitch - 4);
        rewardBar.visible = rewardBar.isScrollable();
        boolean validMode = entry != null && DungeonCreateRoomLayout.modeIndex(entry.modeMask()) >= 0;
        selectButton.active = validMode && entry.id().matches("[a-z0-9_]+");
        selectButton.setTooltip(Tooltip.create(Component.literal(entry == null ? "เลือกดันเจี้ยนก่อน"
                : !validMode ? "ดันเจี้ยนต้องกำหนดโหมดเดียวก่อนสร้างห้อง"
                : "สร้างห้องและไปหน้ารอเริ่มดันเจี้ยน")));
    }

    private void createSelectedRoom() {
        var entry = selected();
        if (entry == null || minecraft.getConnection() == null || !selectButton.active) return;
        int mode = DungeonCreateRoomLayout.modeIndex(entry.modeMask());
        if (mode < 0 || !entry.id().matches("[a-z0-9_]+")) return;
        String type = switch (mode) { case 0 -> "solo"; case 1 -> "trio"; default -> "raid"; };
        selectButton.active = false;
        minecraft.getConnection().sendCommand("neow dungeon room create " + entry.id() + " " + type);
        // The server validates level/party requirements and opens WAITING only on success.
        // Closing exposes the existing server error messages when creation is rejected.
        minecraft.setScreen(null);
    }

    void receiveData(DungeonBrowserDataPayload payload) {
        entries = List.copyOf(payload.entries()); dataReceived = true;
        if (selected() == null) selectedId = entries.isEmpty() ? "" : entries.getFirst().id();
        refreshRanges();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        renderBackground(g, mouseX, mouseY, tick);
        hoveredText = null; hoveredItem = null;
        g.setColor(1F, 1F, 1F, 1F);
        DungeonBrowserV5Skin.frame(g, panelX, panelY, panelW, panelH);
        g.drawCenteredString(font, title, panelX + panelW / 2,
                panelY + Math.round(panelW * 60F / 1468F) - font.lineHeight / 2, TEXT);
        int countWidth = font.width(entries.size() + " แห่ง");
        int headerRight = listBar.getX() + listBar.getWidth();
        text(g, "รายชื่อดันเจี้ยน", listX + 2, y(63), headerRight - countWidth - listX - 10, TEXT);
        right(g, entries.size() + " แห่ง", headerRight, y(63), countWidth, MUTED);
        g.fill(x(238), y(65), x(238) + 1, y(374), 0xFF343C40);
        renderList(g, mouseX, mouseY);
        renderDetails(g, mouseX, mouseY);
        String status = !dataReceived ? "กำลังโหลดข้อมูล..." : entries.isEmpty() ? "ไม่มีดันเจี้ยน" : "พร้อมเล่น";
        int statusY = panelY + panelH - 34;
        g.fill(listX + 2, statusY + 2, listX + 6, statusY + 6, selected() == null ? 0xFF788185 : 0xFF2DD568);
        text(g, status, listX + 12, statusY, listW - 12, MUTED);
        for (var renderable : renderables) renderable.render(g, mouseX, mouseY, tick);
        // Tooltips render last, outside both scissor regions and without any pose scaling.
        if (hoveredItem != null) g.renderTooltip(font, hoveredItem, mouseX, mouseY);
        else if (hoveredText != null) g.renderTooltip(font, hoveredText, mouseX, mouseY);
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        int scroll = (int) listBar.getValue();
        g.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = scroll / rowH; i < entries.size(); i++) {
            int top = listY + i * rowH - scroll;
            if (top >= listY + listH) break;
            var entry = entries.get(i);
            boolean hover = inside(mx, my, listX, listY, listW, listH) && my >= top && my < top + rowH;
            if (entry.id().equals(selectedId))
                DungeonBrowserV5Skin.socket(g, "selected", listX, top, listW, rowH-3, 1206, 246, 32, 3);
            else {
                if (hover) g.fill(listX, top, listX+listW, top+rowH-3, 0x55357070);
                g.fill(listX+2, top+rowH-2, listX+listW-2, top+rowH-1, 0xFF525B60);
            }
            int thumb = Math.max(16, Math.min(32, Math.min(rowH - 11, listW / 3)));
            DungeonPreviewImages.draw(g, entry.imageId(), listX + 5, top + 5, thumb, thumb);
            g.renderOutline(listX + 5, top + 5, thumb, thumb, 0xFF69777B);
            int tx = listX + thumb + 10, tw = listX + listW - tx - 4;
            text(g, entry.displayName(), tx, top + 5, tw, TEXT);
            int levelY = top + font.lineHeight + 8;
            int modeY = top + font.lineHeight * 2 + 11;
            icon(g, 0, tx, levelY, 10);
            text(g, "Lv. " + entry.minimumLevel() + "+", tx + 13, levelY + 1, tw - 13, MUTED);
            member(g, tx, modeY, 10);
            text(g, modeText(entry.modeMask()), tx + 13, modeY + 1, tw - 13, TEXT);
            if (hover && font.width(entry.displayName()) > tw) hoveredText = Component.literal(entry.displayName());
        }
        if (entries.isEmpty()) text(g, dataReceived ? "ไม่มีดันเจี้ยน" : "กำลังโหลด...", listX + 5, listY + 15, listW - 10, MUTED);
        g.disableScissor();
    }

    private void renderDetails(GuiGraphics g, int mx, int my) {
        var entry = selected(); if (entry == null) return;
        int headingX = x(258) + 22;
        DungeonBrowserV5Skin.entrance(g, false, x(258), y(76), 17);
        text(g, entry.displayName(), headingX, y(76), x(473) - headingX, GOLD);
        text(g, entry.id(), headingX, Math.max(y(98), y(76) + font.lineHeight + 3), x(473) - headingX, MUTED);
        icon(g, 0, x(258), y(124) - 1, 12);
        detail(g, panelW < 500 ? "เลเวล" : "เลเวลแนะนำ", "Lv. " + entry.minimumLevel() + "+", y(124));
        member(g, x(258), y(146), 12);
        detail(g, panelW < 500 ? "ผู้เล่น" : "จำนวนผู้เล่น", modeText(entry.modeMask()), y(146));
        g.blit(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/dungeon/hud/clock.png"),
                x(258), y(168)-1, 12, 12, 0, 0, 32, 32, 32, 32);
        detail(g, "เวลา", entry.timeLimitSeconds() <= 0 ? "ไม่จำกัด"
                : "%d:%02d".formatted(entry.timeLimitSeconds() / 60, entry.timeLimitSeconds() % 60), y(168));
        DungeonPreviewImages.draw(g, entry.imageId(), x(484), y(76), x(700) - x(484), y(187) - y(76));
        g.renderOutline(x(484) - 1, y(76) - 1, x(700) - x(484) + 2, y(187) - y(76) + 2, 0xFF657074);
        sectionDivider(g, metadataBottom + 3);
        if (routeHeadingTop >= 0) {
            icon(g, 1, x(258), routeHeadingTop - 2, 14);
            text(g, "เส้นทางการเล่น", headingX, routeHeadingTop, x(700) - headingX, GOLD);
        }
        int start = x(258), routeWidth = x(700) - start;
        int first = start + routeWidth / 6, second = start + routeWidth / 2, last = start + routeWidth * 5 / 6;
        routeNode(g, first, routeTop, routeSize, -1, "เริ่มต้น");
        if (entry.waveCount() > 0) {
            routeNode(g, second, routeTop, routeSize, 1, entry.waveCount() == 1 ? "Wave 1" : entry.waveCount() + " Waves");
            text(g, ">", (first + second) / 2, routeTop + routeSize / 2 - 4, 10, MUTED);
            text(g, ">", (second + last) / 2, routeTop + routeSize / 2 - 4, 10, MUTED);
        } else text(g, ">", second, routeTop + routeSize / 2 - 4, 10, MUTED);
        routeNode(g, last, routeTop, routeSize, 2, "บอส");
        sectionDivider(g, bossTop - 5);
        DungeonBrowserV5Skin.bossFrame(g, start, bossTop, routeWidth, bossHeight);
        int bossTextY = bossTop + (bossHeight - font.lineHeight) / 2;
        icon(g, 2, start + 6, bossTop + (bossHeight - 12) / 2, 12);
        String boss = "บอส: " + (entry.bossName().isBlank() ? "ไม่มี" : entry.bossName());
        text(g, boss, start + 24, bossTextY, routeWidth - 32, TEXT);
        if (inside(mx, my, start, bossTop, routeWidth, bossHeight)) hoveredText = Component.literal(boss);
        sectionDivider(g, rewardDividerTop);
        NeoSystemIcons.render(g, NeoSystemIcons.Icon.MARKET, x(258), rewardHeadingTop - 2, 14, true);
        text(g, "รางวัล", headingX, rewardHeadingTop, x(533) - headingX, GOLD);
        renderRewards(g, entry, mx, my);
        if (inside(mx, my, headingX, y(76), x(473) - headingX, font.lineHeight)) hoveredText = Component.literal(entry.displayName());
    }

    private void sectionDivider(GuiGraphics g, int top) {
        blit(g, "divider", x(258), top, x(700) - x(258), 2, 884, 20);
    }

    private void routeNode(GuiGraphics g, int center, int top, int size, int kind, String label) {
        int cellWidth = (x(700) - x(258)) / 3 - 10;
        int labelWidth = inlineRouteCaptions ? Math.max(1, cellWidth - size - 3) : Math.max(1, cellWidth);
        String fitted = NeoGuiRender.trim(font, label, labelWidth);
        int left = inlineRouteCaptions ? center - (size + 3 + font.width(fitted)) / 2 : center - size / 2;
        if (kind < 0) DungeonBrowserV5Skin.entrance(g, true, left, top, size);
        else icon(g, kind, left, top, size);
        g.drawString(font, fitted, inlineRouteCaptions ? left + size + 3 : center - font.width(fitted) / 2,
                inlineRouteCaptions ? top + (size-font.lineHeight)/2 : top + size + 3, TEXT, false);
    }

    private static void icon(GuiGraphics g, int index, int left, int top, int size) {
        DungeonGuiIcons.render(g, index, left, top, size);
    }

    private static void member(GuiGraphics g, int x, int y, int size) {
        g.blit(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/common/icons/member.png"),
                x, y, size, size, 0, 0, 32, 32, 32, 32);
    }

    private void renderRewards(GuiGraphics g, DungeonBrowserDataPayload.Entry entry, int mx, int my) {
        int offset = (int) rewardBar.getValue();
        g.enableScissor(rewardX, rewardY, rewardX + rewardW, rewardY + rewardSlot);
        for (int i = offset / rewardPitch; i < entry.rewardItems().size(); i++) {
            int left = rewardX + i * rewardPitch - offset;
            if (left >= rewardX + rewardW) break;
            // Read-only previews: no menu slots, inventory mutations or claim packets.
            g.blitSprite(ResourceLocation.withDefaultNamespace("container/slot"), left, rewardY, rewardSlot, rewardSlot);
            ItemStack stack = entry.rewardItems().get(i);
            g.pose().pushPose();
            int padding = rewardSlot == 18 ? 1 : 2;
            g.pose().translate(left + padding, rewardY + padding, 0);
            float itemScale = (rewardSlot - padding * 2) / 16F;
            g.pose().scale(itemScale, itemScale, 1);
            g.renderItem(stack, 0, 0);
            g.renderItemDecorations(font, stack, 0, 0);
            g.pose().popPose();
            if (inside(mx, my, rewardX, rewardY, rewardW, rewardSlot) && mx >= left && mx < left + rewardSlot) hoveredItem = stack;
        }
        g.disableScissor();
        if (entry.rewardItems().isEmpty()) text(g, entry.previewIncomplete() ? "ไม่มีตัวอย่าง" : "ไม่มีไอเทม", rewardX, rewardY + 4, rewardW, MUTED);
        if (entry.previewIncomplete()) {
            text(g, "*", rewardX + rewardW - 5, rewardHeadingTop, 8, GOLD);
            if (inside(mx, my, rewardX + rewardW - 8, rewardHeadingTop, 10, font.lineHeight)) hoveredText = Component.literal(
                    "ตัวอย่างชนิดไอเทมบางส่วน จำนวนและคุณสมบัติขึ้นกับ Loot Table");
        }
    }

    private void detail(GuiGraphics g, String label, String value, int top) {
        text(g, label, x(258) + 18, top, x(374) - x(258) - 18, MUTED);
        right(g, value, x(464), top, x(464) - x(374), TEXT);
    }
    private void text(GuiGraphics g, String value, int left, int top, int max, int color) {
        if (max > 0) g.drawString(font, NeoGuiRender.trim(font, value, max), left, top, color, false);
    }
    private void right(GuiGraphics g, String value, int left, int top, int max, int color) {
        if (max <= 0) return;
        String fitted = NeoGuiRender.trim(font, value, max);
        g.drawString(font, fitted, left - font.width(fitted), top, color, false);
    }
    private int x(int value) {
        if (value >= 258) {
            int left = x(238) + 8, right = panelX + panelW - 12;
            return left + Math.round((value - 258) * (right - left) / 442F);
        }
        return panelX + 24 + Math.round((value - 24) * (panelW - 48) / 686F);
    }
    private int y(int value) { return panelY + Math.round(value * panelH / 417F); }
    private static ResourceLocation asset(String name) { return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/dungeon/browser/" + name + ".png"); }
    private static void blit(GuiGraphics g, String name, int x, int y, int w, int h, int sw, int sh) { g.blit(asset(name), x, y, w, h, 0, 0, sw, sh, sw, sh); }
    private DungeonBrowserDataPayload.Entry selected() { return entries.stream().filter(e -> e.id().equals(selectedId)).findFirst().orElse(null); }
    private static boolean inside(double x, double y, int left, int top, int w, int h) { return x >= left && x < left + w && y >= top && y < top + h; }

    private void select(int index) {
        String next = entries.get(index).id();
        if (!next.equals(selectedId)) { selectedId = next; rewardBar.value(0); refreshRanges(); }
    }
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(mx, my, listX, listY, listW, listH)) {
            int index = ((int) my - listY + (int) listBar.getValue()) / rowH;
            if (index < entries.size()) select(index);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (inside(mx, my, listX, listY, listW, listH)) { listBar.scroll(dy); return true; }
        if (inside(mx, my, rewardX, rewardY, rewardW, rewardSlot + 12)) { rewardBar.scroll(dy != 0 ? dy : dx); return true; }
        return super.mouseScrolled(mx, my, dx, dy);
    }
    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (getFocused() instanceof NeoScrollbar) return super.keyPressed(key, scanCode, modifiers);
        if (!entries.isEmpty() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            int index = Math.clamp(entries.indexOf(selected()) + (key == GLFW.GLFW_KEY_UP ? -1 : 1), 0, entries.size() - 1);
            select(index);
            if (index * rowH < listBar.getValue()) listBar.value(index * rowH);
            else if ((index + 1) * rowH > listBar.getValue() + listH) listBar.value((index + 1) * rowH - listH);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    private static String modeText(int mask) {
        StringBuilder result = new StringBuilder(); int[] bits = {1, 2, 4}, counts = {1, 3, 6};
        for (int i = 0; i < bits.length; i++) if ((mask & bits[i]) != 0) { if (!result.isEmpty()) result.append("/"); result.append(counts[i]); }
        return result.isEmpty() ? "ไม่มี" : result + " คน";
    }
    @Override public boolean isPauseScreen() { return false; }
}
