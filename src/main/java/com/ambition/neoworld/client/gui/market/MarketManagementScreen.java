package com.ambition.neoworld.client.gui.market;

import com.ambition.neoworld.client.gui.common.NeoBitmapFonts;

import java.util.ArrayList;
import java.util.List;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.market.model.MarketManagementEntry;
import com.ambition.neoworld.network.market.MarketActionResultPayload;
import com.ambition.neoworld.network.market.MarketManagementActionPayload;
import com.ambition.neoworld.network.market.MarketManagementPagePayload;
import com.ambition.neoworld.network.market.MarketManagementQueryPayload;
import com.ambition.neoworld.network.market.MarketSellOpenPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Unified seller view for active listings, pending proceeds and claimed sales. */
public final class MarketManagementScreen extends Screen {
    private static final int PAGE_SIZE = 8;
    private static final String SILVER_ICON = "§f\uE046";
    private static final ResourceLocation FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_frame.png");
    private static final ResourceLocation SLOT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_slot_states.png");
    private static final int FRAME_TEXTURE_WIDTH = 1556;
    private static final int FRAME_TEXTURE_HEIGHT = 1011;
    private static final int SLOT_TEXTURE_WIDTH = 1944;
    private static final int SLOT_TEXTURE_HEIGHT = 809;
    private static final int COLOR_TEXT = 0xFFE7ECEF;
    private static final int COLOR_MUTED = 0xFFAAB3B8;
    private static final int COLOR_TEAL = 0xFF37C9BE;
    private static final int COLOR_GOLD = 0xFFF2C14E;
    private static final int COLOR_ROW = 0xE8101518;
    private static final int COLOR_ROW_ALT = 0xE8161C20;
    private static final int COLOR_ROW_HOVER = 0xE7193939;
    private static final int COLOR_LINE = 0xFF4A5359;

    private final List<NeoButton> statusButtons = new ArrayList<>();
    private List<MarketManagementPagePayload.Entry> entries = List.of();
    private NeoButton previousButton;
    private NeoButton nextButton;
    private NeoButton claimAllButton;
    private NeoButton sellButton;
    private NeoButton closeButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentWidth;
    private int summaryY;
    private int headerY;
    private int rowsY;
    private int rowHeight;
    private int footerY;
    private int itemEnd;
    private int quantityEnd;
    private int unitPriceEnd;
    private int proceedsEnd;
    private int updatedEnd;
    private int page = 1;
    private int totalPages = 1;
    private long totalEntries;
    private long pendingSilver;
    private long serverGameTime;
    private int requestId;
    private int refreshTicks;
    private boolean loading;

