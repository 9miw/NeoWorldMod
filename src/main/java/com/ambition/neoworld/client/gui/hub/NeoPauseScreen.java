package com.ambition.neoworld.client.gui.hub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import com.ambition.neoworld.client.gui.navigation.NeoScreenDestination;
import com.ambition.neoworld.client.gui.navigation.NeoScreenRegistry;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.common.NeoSystemIcons;
import com.ambition.neoworld.client.gui.widget.NeoScrollbar;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.ModListScreen;

/** Floating ESC menu. Layout is expressed in the approved 1280x720 reference canvas. */
public final class NeoPauseScreen extends Screen {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/menu/background.png");
    private int gap;
    private float uiScale;
    private final List<NeoButton> cards = new ArrayList<>();
    private final List<NeoButton> dockButtons = new ArrayList<>();
    private static final List<String> SYSTEM_ORDER = List.of("profile", "party", "titles", "fashion", "activity", "market", "ranking");
    private final List<NeoScreenDestination> destinations = NeoScreenRegistry.destinations().stream()
            .filter(destination -> SYSTEM_ORDER.contains(destination.id()))
            .sorted(Comparator.comparingInt(destination -> SYSTEM_ORDER.indexOf(destination.id())))
            .toList();
    private NeoScrollbar onlineScroll;
    private EditBox search;
    private NeoButton onlineFrame;
    private List<PlayerInfo> online = List.of();
    private int onlineCount, onlineX, onlineWidth, onlineY, onlineBottom, rowHeight, shownRows;

    public NeoPauseScreen() { super(Component.literal("เมนู NeoWorld")); }

