package com.ambition.neoworld.client.gui.market;

import com.ambition.neoworld.client.gui.common.NeoBitmapFonts;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.market.MarketMath;
import com.ambition.neoworld.market.menu.MarketSellMenu;
import com.ambition.neoworld.network.market.MarketActionResultPayload;
import com.ambition.neoworld.network.market.MarketSellSubmitPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Container-backed listing form. Text and live values stay out of the PNG frame. */
public final class MarketSellScreen extends AbstractContainerScreen<MarketSellMenu> {
    private static final ResourceLocation SHELL = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_sell_shell.png");
    private static final ResourceLocation PANELS = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_sell_panels.png");
    private static final ResourceLocation SLOT_STATES = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/market/market_slot_states.png");
    private static final int FRAME_WIDTH = 1671;
    private static final int FRAME_HEIGHT = 941;
    private static final int SLOT_ATLAS_WIDTH = 1944;
    private static final int SLOT_ATLAS_HEIGHT = 809;
    private static final int COLOR_TEXT = 0xFFE7ECEF;
    private static final int COLOR_MUTED = 0xFFAAB3B8;
    private static final int COLOR_TEAL = 0xFF37C9BE;
    private static final int COLOR_GOLD = 0xFFF2C14E;
    private static final String SILVER_ICON = "\u00a7f\uE046";
    private static final long[] DURATIONS = {
            60L * 60L * 20L,
            12L * 60L * 60L * 20L,
            24L * 60L * 60L * 20L,
            48L * 60L * 60L * 20L,
            7L * 24L * 60L * 60L * 20L
    };
    private static final String[] DURATION_LABELS = {"1 ชั่วโมง", "12 ชั่วโมง", "24 ชั่วโมง", "48 ชั่วโมง", "7 วัน"};

    private EditBox priceInput;
    private NeoButton decreaseButton;
    private NeoButton increaseButton;
    private NeoButton durationButton;
    private NeoButton confirmButton;
    private int quantity = 1;
    private int durationIndex = 3;
    private boolean submitting;

    public MarketSellScreen(MarketSellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MarketSellMenu.WIDTH;
        imageHeight = MarketSellMenu.HEIGHT;
        inventoryLabelY = 10_000;
        titleLabelY = 10_000;
    }

    @Override
    protected void init() {
        String preservedPrice = priceInput == null ? "1" : priceInput.getValue();
        super.init();
        int formX = leftPos + 281;
        priceInput = new EditBox(font, formX + 73, topPos + 99, 70, 18, Component.literal("ราคาต่อชิ้น"));
        priceInput.setMaxLength(18);
        priceInput.setFilter(value -> value.matches("[0-9]*"));
        priceInput.setValue(preservedPrice);
        addRenderableWidget(priceInput);

        decreaseButton = addRenderableWidget(button("-", formX + 73, topPos + 76, 20, 18,
                () -> changeQuantity(-1)));
        increaseButton = addRenderableWidget(button("+", formX + 123, topPos + 76, 20, 18,
                () -> changeQuantity(1)));
        durationButton = addRenderableWidget(button(DURATION_LABELS[durationIndex], formX + 73,
                topPos + 122, 70, 18, this::cycleDuration));

        addRenderableWidget(button("กลับ", leftPos + 15, topPos + 219, 68, 20, this::backToManagement));
        confirmButton = addRenderableWidget(button("ยืนยันวางขาย", leftPos + imageWidth - 116,
                topPos + 219, 101, 20, this::confirmListing).style(NeoButton.Style.PRIMARY));
        NeoButton close = addRenderableWidget(button("\uE01B", closeButtonX(), closeButtonY(), 24, 18, this::onClose)
                .style(NeoButton.Style.DESTRUCTIVE).textScrolling(false));
        close.setTooltip(Tooltip.create(Component.literal("ปิดและคืนสินค้าที่ยังไม่ได้วางขาย")));
    }

