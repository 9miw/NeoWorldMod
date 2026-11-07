package com.ambition.neoworld.client.gui.market;

import com.ambition.neoworld.client.gui.common.NeoBitmapFonts;

import com.ambition.neoworld.client.gui.widget.EconomyBalanceWidget;
import com.ambition.neoworld.client.gui.widget.NeoButton;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.economy.CurrencyMath;
import com.ambition.neoworld.market.model.MarketCategory;
import com.ambition.neoworld.market.model.MarketSort;
import com.ambition.neoworld.network.market.MarketActionResultPayload;
import com.ambition.neoworld.network.market.MarketBuyPayload;
import com.ambition.neoworld.network.market.MarketPagePayload;
import com.ambition.neoworld.network.market.MarketQueryPayload;

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
    private static final int QUANTITY_HOLD_DELAY_TICKS = 8;
    private static final int QUANTITY_HOLD_REPEAT_TICKS = 2;
    private static final String SILVER_ICON = "§f\uE046";

    private static final ResourceLocation SHELL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_shell_obsidian.png");
    private static final ResourceLocation SLOT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_slot_states.png");
    private static final ResourceLocation SEARCH_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_search_states.png");
    private static final ResourceLocation DETAIL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_detail_panel_v2.png");
    private static final ResourceLocation TITLE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_title_obsidian_gold.png");
    private static final int DESIGN_WIDTH = 350;
    private static final int DESIGN_HEIGHT = 225;
    private static final int SHELL_TEXTURE_WIDTH = 1530;
    private static final int SHELL_TEXTURE_HEIGHT = 882;
    private static final int SLOT_TEXTURE_WIDTH = 1944;
    private static final int SLOT_TEXTURE_HEIGHT = 809;
    private static final int SEARCH_TEXTURE_WIDTH = 1945;
    private static final int SEARCH_TEXTURE_HEIGHT = 808;
    private static final int DETAIL_TEXTURE_WIDTH = 602;
    private static final int DETAIL_TEXTURE_HEIGHT = 1354;
    private static final int TITLE_TEXTURE_WIDTH = 184;
    private static final int TITLE_TEXTURE_HEIGHT = 56;

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
    private NeoButton decreaseQuantityButton;
    private NeoButton increaseQuantityButton;
    private EconomyBalanceWidget balanceWidget;
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
    private int purchaseQuantity = 1;
    private int page = 1;
    private int totalPages = 1;
    private long totalListings;
    private long serverGameTime;
    private int requestId;
    private int pendingSearchTicks = -1;
    private int quantityHoldDelta;
    private int quantityHoldTicks;
    private double pointerX;
    private double pointerY;
    private boolean loading;
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
        String previousSearch = searchBox == null ? "" : searchBox.getValue();
        panelWidth = Math.min(DESIGN_WIDTH, width - 8);
        panelHeight = Math.min(DESIGN_HEIGHT, height - 8);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        categoryX = panelX + layoutX(14);
        gridX = panelX + layoutX(10);
        categoryY = panelY + layoutY(30);
        searchY = panelY + layoutY(59);
        gridY = panelY + layoutY(81);
        slotWidth = Math.max(18, layoutX(25));
        slotHeight = slotWidth;
        gridWidth = slotWidth * COLUMNS;
        detailX = panelX + layoutX(239);
        detailWidth = Math.max(76, panelX + panelWidth - layoutX(8) - detailX);
        categoryWidth = Math.max(24, layoutX(28));

        searchX = panelX + layoutX(10);
        searchWidth = layoutX(192);
        sortX = panelX + layoutX(207);
        pagerY = panelY + layoutY(199);
        buyY = panelY + layoutY(188);
        detailY = panelY + layoutY(58);

        searchBox = new EditBox(font, searchX + 30, searchY + 4, Math.max(20, searchWidth - 34), 14,
                Component.literal("ค้นหาสินค้า"));
        searchBox.setMaxLength(64);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFE7ECEF);
        searchBox.setTextColorUneditable(0xFF7F898F);
        searchBox.setHint(Component.literal("ค้นหา").withStyle(ChatFormatting.GRAY));
        searchBox.setValue(previousSearch);
        searchBox.setResponder(value -> pendingSearchTicks = SEARCH_DELAY_TICKS);
        addRenderableWidget(searchBox);

        categoryButtons.clear();
        int categoryIndex = 0;
        for (MarketCategory value : MarketCategory.values()) {
            int buttonX = categoryX + categoryIndex++ * (categoryWidth + layoutX(3));
            NeoButton button = addRenderableWidget(new NeoButton(
                    Button.builder(Component.empty(), ignored -> selectCategory(value))
                            .bounds(buttonX, categoryY, categoryWidth, categoryWidth)
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
        int quantityY = detailY + 81;
        decreaseQuantityButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal("-"), ignored -> changePurchaseQuantity(-1))
                        .bounds(detailX + detailWidth - 58, quantityY, 16, 16)
                        .tooltip(Tooltip.create(Component.literal("ลดจำนวนซื้อ")))));
        increaseQuantityButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal("+"), ignored -> changePurchaseQuantity(1))
                        .bounds(detailX + detailWidth - 23, quantityY, 16, 16)
                        .tooltip(Tooltip.create(Component.literal("เพิ่มจำนวนซื้อ")))));
        buyButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal("ซื้อ"), ignored -> beginPurchase())
                        .bounds(detailX + 7, buyY, detailWidth - 14, 22)
                        .tooltip(Tooltip.create(Component.literal("ซื้อสินค้า"))))
                .style(NeoButton.Style.PRIMARY));
        closeButton = addRenderableWidget(new NeoButton(
                Button.builder(NeoBitmapFonts.text("\uE01B"), ignored -> onClose())
                        .bounds(closeButtonX(), closeButtonY(), 24, 18)
                        .tooltip(Tooltip.create(Component.literal("ปิด"))))
                .style(NeoButton.Style.DESTRUCTIVE).textScrolling(false));

        int balanceWidth = 190;
        int balanceHeight = 20;
        int balanceX = 3;
        int balanceY = 0;
        balanceWidget = addWidget(new EconomyBalanceWidget(balanceX, balanceY, balanceWidth, balanceHeight,
                EconomyBalanceWidget.Alignment.LEFT));
        requestPage(page);
    }

    @Override
    public void tick() {
        super.tick();
        serverGameTime++;
        if (pendingSearchTicks > 0 && --pendingSearchTicks == 0) requestPage(1);
        if (statusTicks > 0) statusTicks--;
        tickQuantityHold();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        pointerX = mouseX;
        pointerY = mouseY;
        renderBackground(graphics, mouseX, mouseY, partialTick);
        renderFrame(graphics);
        renderTitlePlate(graphics);
        renderSearch(graphics);
        renderGrid(graphics, mouseX, mouseY);
        renderDetails(graphics, mouseX, mouseY);
        renderFooter(graphics, mouseX, mouseY);
        updateButtonStates();
        for (var renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        renderButtonIcons(graphics);
        balanceWidget.render(graphics, mouseX, mouseY, partialTick);

        MarketPagePayload.Entry hovered = entryAt(mouseX, mouseY);
        MarketPagePayload.Entry selected = selectedEntry();
        if (hovered != null) {
            graphics.renderTooltip(font, hovered.item(), mouseX, mouseY);
        } else if (selected != null
                && inside(mouseX, mouseY, detailX + 7, detailY + 6, detailWidth - 14, 34)) {
            graphics.renderTooltip(font, selected.item(), mouseX, mouseY);
        }
    }

    private void renderFrame(GuiGraphics graphics) {
        graphics.blit(SHELL_TEXTURE, panelX, panelY, panelWidth, panelHeight, 0, 0,
                SHELL_TEXTURE_WIDTH, SHELL_TEXTURE_HEIGHT, SHELL_TEXTURE_WIDTH, SHELL_TEXTURE_HEIGHT);
    }

    private void renderTitlePlate(GuiGraphics graphics) {
        int plateWidth = layoutX(160);
        int plateHeight = layoutY(34);
        int x = panelX + (panelWidth - plateWidth) / 2;
        int y = panelY - layoutY(2);
        graphics.blit(TITLE_TEXTURE, x, y, plateWidth, plateHeight, 0, 0,
                TITLE_TEXTURE_WIDTH, TITLE_TEXTURE_HEIGHT, TITLE_TEXTURE_WIDTH, TITLE_TEXTURE_HEIGHT);
        int textY = y + Math.round(plateHeight * 0.48F);
        graphics.pose().pushPose();
        graphics.pose().translate(x + plateWidth / 2.0F, textY, 0);
        graphics.pose().scale(1.25F, 1.25F, 1.0F);
        graphics.drawString(font, title, -font.width(title) / 2, -font.lineHeight / 2,
                0xFFFFF4DB, true);
        graphics.pose().popPose();
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
                    button.getX() + (button.getWidth() - 16) / 2,
                    button.getY() + (button.getHeight() - 16) / 2);
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
                graphics.renderItemDecorations(font, item, itemX, itemY);
            }
        }
    }

    private void renderDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        int top = detailY;
        int targetHeight = Math.max(80, panelY + panelHeight - top - layoutY(10));
        blitNineSlice(graphics, DETAIL_TEXTURE, detailX, top, detailWidth, targetHeight,
                DETAIL_TEXTURE_WIDTH, DETAIL_TEXTURE_HEIGHT, 64, 7);
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
        graphics.drawString(font, "คงเหลือ", textX, statsY, COLOR_TEXT_MUTED, false);
        drawRight(graphics, String.valueOf(item.getCount()), detailX + detailWidth - 8, statsY, 0xFFF1F3F4);
        graphics.drawString(font, "ราคาต่อชิ้น", textX, statsY + 11, COLOR_TEXT_MUTED, false);
        drawRight(graphics, formatSilver(selected.priceSilver()), detailX + detailWidth - 8, statsY + 11,
                COLOR_GOLD);
        long ticks = Math.max(0L, selected.expiresAtGameTime() - serverGameTime);
        graphics.drawString(font, "เหลือ", textX, statsY + 22, COLOR_TEXT_MUTED, false);
        drawRight(graphics, formatTime(ticks), detailX + detailWidth - 8, statsY + 22, 0xFF68D391);
        graphics.drawString(font, "จำนวน", textX, statsY + 38, COLOR_TEXT_MUTED, false);
        int quantityValueX = decreaseQuantityButton.getX() + decreaseQuantityButton.getWidth();
        int quantityValueWidth = increaseQuantityButton.getX() - quantityValueX;
        drawCentered(graphics, String.valueOf(purchaseQuantity), quantityValueX, quantityValueWidth,
                statsY + 38, 0xFFF1F3F4);
        drawSeparator(graphics, detailX + 7, detailX + detailWidth - 7, buyY - 15);
        graphics.drawString(font, "ราคารวม", textX, buyY - 25, COLOR_TEXT_MUTED, false);
        drawRight(graphics, formatSilver(purchaseTotal(selected)), detailX + detailWidth - 8, buyY - 25,
                COLOR_GOLD);
    }

    private void renderFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        String pageText = "หน้า " + page + "/" + totalPages + "  (" + format(totalListings) + " รายการ)";
        drawCentered(graphics, pageText, gridX + 24, gridWidth - 48, pagerY + 6, 0xFFBEC6CB);
        if (statusTicks > 0 && !status.isEmpty()) {
            graphics.drawString(font, trim(status, gridWidth), gridX, gridY - 11,
                    statusColor, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) stopQuantityHold();
        if (inside(mouseX, mouseY, gridX, gridY, gridWidth, ROWS * slotHeight)) {
            int column = ((int) mouseX - gridX) / slotWidth;
            int row = ((int) mouseY - gridY) / slotHeight;
            int index = row * COLUMNS + column;
            if (index >= 0 && index < entries.size()) {
                selectedIndex = index;
                purchaseQuantity = 1;
            }
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (button == 0 && handled) {
            if (decreaseQuantityButton.active && decreaseQuantityButton.visible
                    && decreaseQuantityButton.isMouseOver(mouseX, mouseY)) {
                startQuantityHold(-1);
            } else if (increaseQuantityButton.active && increaseQuantityButton.visible
                    && increaseQuantityButton.isMouseOver(mouseX, mouseY)) {
                startQuantityHold(1);
            }
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) stopQuantityHold();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void receivePage(MarketPagePayload payload) {
        if (payload.requestId() != requestId) return;
        loading = false;
        if (!payload.error().isEmpty()) {
            entries = List.of();
            selectedIndex = -1;
            purchaseQuantity = 1;
            showStatus(payload.error(), false);
        } else {
            entries = payload.entries();
            page = payload.page();
            totalPages = payload.totalPages();
            totalListings = payload.totalListings();
            serverGameTime = payload.serverGameTime();
            if (selectedIndex >= entries.size()) {
                selectedIndex = -1;
                purchaseQuantity = 1;
            }
        }
    }

    public void receiveAction(MarketActionResultPayload payload) {
        loading = false;
        requestPage(page);
        NeoPopupScreen.showAlert(
                Component.literal(payload.success() ? "ทำรายการสำเร็จ" : "ไม่สามารถทำรายการได้"),
                Component.literal(payload.message()),
                () -> {});
    }

    private void requestPage(int requestedPage) {
        if (minecraft == null || searchBox == null) return;
        page = Math.max(1, requestedPage);
        loading = true;
        selectedIndex = -1;
        purchaseQuantity = 1;
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
        boolean contentEnabled = true;
        categoryButtons.forEach((value, button) -> {
            button.active = contentEnabled;
            button.selected(value == category);
        });
        sortButton.active = contentEnabled;
        sortButton.setTooltip(Tooltip.create(sortLabel()));
        previousButton.active = contentEnabled && !loading && page > 1;
        nextButton.active = contentEnabled && !loading && page < totalPages;
        MarketPagePayload.Entry selected = selectedEntry();
        boolean validPurchase = selected != null && purchaseTotal(selected) > 0;
        buyButton.active = contentEnabled && !loading && validPurchase;
        buyButton.setMessage(Component.literal(loading ? "กำลังซื้อ..." : "ซื้อ"));
        decreaseQuantityButton.visible = selected != null;
        decreaseQuantityButton.active = contentEnabled && !loading && purchaseQuantity > 1;
        increaseQuantityButton.visible = selected != null;
        increaseQuantityButton.active = selected != null && contentEnabled && !loading
                && purchaseQuantity < selected.item().getCount();
        closeButton.active = contentEnabled;
        searchBox.setEditable(contentEnabled);

    }

    private void beginPurchase() {
        MarketPagePayload.Entry selected = selectedEntry();
        long totalPrice = selected == null ? -1L : purchaseTotal(selected);
        if (selected == null || totalPrice <= 0 || loading) return;
        long listingId = selected.listingId();
        int quantity = purchaseQuantity;
        Component message = NeoBitmapFonts.text(selected.item().getHoverName().getString()
                + "\nจำนวน " + quantity + " | " + formatSilver(totalPrice));
        NeoPopupScreen.showChoice(
                Component.literal("ยืนยันการซื้อ"),
                message,
                Component.literal("ยืนยัน"),
                () -> confirmPurchase(listingId, quantity),
                Component.literal("ยกเลิก"),
                () -> {});
    }

    private void changePurchaseQuantity(int delta) {
        MarketPagePayload.Entry selected = selectedEntry();
        if (selected == null || loading) return;
        purchaseQuantity = Math.max(1, Math.min(selected.item().getCount(), purchaseQuantity + delta));
    }

    private void startQuantityHold(int delta) {
        quantityHoldDelta = delta;
        quantityHoldTicks = 0;
    }

    private void stopQuantityHold() {
        quantityHoldDelta = 0;
        quantityHoldTicks = 0;
    }

    private void tickQuantityHold() {
        if (quantityHoldDelta == 0) return;
        NeoButton button = quantityHoldDelta < 0 ? decreaseQuantityButton : increaseQuantityButton;
        if (button == null || !button.active || !button.visible || !button.isMouseOver(pointerX, pointerY)) {
            stopQuantityHold();
            return;
        }
        quantityHoldTicks++;
        if (quantityHoldTicks >= QUANTITY_HOLD_DELAY_TICKS
                && (quantityHoldTicks - QUANTITY_HOLD_DELAY_TICKS) % QUANTITY_HOLD_REPEAT_TICKS == 0) {
            changePurchaseQuantity(quantityHoldDelta);
        }
    }

    private void confirmPurchase(long listingId, int quantity) {
        loading = true;
        PacketDistributor.sendToServer(new MarketBuyPayload(java.util.UUID.randomUUID(), listingId, quantity));
    }

    private MarketPagePayload.Entry selectedEntry() {
        return selectedIndex >= 0 && selectedIndex < entries.size() ? entries.get(selectedIndex) : null;
    }

    private long purchaseTotal(MarketPagePayload.Entry entry) {
        return CurrencyMath.total(entry.priceSilver(), purchaseQuantity);
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
            case ALL -> "ALL";
            case BLOCKS -> "บล็อก";
            case COMBAT -> "อาวุธและชุดเกราะ";
            case TOOLS -> "เครื่องมือ";
            case FOOD -> "อาหาร";
            case MATERIALS -> "วัตถุดิบ";
        };
    }

    private void blitSlot(GuiGraphics graphics, int x, int y, int width, int height,
                          int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(SLOT_TEXTURE, x, y, width, height, u, v, sourceWidth, sourceHeight,
                SLOT_TEXTURE_WIDTH, SLOT_TEXTURE_HEIGHT);
    }

    private void blitNineSlice(GuiGraphics graphics, ResourceLocation texture,
                               int x, int y, int width, int height,
                               int textureWidth, int textureHeight,
                               int sourceBorder, int targetBorder) {
        int sourceXBorder = Math.min(sourceBorder, textureWidth / 2);
        int sourceYBorder = Math.min(sourceBorder, textureHeight / 2);
        int targetXBorder = Math.min(targetBorder, width / 2);
        int targetYBorder = Math.min(targetBorder, height / 2);
        int sourceCenterWidth = textureWidth - sourceXBorder * 2;
        int sourceCenterHeight = textureHeight - sourceYBorder * 2;
        int targetCenterWidth = width - targetXBorder * 2;
        int targetCenterHeight = height - targetYBorder * 2;

        graphics.blit(texture, x, y, targetXBorder, targetYBorder,
                0, 0, sourceXBorder, sourceYBorder, textureWidth, textureHeight);
        graphics.blit(texture, x + targetXBorder, y, targetCenterWidth, targetYBorder,
                sourceXBorder, 0, sourceCenterWidth, sourceYBorder, textureWidth, textureHeight);
        graphics.blit(texture, x + width - targetXBorder, y, targetXBorder, targetYBorder,
                textureWidth - sourceXBorder, 0, sourceXBorder, sourceYBorder, textureWidth, textureHeight);
        graphics.blit(texture, x, y + targetYBorder, targetXBorder, targetCenterHeight,
                0, sourceYBorder, sourceXBorder, sourceCenterHeight, textureWidth, textureHeight);
        graphics.blit(texture, x + targetXBorder, y + targetYBorder, targetCenterWidth, targetCenterHeight,
                sourceXBorder, sourceYBorder, sourceCenterWidth, sourceCenterHeight, textureWidth, textureHeight);
        graphics.blit(texture, x + width - targetXBorder, y + targetYBorder, targetXBorder, targetCenterHeight,
                textureWidth - sourceXBorder, sourceYBorder, sourceXBorder, sourceCenterHeight,
                textureWidth, textureHeight);
        graphics.blit(texture, x, y + height - targetYBorder, targetXBorder, targetYBorder,
                0, textureHeight - sourceYBorder, sourceXBorder, sourceYBorder, textureWidth, textureHeight);
        graphics.blit(texture, x + targetXBorder, y + height - targetYBorder, targetCenterWidth, targetYBorder,
                sourceXBorder, textureHeight - sourceYBorder, sourceCenterWidth, sourceYBorder,
                textureWidth, textureHeight);
        graphics.blit(texture, x + width - targetXBorder, y + height - targetYBorder,
                targetXBorder, targetYBorder, textureWidth - sourceXBorder, textureHeight - sourceYBorder,
                sourceXBorder, sourceYBorder, textureWidth, textureHeight);
    }

    private int closeButtonX() {
        int rightOutside = panelX + panelWidth + 3;
        return rightOutside + 18 <= width - 2 ? rightOutside : panelX + panelWidth - 18;
    }

    private int closeButtonY() {
        return closeButtonX() > panelX + panelWidth
                ? panelY + layoutY(3)
                : Math.max(2, panelY - 20);
    }

    private int layoutX(int designX) {
        return Math.round(designX * panelWidth / (float) DESIGN_WIDTH);
    }

    private int layoutY(int designY) {
        return Math.round(designY * panelHeight / (float) DESIGN_HEIGHT);
    }

    private void drawSeparator(GuiGraphics graphics, int startX, int endX, int y) {
        graphics.fill(startX, y, endX, y + 1, 0xFF394147);
        graphics.fill(startX + 2, y + 1, endX - 2, y + 2, 0xFF171B1E);
    }

    private void drawRight(GuiGraphics graphics, String text, int right, int y, int color) {
        Component label = NeoBitmapFonts.text(text);
        graphics.drawString(font, label, right - font.width(label), y, color, false);
    }

    private void drawCentered(GuiGraphics graphics, String text, int x, int width, int y, int color) {
        Component label = NeoBitmapFonts.text(text);
        graphics.drawString(font, label, x + (width - font.width(label)) / 2, y, color, false);
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

    private static String formatSilver(long value) {
        return format(value) + " " + SILVER_ICON;
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