    @Override
    protected void init() {
        cards.clear();
        dockButtons.clear();
        uiScale = Math.min(width / 1280F, height / 720F);
        gap = Math.max(3, Math.round(14 * uiScale));
        int dockGap = Math.max(3, Math.round(10 * uiScale));
        int dockSize = Math.min(Math.max(16, Math.round(46 * uiScale)), Math.max(1, (width - 8 - 6 * dockGap) / 7));
        int dockY = height - dockSize - Math.max(8, Math.round(40 * uiScale));
        int edge = Math.max(6, Math.round(24 * uiScale));
        int middleGap = Math.max(6, 2 * gap);
        int availableWidth = width - 2 * edge - middleGap;
        int targetLeft = Math.max(96, Math.round(392 * uiScale));
        int targetRight = Math.max(104, Math.round(400 * uiScale));
        boolean compact = targetLeft + targetRight > availableWidth;
        int panelTop = Math.max(12, Math.round(32 * uiScale));
        int panelHeight = Math.min(dockY - panelTop - gap, Math.max(100, Math.round(350 * uiScale)));
        int panelY = Math.max(panelTop, (dockY - panelHeight) / 2);
        onlineX = edge;
        onlineWidth = compact ? availableWidth * 2 / 5 : targetLeft;
        onlineY = panelY;
        onlineBottom = panelY + panelHeight;
        int rightEdge = width - edge;
        int rightWidth = compact ? availableWidth - onlineWidth : targetRight;
        int rightX = rightEdge - rightWidth;
        int cardWidth = (rightWidth - gap) / 2;
        int smallHeight = (panelHeight - 17 - 3 * gap) / 5;
        for (int i = 0; i < destinations.size(); i++) {
            NeoScreenDestination destination = destinations.get(i);
            int slot = SYSTEM_ORDER.indexOf(destination.id());
            int row = slot / 2;
            int cardY = panelY + 17 + row * (smallHeight + gap) + (row == 3 ? smallHeight : 0);
            int cardHeight = row == 2 ? 2 * smallHeight : smallHeight;
            NeoButton card = addRenderableWidget(new NeoButton(Button.builder(Component.empty(),
                    b -> destination.opener().run()).bounds(rightX + slot % 2 * (cardWidth + gap),
                    cardY, cardWidth, cardHeight)
                    .createNarration(supplier -> destination.title().copy())
                    .tooltip(Tooltip.create(destination.description())))
                    .skin(NeoButton.Skin.FLOATING).skinCornerSize(Math.max(2, Math.round(8 * uiScale))));
            card.active = destination.enabled();
            cards.add(card);
        }
        onlineFrame = new NeoButton(Button.builder(Component.empty(), b -> {})
                .bounds(onlineX, onlineY, onlineWidth, panelHeight)).skin(NeoButton.Skin.FLOATING)
                .skinCornerSize(Math.max(2, Math.round(8 * uiScale)));
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, onlineX + 7, onlineY + 19,
                onlineWidth - 14, 16, Component.literal("ค้นหาผู้เล่น")));
        search.setHint(Component.literal("ค้นหาผู้เล่น"));
        search.setMaxLength(64);
        search.setValue(query);
        rowHeight = Math.max(16, Math.round(30 * uiScale));
        shownRows = Math.max(1, (onlineBottom - listTop() - 5) / rowHeight);
        onlineScroll = addRenderableWidget(new NeoScrollbar(onlineX + onlineWidth - 9, listTop(), 4,
                shownRows * rowHeight, Component.literal("เลื่อนรายชื่อผู้เล่น")).scrollStep(1));
        online = List.of();
        search.setResponder(value -> {
            onlineScroll.value(0);
            refreshOnline();
        });
        refreshOnline();

        int dockX = (width - (7 * dockSize + 6 * dockGap)) / 2;
        dock(dockX, dockY, dockSize, Component.empty(), "กลับเข้าเกม", NeoButton.Style.PRIMARY,
                () -> onClose());
        dock(dockX + (dockSize + dockGap), dockY, dockSize, Component.empty(), "ตั้งค่า",
                NeoButton.Style.STANDARD, () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
        dock(dockX + 2 * (dockSize + dockGap), dockY, dockSize, Component.empty(), "ม็อด",
                NeoButton.Style.STANDARD, () -> minecraft.setScreen(new ModListScreen(this)));
        dock(dockX + 3 * (dockSize + dockGap), dockY, dockSize, Component.empty(), "ความก้าวหน้า",
                NeoButton.Style.STANDARD, () -> minecraft.setScreen(new AdvancementsScreen(
                        minecraft.player.connection.getAdvancements(), this)));
        dock(dockX + 4 * (dockSize + dockGap), dockY, dockSize, Component.empty(), "สถิติ",
                NeoButton.Style.STANDARD, () -> minecraft.setScreen(new StatsScreen(this, minecraft.player.getStats())));
        boolean canOpenLan = minecraft.hasSingleplayerServer()
                && !minecraft.getSingleplayerServer().isPublished();
        NeoButton lan = dock(dockX + 5 * (dockSize + dockGap), dockY, dockSize,
                Component.empty(), "เปิดให้เล่นผ่าน LAN", NeoButton.Style.STANDARD,
                () -> {
                    if (minecraft.hasSingleplayerServer() && !minecraft.getSingleplayerServer().isPublished())
                        minecraft.setScreen(new ShareToLanScreen(this));
                });
        lan.active = canOpenLan;
        if (!canOpenLan) lan.setTooltip(Tooltip.create(Component.literal(minecraft.hasSingleplayerServer()
                ? "โลกนี้เปิดให้เล่นผ่าน LAN แล้ว" : "เปิดผ่าน LAN ได้เฉพาะโลกเล่นคนเดียว")));
        dock(dockX + 6 * (dockSize + dockGap), dockY, dockSize, Component.empty(),
                minecraft.isLocalServer() ? "บันทึกและกลับหน้าหลัก" : "ตัดการเชื่อมต่อ",
                NeoButton.Style.DESTRUCTIVE, () -> minecraft.getReportingContext()
                        .draftReportHandled(minecraft, this, this::disconnect, true));
    }

    private NeoButton dock(int x, int y, int size, Component icon, String label, NeoButton.Style style, Runnable action) {
        NeoButton button = addRenderableWidget(new NeoButton(Button.builder(icon, b -> action.run()).bounds(x, y, size, size)
                .createNarration(supplier -> Component.literal(label))
                .tooltip(Tooltip.create(Component.literal(label)))).style(style).textScrolling(false)
                .skin(NeoButton.Skin.FLOATING).skinCornerSize(Math.max(2, Math.round(8 * uiScale))));
        dockButtons.add(button);
        return button;
    }

    private int listTop() { return onlineY + 40; }

    @Override
    public void tick() {
        super.tick();
        refreshOnline();
    }

    private void refreshOnline() {
        var connection = minecraft.getConnection();
        var listed = connection == null ? List.<PlayerInfo>of() : connection.getListedOnlinePlayers();
        onlineCount = listed.size();
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        List<PlayerInfo> next = listed.stream().filter(player -> player.getProfile().getName().toLowerCase(Locale.ROOT).contains(query)
                        || displayName(player).getString().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(player -> player.getProfile().getName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (next.size() != online.size()) onlineScroll.range(shownRows, next.size());
        online = next;
        onlineScroll.visible = online.size() > shownRows;
    }

    private static Component displayName(PlayerInfo player) {
        return player.getTabListDisplayName() != null ? player.getTabListDisplayName()
                : net.minecraft.world.scores.PlayerTeam.formatNameForTeam(player.getTeam(), Component.literal(player.getProfile().getName()));
    }

    private void renderOnline(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        onlineFrame.render(graphics, -1, -1, partialTick);
        graphics.drawString(font, "ออนไลน์ " + onlineCount + " คน", onlineX + 7, onlineY + 7, NeoGuiStyle.COLOR_GOLD, true);
        int first = (int) Math.round(onlineScroll.getValue());
        int left = onlineX + 7;
        int right = onlineScroll.getX() - 2;
        graphics.enableScissor(left, listTop(), right, onlineBottom - 4);
        for (int row = 0; row < shownRows && first + row < online.size(); row++) {
            PlayerInfo player = online.get(first + row);
            int y = listTop() + row * rowHeight;
            int face = Math.min(16, rowHeight - 4);
            if ((row & 1) == 0) graphics.fill(left, y, right, y + rowHeight, 0x44000000);
            PlayerFaceRenderer.draw(graphics, player.getSkin().texture(), left + 2, y + (rowHeight - face) / 2, face);
            int textX = left + face + 5;
            Component name = displayName(player);
            int textY = y + (rowHeight - font.lineHeight) / 2;
            int nameWidth = Math.max(0, right - 16 - textX);
            if (font.width(name) <= nameWidth) graphics.drawString(font, name, textX, textY, 0xFFFFFFFF, true);
            else graphics.drawString(font, font.plainSubstrByWidth(name.getString(), nameWidth), textX, textY, 0xFFFFFFFF, true);
            int latency = player.getLatency();
            int bars = latency < 0 ? 0 : latency < 150 ? 5 : latency < 300 ? 4 : latency < 600 ? 3 : latency < 1000 ? 2 : 1;
            int base = y + rowHeight / 2 + 4;
            for (int i = 0; i < 5; i++) {
                int x = right - 12 + 2 * i;
                graphics.fill(x, base - i - 2, x + 1, base, i < bars ? 0xFF65DBA1 : 0xFF495960);
            }
        }
        if (online.isEmpty()) graphics.drawString(font, "ไม่พบผู้เล่น", left + 2, listTop() + 3, 0xFFCCCCCC, false);
        graphics.disableScissor();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Cover the entire window while preserving the background's aspect ratio.
        float cover = Math.max(width / 1672F, height / 941F);
        int bgWidth = (int) Math.ceil(1672 * cover);
        int bgHeight = (int) Math.ceil(941 * cover);
        graphics.enableScissor(0, 0, width, height);
        graphics.blit(BACKGROUND, (width - bgWidth) / 2, (height - bgHeight) / 2,
                bgWidth, bgHeight, 0, 0, 1672, 941, 1672, 941);
        graphics.disableScissor();
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2F, Math.max(5, onlineY - 44 * uiScale), 0);
        float headingScale = 30 * uiScale / font.lineHeight;
        graphics.pose().scale(headingScale, headingScale, 1);
        Component heading = Component.literal("NEO").append(Component.literal("WORLD")
                .withStyle(style -> style.withColor(0x2FC7DE)));
        graphics.drawString(font, heading, -font.width(heading) / 2, 0, 0xFFF3F5F7, true);
        graphics.pose().popPose();
        renderOnline(graphics, mouseX, mouseY, partialTick);
        if (!cards.isEmpty()) graphics.drawString(font, "ระบบ NeoWorld", cards.getFirst().getX(), onlineY + 4, NeoGuiStyle.COLOR_GOLD, true);
        for (var renderable : renderables) renderable.render(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < cards.size(); i++) {
            NeoButton card = cards.get(i);
            if (!card.visible) continue;
            String id = destinations.get(i).id();
            boolean large = id.equals("activity") || id.equals("market");
            int padding = Math.max(3, Math.round(9 * uiScale));
            int iconSize = Math.max(1, Math.min(card.getHeight() - 4, Math.round((large ? 38 : 24) * uiScale)));
            int iconX = card.getX() + padding;
            int iconY = card.getY() + (card.getHeight() - iconSize) / 2;
            var systemIcon = NeoSystemIcons.forSystem(id);
            if (systemIcon != null) NeoSystemIcons.render(graphics, systemIcon,
                    iconX, iconY, iconSize, card.active);
            else if (id.equals("ranking")) {
                graphics.blit(ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/pvp/trophy.png"),
                        iconX, iconY, iconSize, iconSize,
                        0, 0, 1254, 1254, 1254, 1254);
            } else {
                // Existing additional systems retain their registered item preview.
                graphics.pose().pushPose();
                graphics.pose().translate(iconX, iconY, 0);
                graphics.pose().scale(iconSize / 16F, iconSize / 16F, 1);
                graphics.renderItem(destinations.get(i).icon().get(), 0, 0);
                graphics.pose().popPose();
            }
            Component label = switch (id) {
                case "market" -> Component.literal("ตลาดกลาง");
                case "profile" -> Component.literal("โปรไฟล์");
                default -> destinations.get(i).title();
            };
            int textX = iconX + iconSize + padding;
            float scale = Math.min((large ? 20 : 17) * uiScale / font.lineHeight,
                    Math.max(1, card.getX() + card.getWidth() - padding - textX) / (float) Math.max(1, font.width(label)));
            graphics.pose().pushPose();
            graphics.pose().translate(textX, card.getY() + (card.getHeight() - font.lineHeight * scale) / 2F, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, label, 0, 0, NeoGuiStyle.COLOR_GOLD, false);
            graphics.pose().popPose();
        }
        NeoSystemIcons.Icon[] dockIcons = {
                NeoSystemIcons.Icon.RESUME, NeoSystemIcons.Icon.SETTINGS, NeoSystemIcons.Icon.MODS,
                NeoSystemIcons.Icon.ADVANCEMENTS, NeoSystemIcons.Icon.STATISTICS,
                NeoSystemIcons.Icon.LAN, NeoSystemIcons.Icon.EXIT
        };
        for (int i = 0; i < dockButtons.size(); i++) {
            NeoButton button = dockButtons.get(i);
            int padding = Math.max(3, Math.round(Math.min(button.getWidth(), button.getHeight()) * 0.22F));
            int size = Math.max(1, Math.min(button.getWidth(), button.getHeight()) - 2 * padding);
            // Bitmap glyph metrics are fitted by NeoSystemIcons; center using the actual button bounds.
            graphics.enableScissor(button.getX() + padding, button.getY() + padding,
                    button.getX() + button.getWidth() - padding, button.getY() + button.getHeight() - padding);
            NeoSystemIcons.render(graphics, dockIcons[i], button.getX() + (button.getWidth() - size) / 2,
                    button.getY() + (button.getHeight() - size) / 2, size, button.active);
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= onlineX && mouseX < onlineX + onlineWidth
                && mouseY >= listTop() && mouseY < onlineBottom && onlineScroll.scroll(scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
        minecraft.mouseHandler.grabMouse();
    }

    private void disconnect() {
        boolean local = minecraft.isLocalServer();
        var server = minecraft.getCurrentServer();
        minecraft.level.disconnect();
        if (local) minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        else minecraft.disconnect();
        TitleScreen titleScreen = new TitleScreen();
        minecraft.setScreen(local ? titleScreen : server != null && server.isRealm()
                ? new RealmsMainScreen(titleScreen) : new JoinMultiplayerScreen(titleScreen));
    }

    @Override public boolean isPauseScreen() { return true; }

}
