package com.ambition.neoworld.client.gui.market;

import com.ambition.neoworld.client.gui.widget.EconomyBalanceWidget;
import com.ambition.neoworld.client.gui.widget.NeoButton;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.market.MarketCategory;
import com.ambition.neoworld.market.MarketSort;
import com.ambition.neoworld.network.MarketActionResultPayload;
import com.ambition.neoworld.network.MarketBuyPayload;
import com.ambition.neoworld.network.MarketPagePayload;
import com.ambition.neoworld.network.MarketQueryPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MarketScreen extends Screen {
    private static final int COLUMNS = 9;
    private static final int ROWS = 4;
    private static final int PAGE_SIZE = COLUMNS * ROWS;
    private static final int SEARCH_DELAY_TICKS = 8;

    private static final ResourceLocation FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_frame.png");
    private static final ResourceLocation WIDGET_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_widgets.png");
    private static final ResourceLocation SLOT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_slot_states.png");
    private static final ResourceLocation SEARCH_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_search_states.png");
    private static final ResourceLocation DETAIL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_detail_scroll.png");
    private static final int FRAME_TEXTURE_WIDTH = 1556;
    private static final int FRAME_TEXTURE_HEIGHT = 1011;
    private static final int WIDGET_TEXTURE_WIDTH = 1536;
    private static final int WIDGET_TEXTURE_HEIGHT = 1024;
    private static final int SLOT_TEXTURE_WIDTH = 1944;
    private static final int SLOT_TEXTURE_HEIGHT = 809;
    private static final int SEARCH_TEXTURE_WIDTH = 1945;
    private static final int SEARCH_TEXTURE_HEIGHT = 808;
    private static final int DETAIL_TEXTURE_WIDTH = 1505;
    private static final int DETAIL_TEXTURE_HEIGHT = 1045;

    private static final int COLOR_FRAME_DARK = 0xFF20252A;
    private static final int COLOR_FRAME = 0xFF4B535A;
    private static final int COLOR_FRAME_LIGHT = 0xFF7B858C;
    private static final int COLOR_PANEL = 0xF5161B1E;
    private static final int COLOR_PANEL_ALT = 0xFF101518;
    private static final int COLOR_SLOT = 0xFF252B2F;
    private static final int COLOR_SLOT_HOVER = 0xFF374348;
    private static final int COLOR_ACCENT = 0xFF2FB5A7;
    private static final int COLOR_ACCENT_DARK = 0xFF176B65;
    private static final int COLOR_GOLD = 0xFFF2C14E;
    private static final int COLOR_TEXT_MUTED = 0xFFABB4BA;

    private final Map<MarketCategory, ItemStack> categoryIcons = new EnumMap<>(MarketCategory.class);
    private final Map<MarketCategory, NeoButton> categoryButtons = new EnumMap<>(MarketCategory.class);
    private List<MarketPagePayload.Entry> entries = List.of();
    private MarketCategory category = MarketCategory.ALL;
    private MarketSort sort = MarketSort.PRICE_ASC;
    private EditBox searchBox;
    private NeoButton closeButton;
    private NeoButton sortButton;
    private NeoButton buyButton;
    private NeoButton previousButton;
    private NeoButton nextButton;
    private NeoButton confirmButton;
    private NeoButton cancelButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int slotWidth;
    private int slotHeight;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int detailX;
    private int detailY;
    private int detailWidth;
    private int categoryX;
    private int categoryY;
    private int categoryWidth;
    private int searchX;
    private int searchY;
    private int searchWidth;
    private int sortX;
    private int pagerY;
    private int buyY;
    private int selectedIndex = -1;
    private int page = 1;
    private int totalPages = 1;
    private long totalListings;
    private long serverGameTime;
    private int requestId;
    private int pendingSearchTicks = -1;
    private boolean loading;
    private boolean confirming;
    private String status = "";
    private int statusColor = 0xFFE3E7EA;
    private int statusTicks;

    public MarketScreen() {
        super(Component.literal("ตลาดกลาง"));
        categoryIcons.put(MarketCategory.ALL, new ItemStack(Items.CHEST));
        categoryIcons.put(MarketCategory.BLOCKS, new ItemStack(Items.BRICKS));
        categoryIcons.put(MarketCategory.COMBAT, new ItemStack(Items.DIAMOND_SWORD));
        categoryIcons.put(MarketCategory.TOOLS, new ItemStack(Items.DIAMOND_PICKAXE));
        categoryIcons.put(MarketCategory.FOOD, new ItemStack(Items.COOKED_BEEF));
        categoryIcons.put(MarketCategory.MATERIALS, new ItemStack(Items.IRON_INGOT));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(404, width - 8);
        panelHeight = Math.min(232, height - 8);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        categoryX = panelX + scaleX(60);
        gridX = panelX + scaleX(118);
        categoryY = panelY + scaleY(156);
        searchY = panelY + scaleY(258);
        gridY = panelY + scaleY(350);
        slotWidth = Math.max(22, scaleX(96));
        slotHeight = slotWidth;
        gridWidth = slotWidth * COLUMNS;
        detailX = panelX + scaleX(1079);
        detailWidth = scaleX(417);
        categoryWidth = scaleX(116);

        searchX = panelX + scaleX(60);
        searchWidth = scaleX(900);
        sortX = panelX + scaleX(970);
        pagerY = panelY + scaleY(881);
        buyY = pagerY;
        detailY = panelY + scaleY(145);

        searchBox = new EditBox(font, searchX + 30, searchY + 4, Math.max(20, searchWidth - 34), 14,
                Component.literal("ค้นหาสินค้า"));
        searchBox.setMaxLength(64);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFE7ECEF);
        searchBox.setTextColorUneditable(0xFF7F898F);
        searchBox.setHint(Component.literal("ค้นหา").withStyle(ChatFormatting.GRAY));
        searchBox.setResponder(value -> pendingSearchTicks = SEARCH_DELAY_TICKS);
        addRenderableWidget(searchBox);

        categoryButtons.clear();
        int categoryIndex = 0;
        for (MarketCategory value : MarketCategory.values()) {
            int buttonX = categoryX + categoryIndex++ * (categoryWidth + 3);
            NeoButton button = addRenderableWidget(new NeoButton(
                    Button.builder(Component.empty(), ignored -> selectCategory(value))
                            .bounds(buttonX, categoryY, categoryWidth, 21)
                            .tooltip(Tooltip.create(Component.literal(categoryLabel(value))))));
            categoryButtons.put(value, button);
        }

        sortButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.empty(), ignored -> cycleSort())
                        .bounds(sortX, searchY, 24, 18)
                        .tooltip(Tooltip.create(sortLabel()))));
        previousButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal("<"), ignored -> requestPage(page - 1))
                        .bounds(gridX, pagerY, 22, 18)
                        .tooltip(Tooltip.create(Component.literal("หน้าก่อนหน้า")))));
        nextButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal(">"), ignored -> requestPage(page + 1))
                        .bounds(gridX + gridWidth - 22, pagerY, 22, 18)
                        .tooltip(Tooltip.create(Component.literal("หน้าถัดไป")))));
        buyButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal("ซื้อ"), ignored -> beginPurchase())
                        .bounds(detailX + 7, buyY, detailWidth - 14, 22)
                        .tooltip(Tooltip.create(Component.literal("ซื้อสินค้า"))))
                .style(NeoButton.Style.PRIMARY));
        closeButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal("\uE01B"), ignored -> onClose())
                        .bounds(closeButtonX(), closeButtonY(), 18, 18)
                        .tooltip(Tooltip.create(Component.literal("ปิด"))))
                .style(NeoButton.Style.DESTRUCTIVE));

        int confirmationWidth = Math.min(230, width - 24);
        int confirmationX = (width - confirmationWidth) / 2;
        int confirmationY = (height - 92) / 2;
        confirmButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal("ยืนยัน"), ignored -> confirmPurchase())
                        .bounds(confirmationX + 18, confirmationY + 65, 84, 18))
                .style(NeoButton.Style.PRIMARY));
        cancelButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal("ยกเลิก"), ignored -> confirming = false)
                        .bounds(confirmationX + confirmationWidth - 102, confirmationY + 65, 84, 18)));

        int balanceWidth = 190;
        int balanceHeight = 20;
        int balanceX = 3;
        int balanceY = 0;
        addRenderableWidget(new EconomyBalanceWidget(balanceX, balanceY, balanceWidth, balanceHeight,
                EconomyBalanceWidget.Alignment.LEFT));
        requestPage(1);
    }

    @Override
    public void tick() {
        super.tick();
        serverGameTime++;
        if (pendingSearchTicks > 0 && --pendingSearchTicks == 0) requestPage(1);
        if (statusTicks > 0) statusTicks--;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderFrame(graphics);
        renderTitlePlate(graphics);
        renderSearch(graphics);
        renderGrid(graphics, mouseX, mouseY);
        renderDetails(graphics, mouseX, mouseY);
        renderFooter(graphics, mouseX, mouseY);
        updateButtonStates();
        for (var renderable : renderables) {
            if (renderable == confirmButton || renderable == cancelButton) continue;
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        renderButtonIcons(graphics);

        MarketPagePayload.Entry hovered = entryAt(mouseX, mouseY);
        MarketPagePayload.Entry selected = selectedEntry();
        if (confirming) {
            renderConfirmation(graphics);
            confirmButton.render(graphics, mouseX, mouseY, partialTick);
            cancelButton.render(graphics, mouseX, mouseY, partialTick);
        } else if (hovered != null) {
            graphics.renderTooltip(font, hovered.item(), mouseX, mouseY);
        } else if (selected != null
                && inside(mouseX, mouseY, detailX + 7, detailY + 6, detailWidth - 14, 34)) {
            graphics.renderTooltip(font, selected.item(), mouseX, mouseY);
        }
    }

    private void renderFrame(GuiGraphics graphics) {
        graphics.blit(FRAME_TEXTURE, panelX, panelY, panelWidth, panelHeight, 0, 0,
                FRAME_TEXTURE_WIDTH, FRAME_TEXTURE_HEIGHT, FRAME_TEXTURE_WIDTH, FRAME_TEXTURE_HEIGHT);
    }

    private void renderTitlePlate(GuiGraphics graphics) {
        int plateWidth = scaleX(620);
        int x = panelX + (panelWidth - plateWidth) / 2;
        int y = panelY + scaleY(42);
        graphics.pose().pushPose();
        graphics.pose().translate(x + plateWidth / 2.0F, y, 0);
        graphics.pose().scale(1.25F, 1.25F, 1.0F);
        graphics.drawString(font, title, -font.width(title) / 2, 0, 0xFFF4F6F7, false);
        graphics.pose().popPose();
        graphics.drawString(font, "+", x + 10, y + 1, COLOR_GOLD, false);
        graphics.drawString(font, "+", x + plateWidth - 15, y + 1, COLOR_GOLD, false);
    }

    private void renderSearch(GuiGraphics graphics) {
        boolean focused = searchBox != null && searchBox.isFocused();
        graphics.blit(SEARCH_TEXTURE, searchX, searchY, searchWidth, 18,
                152, focused ? 436 : 179, 1642, focused ? 189 : 190,
                SEARCH_TEXTURE_WIDTH, SEARCH_TEXTURE_HEIGHT);
    }

    private void renderButtonIcons(GuiGraphics graphics) {
        for (Map.Entry<MarketCategory, NeoButton> entry : categoryButtons.entrySet()) {
            NeoButton button = entry.getValue();
            graphics.renderItem(categoryIcons.get(entry.getKey()),
                    button.getX() + (button.getWidth() - 16) / 2, button.getY() + 2);
        }
        graphics.renderItem(new ItemStack(Items.HOPPER),
                sortButton.getX() + (sortButton.getWidth() - 16) / 2, sortButton.getY() + 1);
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = 0; index < PAGE_SIZE; index++) {
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            int x = gridX + column * slotWidth;
            int y = gridY + row * slotHeight;
            int cardWidth = slotWidth - 2;
            int cardHeight = slotHeight - 2;
            boolean hovered = inside(mouseX, mouseY, x, y, cardWidth, cardHeight);
            boolean selected = index == selectedIndex;
            if (selected) {
                blitSlot(graphics, x, y, cardWidth, cardHeight, 1331, 168, 474, 474);
            } else if (hovered) {
                blitSlot(graphics, x, y, cardWidth, cardHeight, 735, 168, 474, 474);
            } else {
                blitSlot(graphics, x, y, cardWidth, cardHeight, 139, 168, 474, 474);
            }
            if (index < entries.size()) {
                MarketPagePayload.Entry entry = entries.get(index);
                ItemStack item = entry.item();
                int itemX = x + Math.max(1, (cardWidth - 16) / 2);
                int itemY = y + Math.max(1, (cardHeight - 16) / 2);
                graphics.renderItem(item, itemX, itemY);
                if (item.getCount() > 1) {
                    String count = String.valueOf(item.getCount());
                    graphics.drawString(font, count, x + cardWidth - font.width(count) - 2, y + 2,
                            0xFFFFFFFF, true);
                }
            }
        }
    }

    private void renderDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = detailY;
        int panelHeight = Math.max(80, buyY - top - 4);
        graphics.blit(DETAIL_TEXTURE, detailX, top, detailWidth, panelHeight,
                80, 121, 653, 864, DETAIL_TEXTURE_WIDTH, DETAIL_TEXTURE_HEIGHT);
        MarketPagePayload.Entry selected = selectedEntry();
        if (selected == null) {
            drawCentered(graphics, loading ? "กำลังโหลด..." : "เลือกสินค้า", detailX, detailWidth, top + 18,
                    COLOR_TEXT_MUTED);
            drawCentered(graphics, "เพื่อดูรายละเอียด", detailX, detailWidth, top + 31, 0xFF707A80);
            return;
        }
        ItemStack item = selected.item();
        int itemBoxX = detailX + 7;
        blitSlot(graphics, itemBoxX, top + 6, 32, 32, 139, 168, 474, 474);
        graphics.renderItem(item, itemBoxX + 8, top + 14);
        int nameX = itemBoxX + 38;
        graphics.drawString(font, trim(item.getHoverName().getString(), detailWidth - 51), nameX, top + 8,
                0xFF53D6CF, false);
        graphics.drawString(font, "ผู้ขาย", nameX, top + 20, 0xFF778188, false);
        graphics.drawString(font, trim(selected.sellerName(), detailWidth - 51), nameX, top + 30,
                COLOR_TEXT_MUTED, false);

        int textX = detailX + 8;
        int statsY = top + 49;
        drawSeparator(graphics, detailX + 7, detailX + detailWidth - 7, top + 43);
        graphics.drawString(font, "จำนวนขาย", textX, statsY, COLOR_TEXT_MUTED, false);
        drawRight(graphics, String.valueOf(item.getCount()), detailX + detailWidth - 8, statsY, 0xFFF1F3F4);
        graphics.drawString(font, "ราคาต่อรายการ", textX, statsY + 11, COLOR_TEXT_MUTED, false);
        drawRight(graphics, format(selected.priceSilver()), detailX + detailWidth - 8, statsY + 11, COLOR_GOLD);
        long ticks = Math.max(0L, selected.expiresAtGameTime() - serverGameTime);
        graphics.drawString(font, "เหลือ", textX, statsY + 22, COLOR_TEXT_MUTED, false);
        drawRight(graphics, formatTime(ticks), detailX + detailWidth - 8, statsY + 22, 0xFF68D391);
        drawSeparator(graphics, detailX + 7, detailX + detailWidth - 7, buyY - 15);
        graphics.drawString(font, "ราคารวม", textX, buyY - 11, COLOR_TEXT_MUTED, false);
        drawRight(graphics, format(selected.priceSilver()), detailX + detailWidth - 8, buyY - 11, COLOR_GOLD);
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        String pageText = "หน้า " + page + "/" + totalPages + "  (" + format(totalListings) + " รายการ)";
        drawCentered(graphics, pageText, gridX + 24, gridWidth - 48, pagerY + 6, 0xFFBEC6CB);
        if (statusTicks > 0 && !status.isEmpty()) {
            graphics.drawString(font, trim(status, gridWidth), gridX, gridY - 11,
                    statusColor, false);
        }
    }

    private void renderConfirmation(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0x99000000);
        int boxWidth = Math.min(230, width - 24);
        int boxHeight = 92;
        int x = (width - boxWidth) / 2;
        int y = (height - boxHeight) / 2;
        blitWidget(graphics, x, y, boxWidth, boxHeight, 1144, 678, 363, 276);
        MarketPagePayload.Entry selected = selectedEntry();
        drawCentered(graphics, "ยืนยันการซื้อ", x, boxWidth, y + 12, COLOR_GOLD);
        if (selected != null) {
            drawCentered(graphics, trim(selected.item().getHoverName().getString(), boxWidth - 20), x, boxWidth,
                    y + 31, 0xFFFFFFFF);
            drawCentered(graphics, format(selected.priceSilver()) + " Silver", x, boxWidth, y + 44, 0xFFE3E7EA);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (confirming) return super.mouseClicked(mouseX, mouseY, button);
        if (inside(mouseX, mouseY, gridX, gridY, gridWidth, ROWS * slotHeight)) {
            int column = ((int) mouseX - gridX) / slotWidth;
            int row = ((int) mouseY - gridY) / slotHeight;
            int index = row * COLUMNS + column;
            if (index >= 0 && index < entries.size()) {
                selectedIndex = index;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirming) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                confirming = false;
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void receivePage(MarketPagePayload payload) {
        if (payload.requestId() != requestId) return;
        loading = false;
        if (!payload.error().isEmpty()) {
            entries = List.of();
            selectedIndex = -1;
            showStatus(payload.error(), false);
        } else {
            entries = payload.entries();
            page = payload.page();
            totalPages = payload.totalPages();
            totalListings = payload.totalListings();
            serverGameTime = payload.serverGameTime();
            if (selectedIndex >= entries.size()) {
                selectedIndex = -1;
            }
        }
    }

    public void receiveAction(MarketActionResultPayload payload) {
        loading = false;
        confirming = false;
        showStatus(payload.message(), payload.success());
        requestPage(page);
    }

    private void requestPage(int requestedPage) {
        if (minecraft == null || searchBox == null) return;
        page = Math.max(1, requestedPage);
        loading = true;
        selectedIndex = -1;
        requestId++;
        PacketDistributor.sendToServer(new MarketQueryPayload(requestId, page, category, searchBox.getValue(), sort));
    }

    private void cycleSort() {
        MarketSort[] values = MarketSort.values();
        sort = values[(sort.ordinal() + 1) % values.length];
        requestPage(1);
    }

    private void selectCategory(MarketCategory selectedCategory) {
        category = selectedCategory;
        selectedIndex = -1;
        requestPage(1);
    }

    private void updateButtonStates() {
        boolean contentEnabled = !confirming;
        categoryButtons.forEach((value, button) -> {
            button.active = contentEnabled;
            button.selected(value == category);
        });
        sortButton.active = contentEnabled;
        sortButton.setTooltip(Tooltip.create(sortLabel()));
        previousButton.active = contentEnabled && !loading && page > 1;
        nextButton.active = contentEnabled && !loading && page < totalPages;
        buyButton.active = contentEnabled && !loading && selectedEntry() != null;
        buyButton.setMessage(Component.literal(loading ? "กำลังซื้อ..." : "ซื้อ"));
        closeButton.active = contentEnabled;
        searchBox.setEditable(contentEnabled);

        boolean modalActive = confirming && selectedEntry() != null && !loading;
        confirmButton.visible = confirming;
        confirmButton.active = modalActive;
        cancelButton.visible = confirming;
        cancelButton.active = confirming;
    }

    private void beginPurchase() {
        if (selectedEntry() != null && !loading) confirming = true;
    }

    private void confirmPurchase() {
        MarketPagePayload.Entry selected = selectedEntry();
        if (selected == null) {
            confirming = false;
            return;
        }
        confirming = false;
        loading = true;
        PacketDistributor.sendToServer(new MarketBuyPayload(selected.listingId()));
    }

    private MarketPagePayload.Entry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < entries.size() ? entries.get(selectedIndex) : null;
    }

    private MarketPagePayload.Entry entryAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, gridX, gridY, gridWidth, ROWS * slotHeight)) return null;
        int index = (((int) mouseY - gridY) / slotHeight) * COLUMNS + (((int) mouseX - gridX) / slotWidth);
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private void showStatus(String message, boolean success) {
        status = message;
        statusColor = success ? 0xFF68D391 : 0xFFFF7B72;
        statusTicks = 100;
    }

    private Component sortLabel() {
        return Component.literal(switch (sort) {
            case PRICE_ASC -> "ราคาต่ำสุด";
            case PRICE_DESC -> "ราคาสูงสุด";
            case NEWEST -> "ใหม่ล่าสุด";
            case EXPIRING -> "ใกล้หมดเวลา";
        });
    }

    private String categoryLabel(MarketCategory value) {
        return switch (value) {
            case ALL -> "ทั้งหมด";
            case BLOCKS -> "บล็อก";
            case COMBAT -> "อาวุธและชุดเกราะ";
            case TOOLS -> "เครื่องมือ";
            case FOOD -> "อาหาร";
            case MATERIALS -> "วัตถุดิบ";
        };
    }

    private void blitWidget(GuiGraphics graphics, int x, int y, int width, int height,
                            int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(WIDGET_TEXTURE, x, y, width, height, u, v, sourceWidth, sourceHeight,
                WIDGET_TEXTURE_WIDTH, WIDGET_TEXTURE_HEIGHT);
    }

    private void blitSlot(GuiGraphics graphics, int x, int y, int width, int height,
                          int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(SLOT_TEXTURE, x, y, width, height, u, v, sourceWidth, sourceHeight,
                SLOT_TEXTURE_WIDTH, SLOT_TEXTURE_HEIGHT);
    }

    private int closeButtonX() {
        int rightOutside = panelX + panelWidth + 3;
        return rightOutside + 18 <= width - 2 ? rightOutside : panelX + panelWidth - 18;
    }

    private int closeButtonY() {
        return closeButtonX() > panelX + panelWidth
                ? panelY + scaleY(105)
                : Math.max(2, panelY - 20);
    }

    private int scaleX(int textureX) {
        return Math.round(textureX * panelWidth / (float) FRAME_TEXTURE_WIDTH);
    }

    private int scaleY(int textureY) {
        return Math.round(textureY * panelHeight / (float) FRAME_TEXTURE_HEIGHT);
    }

    private void drawSeparator(GuiGraphics graphics, int startX, int endX, int y) {
        graphics.fill(startX, y, endX, y + 1, 0xFF394147);
        graphics.fill(startX + 2, y + 1, endX - 2, y + 2, 0xFF171B1E);
    }

    private void drawRight(GuiGraphics graphics, String text, int right, int y, int color) {
        graphics.drawString(font, text, right - font.width(text), y, color, false);
    }

    private void drawCentered(GuiGraphics graphics, String text, int x, int width, int y, int color) {
        graphics.drawString(font, text, x + (width - font.width(text)) / 2, y, color, false);
    }

    private String trim(String text, int maxWidth) {
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String format(long value) {
        return String.format("%,d", value);
    }

    private static String formatTime(long ticks) {
        long minutes = (ticks + 1_199L) / 1_200L;
        if (minutes >= 1_440L) return (minutes / 1_440L) + " วัน";
        if (minutes >= 60L) return (minutes / 60L) + " ชม.";
        return minutes + " นาที";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
