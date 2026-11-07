package com.ambition.neoworld.client.gui.shop;

import com.ambition.neoworld.client.gui.common.NeoBitmapFonts;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.state.ClientEconomyData;
import com.ambition.neoworld.shop.ShopMenu;
import com.ambition.neoworld.shop.ShopView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Shared NeoWorld PNG widgets around native, server-owned inventory slots. */
public final class ShopScreen extends AbstractContainerScreen<ShopMenu> {
    private static final int DETAIL_X = 205, DETAIL_WIDTH = 77;
    
    private NeoButton buy, sell, previous, next;
    private NeoButton decrease, increase;
    private final NeoButton[] tabs = new NeoButton[3];
    public ShopScreen(ShopMenu menu, Inventory inv, Component title) {
        super(menu, inv, title); imageWidth = ShopMenu.WIDTH; imageHeight = ShopMenu.HEIGHT;
    }
    @Override protected void init() {
        super.init();
        iconButton("close", "ปิดร้านค้า", 282, 17, 11, 11, ignored -> onClose());
        String[] tabLabels = {"ทั้งหมด", "ร้านขาย", "ร้านรับซื้อ"};
        for (int i = 0; i < tabs.length; i++) {
            final int action = 12 + i;
            tabs[i] = button(tabLabels[i], 18+i*58, 29, 56, 15, ignored -> send(action))
                    .frameRenderer(ShopTextures::tab);
        }
        decrease = button("−", DETAIL_X, 127, 12, 12, ignored -> send(10));
        increase = button("+", DETAIL_X + DETAIL_WIDTH - 12, 127, 12, 12, ignored -> send(11));
        decrease.setTooltip(Tooltip.create(Component.literal("ลดจำนวน 1 ชิ้น")));
        increase.setTooltip(Tooltip.create(Component.literal("เพิ่มจำนวน 1 ชิ้น")));
        buy = button("ซื้อ", DETAIL_X + 3, 179, DETAIL_WIDTH - 6, 14, ignored -> send(6)).style(NeoButton.Style.PRIMARY).textOffsetY(2);
        sell = button("ขาย", DETAIL_X + 3, 198, DETAIL_WIDTH - 6, 14, ignored -> send(7)).style(NeoButton.Style.DESTRUCTIVE).textOffsetY(2);
        previous = iconButton("left", "หน้าก่อนหน้า", 65, 116, 18, 13, ignored -> send(1));
        next = iconButton("right", "หน้าถัดไป", 127, 116, 18, 13, ignored -> send(2));
        updateControls();
    }
    private NeoButton button(String text, int x, int y, int w, int h, Button.OnPress action) {
        return addRenderableWidget(new NeoButton(Button.builder(Component.literal(text), action)
                .bounds(leftPos + x, topPos + y, w, h))
                .frameRenderer((g,b) -> ShopTextures.button(g,b,false)).textScrolling(false));
    }
    private NeoButton iconButton(String icon, String label, int x, int y, int w, int h, Button.OnPress action) {
        NeoButton control = addRenderableWidget(new NeoButton(Button.builder(Component.empty(), action)
                .bounds(leftPos+x, topPos+y, w, h)
                .createNarration(ignored -> Component.literal(label)))
                .frameRenderer((g,b) -> ShopTextures.iconButton(g,b,icon)));
        control.setTooltip(Tooltip.create(Component.literal(label)));
        return control;
    }
    private void send(int action) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); }
    @Override protected void containerTick() {
        super.containerTick();
        updateControls();
    }
    private void updateControls() {
        boolean selected = menu.inShop() && menu.selected() >= 0 && menu.getCarried().isEmpty();
        long buyTotal = total(menu.buyPrice()), sellTotal = total(menu.sellPrice());
        boolean currencyEnabled = menu.currency().enabled();
        buy.active = selected && currencyEnabled && buyTotal > 0 && balance() >= buyTotal;
        sell.active = selected && currencyEnabled && sellTotal > 0;
        buy.setMessage(Component.literal(menu.armed() == 6 ? "ยืนยัน" : "ซื้อ " + (buyTotal > 0 ? ClientEconomyData.formatCompact(buyTotal) : "—")));
        sell.setMessage(Component.literal(menu.armed() == 7 ? "ยืนยัน" : "ขาย " + (sellTotal > 0 ? ClientEconomyData.formatCompact(sellTotal) : "—")));
        buy.textScrolling(font.width(buy.getMessage()) > buy.getWidth()-2);
        sell.textScrolling(font.width(sell.getMessage()) > sell.getWidth()-2);
        if (!currencyEnabled) {
            buy.setTooltip(Tooltip.create(Component.literal("สกุลเงินนี้ไม่รองรับ")));
            sell.setTooltip(Tooltip.create(Component.literal("สกุลเงินนี้ไม่รองรับ")));
        } else {
            buy.setTooltip(Tooltip.create(Component.literal(buyTotal > 0 ? "ยอดรวม " + buyTotal + " " + menu.currency().label() : "ร้านไม่จำหน่าย หรือยอดรวมเกินขอบเขต")));
            sell.setTooltip(Tooltip.create(Component.literal(sellTotal > 0 ? "ยอดรวม " + sellTotal + " " + menu.currency().label() + " (รับเฉพาะไอเทมและข้อมูลที่ตรงกัน)" : "ร้านไม่รับซื้อ หรือยอดรวมเกินขอบเขต")));
        }
        previous.active = menu.page() > 0; next.active = menu.page() + 1 < menu.pages();
        buy.visible = menu.inShop() && menu.view() != ShopView.STORE_BUYS && menu.buyPrice() > 0;
        sell.visible = menu.inShop() && menu.view() != ShopView.STORE_SELLS && menu.sellPrice() > 0;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].visible = menu.inShop();
            tabs[i].active = menu.getCarried().isEmpty();
            tabs[i].selected(menu.view().ordinal() == i);
        }
        decrease.visible = increase.visible = menu.inShop();
        decrease.active = selected && menu.quantity() > 1;
        increase.active = selected && menu.quantity() < 64;
    }
    @Override protected void renderBg(GuiGraphics g, float tick, int mx, int my) {
        ShopTextures.frame(g,leftPos,topPos,imageWidth,imageHeight);
        for (int i=0;i<menu.slots.size();i++) {
            var slot=menu.getSlot(i);
            int state=i < ShopMenu.PAGE_SIZE && i == menu.selected() ? 2 : isHovering(slot.x,slot.y,16,16,mx,my) ? 1 : 0;
            ShopTextures.slot(g,leftPos+slot.x-1,topPos+slot.y-1,state,18);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        String page = (menu.page()+1) + " / " + Math.max(1, menu.pages());
        drawFit(g,title,112,14,76,0xFFF0CE,true);
        drawFit(g,Component.literal("สินค้า"),23,49,38,0xFFF0CE,true);
        drawFit(g,Component.literal("คลังไอเทม"),23,135,54,0xFFF0CE,true);
        g.drawString(font, page, 106 - font.width(page)/2, 119, 0x40220F, false);
        if (menu.inShop() && menu.getSlot(0).getItem().isEmpty()) {
            int noticeWidth = 132, noticeHeight = 24;
            int noticeX = ShopMenu.PRODUCT_X - 1 + (9 * ShopMenu.SLOT_PITCH - noticeWidth) / 2;
            int noticeY = ShopMenu.PRODUCT_Y - 1 + (3 * ShopMenu.SLOT_PITCH - noticeHeight) / 2;
            ShopTextures.emptyNotice(g, noticeX, noticeY, noticeWidth, noticeHeight);
            drawFit(g,Component.literal("ไม่มีสินค้าในแท็บนี้"),noticeX+10,
                    noticeY+(noticeHeight-font.lineHeight)/2,noticeWidth-20,0xFFF0CE,true);
        } else if (menu.getSlot(0).getItem().isEmpty()) {
            drawFit(g,Component.literal("ยังไม่มีร้านค้า"),25,80,155,0x65472D,true);
        }
        ItemStack item = selectedItem();
        if (!menu.inShop()) {
            drawFit(g,Component.literal("เลือกร้านค้า"),DETAIL_X,51,DETAIL_WIDTH,0x40220F,true);
            g.drawWordWrap(font, Component.literal("คลิกไอคอนร้านเพื่อเปิด"), DETAIL_X,65,DETAIL_WIDTH,0x65472D);
            return;
        }
        drawFit(g,item.isEmpty() ? Component.literal("เลือกสินค้า") : item.getHoverName(),DETAIL_X,51,DETAIL_WIDTH,0x40220F,true);
        if (!item.isEmpty()) {
            ShopTextures.slot(g,DETAIL_X+(DETAIL_WIDTH-24)/2,63,0,24);
            g.renderItem(item,DETAIL_X+(DETAIL_WIDTH-16)/2,67);
            drawFit(g,Component.literal("ราคาต่อชิ้น"),DETAIL_X,89,DETAIL_WIDTH,0x65472D,false);
            if (menu.view() != ShopView.STORE_BUYS) unitPrice(g,"ซื้อ",menu.buyPrice(),100);
            if (menu.view() != ShopView.STORE_SELLS) unitPrice(g,"ขาย",menu.sellPrice(),112);
            drawFit(g,Component.literal("ยอดรวม"),DETAIL_X,146,DETAIL_WIDTH,0x65472D,false);
            if (buy.visible) unitPrice(g,"ซื้อ",total(menu.buyPrice()),157);
            if (sell.visible) unitPrice(g,"ขาย",total(menu.sellPrice()),169);
        }
        drawFit(g,Component.literal("จำนวน " + menu.quantity()),DETAIL_X+20,129,DETAIL_WIDTH-40,0x40220F,true);
    }
    private ItemStack selectedItem() {
        return menu.selected() >= 0 && menu.selected() < ShopMenu.PAGE_SIZE ? menu.getSlot(menu.selected()).getItem() : ItemStack.EMPTY;
    }
    private void unitPrice(GuiGraphics g, String label, long price, int y) {
        Component text = Component.literal(label + " " + (price > 0 ? ClientEconomyData.formatCompact(price) + " " : "—"))
                .withStyle(style -> style.withColor(0x40220F));
        if (price > 0) {
            text = text.copy().append(NeoBitmapFonts.text(menu.currency().glyph())
                    .withStyle(style -> style.withColor(0xFFFFFF)));
        }
        g.setColor(1F, 1F, 1F, 1F);
        drawFit(g,text,DETAIL_X,y,DETAIL_WIDTH,0x40220F,false);
    }
    private void drawFit(GuiGraphics g, Component text, int x, int y, int width, int color, boolean centered) {
        float scale = Math.min(1F,width/(float)Math.max(1,font.width(text)));
        scale = Math.max(0.7F,scale);
        var trimmed = Language.getInstance().getVisualOrder(font.substrByWidth(text,(int)(width/scale)));
        g.pose().pushPose();
        g.pose().translate(x + (centered ? (width-font.width(trimmed)*scale)/2F : 0),y,0);
        g.pose().scale(scale,scale,1);
        g.drawString(font,trimmed,0,0,color,false);
        g.pose().popPose();
    }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float tick) { renderBg(g,tick,x,y); }
    @Override public void render(GuiGraphics g, int x, int y, float tick) {
        g.fill(0,0,width,height,0x9A000000);
        super.render(g,x,y,tick); renderTooltip(g,x,y);
        if (!selectedItem().isEmpty() && x >= leftPos + DETAIL_X && x < leftPos + DETAIL_X + DETAIL_WIDTH
                && y >= topPos + 49 && y < topPos + 88) g.renderTooltip(font, selectedItem(), x, y);
    }
    private long total(long price) {
        try { return com.ambition.neoworld.shop.ShopRules.total(price, menu.quantity()); }
        catch (RuntimeException e) { return 0; }
    }
    private long balance() {
        return switch (menu.currency()) {
            case SILVER -> ClientEconomyData.getSilver();
            case DIAMOND -> ClientEconomyData.getDiamond();
        };
    }
}