    private NeoButton button(String label, int x, int y, int width, int height, Runnable action) {
        return new NeoButton(Button.builder(NeoBitmapFonts.text(label), ignored -> action.run())
                .bounds(x, y, width, height));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack stack = menu.getSlot(0).getItem();
        int maximum = Math.max(1, stack.getCount());
        quantity = Math.min(quantity, maximum);
        boolean ready = !submitting && !stack.isEmpty() && validPrice() > 0 && menu.getCarried().isEmpty();
        decreaseButton.active = !submitting && quantity > 1;
        increaseButton.active = !submitting && !stack.isEmpty() && quantity < stack.getCount();
        durationButton.active = !submitting;
        confirmButton.active = ready;
        priceInput.setEditable(!submitting);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(SHELL, leftPos, topPos, imageWidth, imageHeight,
                0, 0, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
        graphics.blit(PANELS, leftPos, topPos, imageWidth, imageHeight,
                0, 0, FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
        for (Slot slot : menu.slots) {
            int size = slot.index == 0 ? 24 : 20;
            int offset = (size - 16) / 2;
            graphics.blit(SLOT_STATES, leftPos + slot.x - offset, topPos + slot.y - offset, size, size,
                    slot.index == 0 ? 735 : 139, 168, 474, 474, SLOT_ATLAS_WIDTH, SLOT_ATLAS_HEIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCentered(graphics, "วางขายสินค้า", 0, imageWidth, 10, COLOR_TEXT);
        graphics.drawString(font, "ช่องเก็บของผู้เล่น", 20, 45, COLOR_TEXT, false);
        graphics.drawString(font, "สินค้าที่วางขาย", 281, 35, COLOR_TEXT, false);

        ItemStack stack = menu.getSlot(0).getItem();
        String itemName = stack.isEmpty() ? "วางไอเทมในช่อง" : stack.getHoverName().getString();
        graphics.drawString(font, trim(itemName, 116), 323, 53, stack.isEmpty() ? COLOR_MUTED : COLOR_TEAL, false);
        graphics.drawString(font, stack.isEmpty() ? "" : "มีทั้งหมด " + stack.getCount(), 323, 65, COLOR_MUTED, false);

        graphics.drawString(font, "จำนวนขาย", 281, 81, COLOR_TEXT, false);
        drawCentered(graphics, Integer.toString(quantity), 374, 404, 81, COLOR_TEXT);
        graphics.drawString(font, "ราคาต่อชิ้น", 281, 104, COLOR_TEXT, false);
        graphics.drawString(font, "ระยะเวลาขาย", 281, 127, COLOR_TEXT, false);

        long gross = safeMultiply(validPrice(), quantity);
        long tax = MarketMath.percentage(gross, menu.salesTaxRateBps());
        long net = gross < 0 || tax < 0 ? -1L : Math.max(0L, gross - tax - menu.listingFeeSilver());
        int summaryY = 153;
        drawAmount(graphics, "ราคารวม", gross, summaryY, COLOR_TEXT);
        drawAmount(graphics, "ค่าลงขาย", menu.listingFeeSilver(), summaryY + 13, COLOR_MUTED);
        drawAmount(graphics, "ภาษีเมื่อขายสำเร็จ " + formatRate(menu.salesTaxRateBps()), tax,
                summaryY + 26, COLOR_MUTED);
        drawAmount(graphics, "ยอดสุทธิ", net, summaryY + 45, COLOR_GOLD);
    }

    private void drawAmount(GuiGraphics graphics, String label, long value, int y, int color) {
        graphics.drawString(font, label, 281, y, color, false);
        String amount = value < 0 ? "-" : String.format("%,d %s", value, SILVER_ICON);
        Component amountLabel = NeoBitmapFonts.text(amount);
        graphics.drawString(font, amountLabel, imageWidth - 18 - font.width(amountLabel), y, color, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void changeQuantity(int change) {
        int maximum = Math.max(1, menu.getSlot(0).getItem().getCount());
        quantity = Math.max(1, Math.min(maximum, quantity + change));
    }

    private void cycleDuration() {
        durationIndex = (durationIndex + 1) % DURATIONS.length;
        durationButton.setMessage(Component.literal(DURATION_LABELS[durationIndex]));
    }

    private void confirmListing() {
        ItemStack stack = menu.getSlot(0).getItem();
        long price = validPrice();
        if (stack.isEmpty() || price <= 0 || quantity <= 0) return;
        Component message = Component.literal(stack.getHoverName().getString() + " x" + quantity
                + "\nราคาต่อชิ้น " + String.format("%,d", price) + " Silver"
                + "\nระยะเวลา " + DURATION_LABELS[durationIndex]);
        NeoPopupScreen.showChoice(Component.literal("ยืนยันวางขาย"), message,
                Component.literal("วางขาย"), () -> submit(price),
                Component.literal("กลับ"), () -> {});
    }

    private void submit(long price) {
        submitting = true;
        PacketDistributor.sendToServer(new MarketSellSubmitPayload(
                menu.containerId, quantity, price, DURATIONS[durationIndex]));
    }

    public void receiveAction(MarketActionResultPayload payload) {
        submitting = false;
        if (payload.success()) quantity = 1;
        NeoPopupScreen.showAlert(
                Component.literal(payload.success() ? "วางขายสำเร็จ" : "ไม่สามารถวางขายได้"),
                Component.literal(payload.message()), () -> {});
    }

    private void backToManagement() {
        super.onClose();
        Minecraft.getInstance().setScreen(new MarketManagementScreen());
    }

    private long validPrice() {
        if (priceInput == null || priceInput.getValue().isEmpty()) return 0L;
        try {
            return Long.parseLong(priceInput.getValue());
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    private int closeButtonX() {
        int outside = leftPos + imageWidth + 3;
        return outside + 24 <= width - 2 ? outside : leftPos + imageWidth - 25;
    }

    private int closeButtonY() {
        return closeButtonX() > leftPos + imageWidth ? topPos + 11 : Math.max(2, topPos - 20);
    }

    private String trim(String value, int width) {
        return font.plainSubstrByWidth(value, Math.max(1, width));
    }

    private void drawCentered(GuiGraphics graphics, String value, int start, int end, int y, int color) {
        String clipped = trim(value, end - start - 4);
        graphics.drawString(font, clipped, start + (end - start - font.width(clipped)) / 2, y, color, false);
    }

    private static long safeMultiply(long value, int multiplier) {
        if (value <= 0 || multiplier <= 0 || value > Long.MAX_VALUE / multiplier) return -1L;
        return value * multiplier;
    }

    private static String formatRate(int basisPoints) {
        return basisPoints % 100 == 0 ? (basisPoints / 100) + "%" : String.format("%.2f%%", basisPoints / 100.0);
    }
}
