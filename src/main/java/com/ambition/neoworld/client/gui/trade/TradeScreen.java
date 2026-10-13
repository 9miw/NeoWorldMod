package com.ambition.neoworld.client.gui.trade;

import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.network.TradeActionPayload.Action;
import com.ambition.neoworld.network.TradeStatePayload;
import com.ambition.neoworld.trade.menu.TradeMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/** Minecraft owns slot rendering, cursor, drag, split, hotkeys and container click synchronization. */
public final class TradeScreen extends AbstractContainerScreen<TradeMenu> {
    private static final ResourceLocation VANILLA_SLOTS = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final ResourceLocation PLAQUE = ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/common/buttons.png");
    private TradeStatePayload state;
    private EditBox silver, count;
    private NeoButton confirm, add, setMoney;
    private int selectedSlot = -1;

    public TradeScreen(TradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = TradeMenu.WIDTH; imageHeight = TradeMenu.HEIGHT;
        TradeScreenController.attach(this);
    }
    public void update(TradeStatePayload next) {
        boolean moneyChanged = state == null || state.mySilver() != next.mySilver();
        state = next;
        menu.updateOffers(next.mine(), next.theirs());
        if (silver != null && moneyChanged) silver.setValue(Long.toString(next.mySilver()));
        if (confirm != null) {
            confirm.setMessage(Component.literal(next.myConfirmed() ? "ถอนยืนยัน" : "ยืนยัน"));
            confirm.selected(next.myConfirmed());
        }
    }
    @Override protected void init() {
        super.init();
        silver = new EditBox(font, leftPos + 12, topPos + 90, 88, 16, Component.literal("Silver ที่เสนอ"));
        silver.setMaxLength(19); silver.setFilter(v -> v.matches("[0-9]*"));
        silver.setValue(state == null ? "0" : Long.toString(state.mySilver())); addRenderableWidget(silver);
        setMoney = button("ตั้งเงิน", 104, 90, 42, () -> {
            try { TradeScreenController.send(Action.SILVER, -1, 0, Long.parseLong(silver.getValue())); }
            catch (NumberFormatException ignored) { silver.setValue(state == null ? "0" : Long.toString(state.mySilver())); }
        });
        addRenderableWidget(setMoney);
        count = new EditBox(font, leftPos + 10, topPos + 198, 26, 16, Component.literal("จำนวนไอเทม"));
        count.setMaxLength(2); count.setFilter(v -> v.matches("[0-9]*")); count.setValue("1"); addRenderableWidget(count);
        add = button("เพิ่ม", 40, 198, 42, () -> {
            try { TradeScreenController.send(Action.OFFER, selectedSlot, Integer.parseInt(count.getValue()), 0); }
            catch (NumberFormatException ignored) { count.setValue("1"); }
        });
        add.setTooltip(Tooltip.create(Component.literal("Ctrl+คลิกเพื่อเลือกช่องและระบุจำนวน; Shift+คลิกเสนอทั้งกอง; คลิกของที่เสนอเพื่อลบ")));
        addRenderableWidget(add);
        confirm = button(state != null && state.myConfirmed() ? "ถอนยืนยัน" : "ยืนยัน", 164, 198, 72,
                () -> TradeScreenController.send(state != null && state.myConfirmed() ? Action.UNCONFIRM : Action.CONFIRM, -1, 0, 0));
        confirm.style(NeoButton.Style.PRIMARY).selected(state != null && state.myConfirmed()); addRenderableWidget(confirm);
        addRenderableWidget(button("ยกเลิก", 240, 198, 62, this::onClose).style(NeoButton.Style.DESTRUCTIVE));
        NeoButton close = button("X", 286, 4, 18, this::onClose).style(NeoButton.Style.DESTRUCTIVE);
        close.setTooltip(Tooltip.create(Component.literal("ปิดและยกเลิกการแลกเปลี่ยน"))); addRenderableWidget(close);
        containerTick();
    }
    private NeoButton button(String label, int x, int y, int width, Runnable action) {
        return new NeoButton(Button.builder(Component.literal(label), ignored -> action.run()).bounds(leftPos + x, topPos + y, width, 16));
    }
    @Override protected void containerTick() {
        boolean ready = state != null && menu.getCarried().isEmpty();
        confirm.active = ready && (!state.mine().isEmpty() || !state.theirs().isEmpty() || state.mySilver() > 0 || state.theirSilver() > 0);
        add.active = ready && selectedSlot >= 0;
        setMoney.active = ready;
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, NeoGuiStyle.COLOR_PANEL);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, NeoGuiStyle.COLOR_FRAME_LIGHT);
        g.renderOutline(leftPos + 2, topPos + 2, imageWidth - 4, imageHeight - 4, NeoGuiStyle.COLOR_FRAME);
        RenderSystem.enableBlend();
        g.blit(PLAQUE, leftPos + imageWidth / 2 - 56, topPos - 3, 112, 20, 28, 441, 350, 144, 1536, 1024);
        for (Slot slot : menu.slots) {
            // Reuse the game's chest slot background at native 18px pitch; item rendering is inherited.
            g.blit(VANILLA_SLOTS, leftPos + slot.x - 1, topPos + slot.y - 1, 7, 17, 18, 18);
            if (slot.index >= TradeMenu.PREVIEW_SLOTS && (slot.getContainerSlot() == selectedSlot
                    || state != null && state.mine().stream().anyMatch(o -> o.slot() == slot.getContainerSlot()))) {
                g.renderOutline(leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18, NeoGuiStyle.COLOR_GOLD);
            }
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font, title, imageWidth / 2, 4, NeoGuiStyle.COLOR_ACCENT);
        label(g, "คุณ", 12, 20, 134, NeoGuiStyle.COLOR_TEXT);
        label(g, state == null ? "กำลังโหลด..." : state.partner(), 168, 20, 132, NeoGuiStyle.COLOR_TEXT);
        if (state == null) return;
        label(g, "Silver " + state.theirSilver(), 168, 94, 132, NeoGuiStyle.COLOR_GOLD);
        label(g, state.myConfirmed() ? "ยืนยันแล้ว" : "ยังไม่ยืนยัน", 12, 109, 134, NeoGuiStyle.COLOR_TEXT_MUTED);
        label(g, state.theirConfirmed() ? "ยืนยันแล้ว" : "ยังไม่ยืนยัน", 168, 109, 132, NeoGuiStyle.COLOR_TEXT_MUTED);
        label(g, "Silver คงเหลือ " + state.balance(), 10, 218, 292, NeoGuiStyle.COLOR_GOLD);
    }
    private void label(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        g.drawString(font, font.plainSubstrByWidth(text, maxWidth), x, y, color, false);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
    @Override protected void slotClicked(Slot slot, int id, int button, ClickType type) {
        if (slot != null && slot.index >= TradeMenu.PREVIEW_SLOTS && type == ClickType.PICKUP && hasControlDown()) {
            selectedSlot = slot.getContainerSlot();
            if (slot.hasItem()) count.setValue(Integer.toString(slot.getItem().getCount()));
            return;
        }
        super.slotClicked(slot, id, button, type);
    }
    @Override public void onClose() { TradeScreenController.cancel(); super.onClose(); }
}