    public MarketManagementScreen() {
        super(Component.literal("จัดการแผงขาย"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(440, width - 8);
        panelHeight = Math.min(242, height - 8);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        contentX = panelX + 11;
        contentWidth = panelWidth - 22;
        summaryY = panelY + 34;
        headerY = panelY + 56;
        rowsY = panelY + 73;
        footerY = panelY + panelHeight - 25;
        rowHeight = Math.max(14, (footerY - rowsY - 2) / PAGE_SIZE);
        itemEnd = contentX + Math.round(contentWidth * 0.28F);
        quantityEnd = itemEnd + Math.round(contentWidth * 0.10F);
        unitPriceEnd = quantityEnd + Math.round(contentWidth * 0.15F);
        proceedsEnd = unitPriceEnd + Math.round(contentWidth * 0.16F);
        updatedEnd = proceedsEnd + Math.round(contentWidth * 0.15F);

        statusButtons.clear();
        for (int index = 0; index < PAGE_SIZE; index++) {
            final int rowIndex = index;
            NeoButton button = addRenderableWidget(new NeoButton(
                    Button.builder(Component.empty(), ignored -> activateRow(rowIndex))
                            .bounds(updatedEnd + 2, rowsY + index * rowHeight + 1,
                                    Math.max(1, contentX + contentWidth - updatedEnd - 4),
                                    Math.max(12, rowHeight - 2))));
            statusButtons.add(button);
        }

        int totalWidth = Math.max(88, Math.round(contentWidth * 0.30F));
        int pagerX = contentX + totalWidth + 5;
        previousButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal("<"), ignored -> requestPage(page - 1))
                        .bounds(pagerX, footerY + 2, 20, 19)
                        .tooltip(Tooltip.create(Component.literal("หน้าก่อนหน้า")))));
        nextButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal(">"), ignored -> requestPage(page + 1))
                        .bounds(pagerX + 72, footerY + 2, 20, 19)
                        .tooltip(Tooltip.create(Component.literal("หน้าถัดไป")))));

        int actionGap = 5;
        int sellWidth = Math.max(76, Math.round(contentWidth * 0.20F));
        int claimWidth = Math.max(78, Math.round(contentWidth * 0.20F));
        int sellX = contentX + contentWidth - sellWidth;
        int claimX = sellX - actionGap - claimWidth;
        claimAllButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal("รับเงินทั้งหมด"), ignored -> beginClaimAll())
                        .bounds(claimX, footerY + 2, claimWidth, 19))
                .style(NeoButton.Style.PRIMARY));
        sellButton = addRenderableWidget(new NeoButton(
                Button.builder(Component.literal("+ ขายสินค้า"), ignored -> openSellScreen())
                        .bounds(sellX, footerY + 2, sellWidth, 19))
                .style(NeoButton.Style.PRIMARY));
        closeButton = addRenderableWidget(new NeoButton(
                Button.builder(NeoBitmapFonts.text("\uE01B"), ignored -> onClose())
                        .bounds(closeButtonX(), closeButtonY(), 24, 18)
                        .tooltip(Tooltip.create(Component.literal("ปิด"))))
                .style(NeoButton.Style.DESTRUCTIVE).textScrolling(false));
        requestPage(page);
    }

    @Override
    public void tick() {
        super.tick();
        serverGameTime++;
        if (!loading && ++refreshTicks >= 100) {
            refreshTicks = 0;
            requestPage(page);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(FRAME_TEXTURE, panelX, panelY, panelWidth, panelHeight, 0, 0,
                FRAME_TEXTURE_WIDTH, FRAME_TEXTURE_HEIGHT, FRAME_TEXTURE_WIDTH, FRAME_TEXTURE_HEIGHT);
        renderTitle(graphics);
        renderSummary(graphics);
        renderHeader(graphics);
        renderRows(graphics, mouseX, mouseY);
        renderFooter(graphics);
        updateButtons();
        for (var renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        MarketManagementPagePayload.Entry hovered = rowAt(mouseX, mouseY);
        if (hovered != null && mouseX < itemEnd) {
            graphics.renderTooltip(font, hovered.item(), mouseX, mouseY);
        }
    }

    private void renderTitle(GuiGraphics graphics) {
        int y = panelY + 10;
        graphics.pose().pushPose();
        graphics.pose().translate(panelX + panelWidth / 2.0F, y, 0);
        graphics.pose().scale(1.22F, 1.22F, 1.0F);
        graphics.drawString(font, title, -font.width(title) / 2, 0, COLOR_TEXT, false);
        graphics.pose().popPose();
    }

    private void renderSummary(GuiGraphics graphics) {
        graphics.fill(contentX, summaryY, contentX + contentWidth, summaryY + 19, 0xE6191E21);
        graphics.renderOutline(contentX, summaryY, contentWidth, 19, COLOR_LINE);
        String total = "รายการทั้งหมด " + format(totalEntries);
        graphics.drawString(font, total, contentX + contentWidth / 4 - font.width(total) / 2,
                summaryY + 6, COLOR_TEXT, false);
        String pending = "เงินรอรับ  " + formatSilver(pendingSilver);
        graphics.drawString(font, pending, contentX + contentWidth * 3 / 4 - font.width(pending) / 2,
                summaryY + 6, COLOR_GOLD, false);
        graphics.fill(contentX + contentWidth / 2, summaryY + 3,
                contentX + contentWidth / 2 + 1, summaryY + 16, COLOR_LINE);
    }

    private void renderHeader(GuiGraphics graphics) {
        graphics.fill(contentX, headerY, contentX + contentWidth, rowsY - 2, 0xF01A2024);
        graphics.renderOutline(contentX, headerY, contentWidth, rowsY - headerY - 2, COLOR_LINE);
        drawCentered(graphics, "สินค้า", contentX, itemEnd, headerY + 4, COLOR_TEXT);
        drawCentered(graphics, "จำนวน", itemEnd, quantityEnd, headerY + 4, COLOR_TEXT);
        drawCentered(graphics, "ราคาต่อชิ้น", quantityEnd, unitPriceEnd, headerY + 4, COLOR_TEXT);
        drawCentered(graphics, "ยอดสุทธิ", unitPriceEnd, proceedsEnd, headerY + 4, COLOR_TEXT);
        drawCentered(graphics, "อัปเดตเมื่อ", proceedsEnd, updatedEnd, headerY + 4, COLOR_TEXT);
        drawCentered(graphics, "สถานะ", updatedEnd, contentX + contentWidth, headerY + 4, COLOR_TEXT);
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = 0; index < PAGE_SIZE; index++) {
            int y = rowsY + index * rowHeight;
            boolean hovered = inside(mouseX, mouseY, contentX, y, contentWidth, rowHeight - 1);
            int color = hovered ? COLOR_ROW_HOVER : (index % 2 == 0 ? COLOR_ROW : COLOR_ROW_ALT);
            graphics.fill(contentX, y, contentX + contentWidth, y + rowHeight - 1, color);
            graphics.fill(contentX, y + rowHeight - 1, contentX + contentWidth, y + rowHeight, 0xFF30383D);
            if (index >= entries.size()) continue;
            MarketManagementPagePayload.Entry entry = entries.get(index);
            ItemStack item = entry.item();
            int slotSize = Math.min(18, rowHeight - 1);
            int slotY = y + (rowHeight - slotSize) / 2;
            blitSlot(graphics, contentX + 2, slotY, slotSize, slotSize,
                    hovered ? 735 : 139, 168, 474, 474);
            int itemX = contentX + 2 + Math.max(1, (slotSize - 16) / 2);
            int itemY = slotY + Math.max(1, (slotSize - 16) / 2);
            graphics.renderItem(item, itemX, itemY);
            String name = trim(item.getHoverName().getString(), Math.max(1, itemEnd - contentX - slotSize - 8));
            graphics.drawString(font, name, contentX + slotSize + 6,
                    y + (rowHeight - font.lineHeight) / 2, COLOR_TEXT, false);
            drawCentered(graphics, String.valueOf(entry.quantity()), itemEnd, quantityEnd,
                    y + (rowHeight - font.lineHeight) / 2, COLOR_TEXT);
            drawCentered(graphics, formatSilver(entry.unitPriceSilver()), quantityEnd, unitPriceEnd,
                    y + (rowHeight - font.lineHeight) / 2, COLOR_MUTED);
            drawCentered(graphics, formatSilver(entry.proceedsSilver()), unitPriceEnd, proceedsEnd,
                    y + (rowHeight - font.lineHeight) / 2,
                    entry.status() == MarketManagementEntry.Status.PENDING ? COLOR_GOLD : COLOR_MUTED);
            drawCentered(graphics, timeLabel(entry), proceedsEnd, updatedEnd,
                    y + (rowHeight - font.lineHeight) / 2, COLOR_MUTED);
        }
    }

    private void renderFooter(GuiGraphics graphics) {
        int totalWidth = Math.max(88, Math.round(contentWidth * 0.30F));
        graphics.fill(contentX, footerY + 1, contentX + totalWidth, footerY + 22, 0xEE2B230C);
        graphics.renderOutline(contentX, footerY + 1, totalWidth, 21, COLOR_GOLD);
        String total = "รวมเงินรอรับ  " + formatSilver(pendingSilver);
        drawCentered(graphics, total, contentX, contentX + totalWidth, footerY + 7, COLOR_GOLD);
        int pagerX = previousButton.getX();
        drawCentered(graphics, "หน้า " + page + "/" + totalPages,
                pagerX + 20, pagerX + 72, footerY + 7, COLOR_TEXT);
        if (loading && entries.isEmpty()) {
            drawCentered(graphics, "กำลังโหลด...", contentX, contentX + contentWidth,
                    rowsY + 24, COLOR_MUTED);
        }
    }

    private void updateButtons() {
        for (int index = 0; index < statusButtons.size(); index++) {
            NeoButton button = statusButtons.get(index);
            if (index >= entries.size()) {
                button.visible = false;
                continue;
            }
            button.visible = true;
            MarketManagementEntry.Status status = entries.get(index).status();
            button.setMessage(Component.literal(switch (status) {
                case ACTIVE -> "กำลังขาย";
                case PENDING -> "รับเงิน";
                case CLAIMED -> "รับแล้ว";
            }));
            button.style(status == MarketManagementEntry.Status.PENDING
                    ? NeoButton.Style.PRIMARY : NeoButton.Style.STANDARD);
            button.active = !loading && status != MarketManagementEntry.Status.CLAIMED;
            button.setTooltip(Tooltip.create(Component.literal(switch (status) {
                case ACTIVE -> "กดเพื่อยกเลิกรายการ";
                case PENDING -> "กดเพื่อรับเงินรายการนี้";
                case CLAIMED -> "รับเงินแล้ว";
            })));
        }
        previousButton.active = !loading && page > 1;
        nextButton.active = !loading && page < totalPages;
        claimAllButton.active = !loading && pendingSilver > 0;
        sellButton.active = !loading;
        closeButton.active = true;
    }

    private void activateRow(int index) {
        if (loading || index < 0 || index >= entries.size()) return;
        MarketManagementPagePayload.Entry entry = entries.get(index);
        if (entry.status() == MarketManagementEntry.Status.ACTIVE) {
            NeoPopupScreen.showChoice(
                    Component.literal("ยกเลิกรายการขาย"),
                    Component.literal(entry.item().getHoverName().getString() + " x" + entry.quantity()
                            + "\nสินค้าจะถูกส่งกลับคลังรับของตลาด"),
                    Component.literal("ยืนยัน"),
                    () -> sendAction(MarketManagementActionPayload.Action.CANCEL_LISTING, entry.listingId()),
                    Component.literal("กลับ"), () -> {});
        } else if (entry.status() == MarketManagementEntry.Status.PENDING) {
            sendAction(MarketManagementActionPayload.Action.CLAIM_SALE, entry.recordId());
        }
    }

    private void beginClaimAll() {
        if (loading || pendingSilver <= 0) return;
        NeoPopupScreen.showChoice(
                Component.literal("รับเงินทั้งหมด"),
                Component.literal("รับเงิน " + formatSilver(pendingSilver) + " เข้ากระเป๋าเงิน"),
                Component.literal("รับเงิน"),
                () -> sendAction(MarketManagementActionPayload.Action.CLAIM_ALL, 0L),
                Component.literal("กลับ"), () -> {});
    }

    private void openSellScreen() {
        PacketDistributor.sendToServer(MarketSellOpenPayload.INSTANCE);
    }

    private void sendAction(MarketManagementActionPayload.Action action, long targetId) {
        loading = true;
        PacketDistributor.sendToServer(new MarketManagementActionPayload(action, targetId));
    }

    private void requestPage(int requestedPage) {
        page = Math.max(1, requestedPage);
        loading = true;
        refreshTicks = 0;
        requestId++;
        PacketDistributor.sendToServer(new MarketManagementQueryPayload(requestId, page));
    }

    public void receivePage(MarketManagementPagePayload payload) {
        if (payload.requestId() != requestId) return;
        loading = false;
        if (!payload.error().isEmpty()) {
            entries = List.of();
            NeoPopupScreen.showAlert(Component.literal("ไม่สามารถโหลดแผงขาย"),
                    Component.literal(payload.error()), () -> {});
            return;
        }
        entries = payload.entries();
        page = payload.page();
        totalPages = payload.totalPages();
        totalEntries = payload.totalEntries();
        pendingSilver = payload.pendingSilver();
        serverGameTime = payload.serverGameTime();
    }

    public void receiveAction(MarketActionResultPayload payload) {
        loading = false;
        requestPage(page);
        NeoPopupScreen.showAlert(
                Component.literal(payload.success() ? "ทำรายการสำเร็จ" : "ไม่สามารถทำรายการได้"),
                Component.literal(payload.message()), () -> {});
    }

    private MarketManagementPagePayload.Entry rowAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, contentX, rowsY, contentWidth, rowHeight * PAGE_SIZE)) return null;
        int index = ((int) mouseY - rowsY) / rowHeight;
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private String timeLabel(MarketManagementPagePayload.Entry entry) {
        if (entry.status() == MarketManagementEntry.Status.ACTIVE) {
            return "เหลือ " + formatDuration(Math.max(0L, entry.expiresGameTime() - serverGameTime));
        }
        return formatDuration(Math.max(0L, serverGameTime - entry.updatedGameTime())) + " ที่แล้ว";
    }

    private void blitSlot(GuiGraphics graphics, int x, int y, int width, int height,
                          int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(SLOT_TEXTURE, x, y, width, height, u, v, sourceWidth, sourceHeight,
                SLOT_TEXTURE_WIDTH, SLOT_TEXTURE_HEIGHT);
    }

    private int closeButtonX() {
        int outside = panelX + panelWidth + 3;
        return outside + 24 <= width - 2 ? outside : panelX + panelWidth - 25;
    }

    private int closeButtonY() {
        return closeButtonX() > panelX + panelWidth ? panelY + 13 : Math.max(2, panelY - 20);
    }

    private void drawCentered(GuiGraphics graphics, String text, int left, int right, int y, int color) {
        int width = Math.max(1, right - left);
        var clipped = net.minecraft.locale.Language.getInstance().getVisualOrder(
                font.substrByWidth(NeoBitmapFonts.text(text), Math.max(1, width - 4)));
        graphics.drawString(font, clipped, left + (width - font.width(clipped)) / 2, y, color, false);
    }

    private String trim(String text, int width) {
        return font.plainSubstrByWidth(text, Math.max(1, width));
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

    private static String formatDuration(long ticks) {
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
