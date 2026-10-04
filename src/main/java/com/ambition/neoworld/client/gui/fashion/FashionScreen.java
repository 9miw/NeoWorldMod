package com.ambition.neoworld.client.gui.fashion;

import java.util.ArrayList;
import java.util.List;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.ClientFashionData;
import com.ambition.neoworld.data.fashion.FashionSlot;
import com.ambition.neoworld.network.FashionActionResultPayload;
import com.ambition.neoworld.network.FashionClearPayload;
import com.ambition.neoworld.network.FashionEquipPayload;
import com.ambition.neoworld.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FashionScreen extends Screen {
    private static final ResourceLocation FRAME_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/fashion/fashion_frame.png");
    private static final ResourceLocation WIDGET_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/fashion/fashion_widgets.png");
    private static final int FRAME_TEXTURE_WIDTH = 430;
    private static final int FRAME_TEXTURE_HEIGHT = 236;
    private static final int WIDGET_TEXTURE_WIDTH = 256;
    private static final int WIDGET_TEXTURE_HEIGHT = 128;

    private static final int COLOR_PANEL = 0xF51A2024;
    private static final int COLOR_PANEL_DARK = 0xF00E1215;
    private static final int COLOR_FRAME = 0xFF4C565E;
    private static final int COLOR_FRAME_LIGHT = 0xFF87919A;
    private static final int COLOR_SLOT = 0xFF252B2F;
    private static final int COLOR_SLOT_HOVER = 0xFF38464D;
    private static final int COLOR_TEAL = 0xFF2FB5A7;
    private static final int COLOR_TEAL_DARK = 0xFF176B65;
    private static final int COLOR_GOLD = 0xFFF2C14E;
    private static final int COLOR_MUTED = 0xFFABB4BA;

    private static final int GRID_COLUMNS = 6;
    private static final int GRID_ROWS = 4;
    private static final int SLOT_SIZE = 22;

    private final List<InventoryEntry> fashionItems = new ArrayList<>();
    private EditBox searchBox;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int leftX;
    private int centerX;
    private int rightX;
    private int contentY;
    private int selectedInventorySlot = -1;
    private FashionSlot selectedSlot = FashionSlot.HEAD;
    private boolean hideArmor;
    private boolean showFashion = true;
    private String status = "";
    private int statusColor = 0xFFE3E7EA;
    private int statusTicks;

    public FashionScreen() {
        super(Component.literal("Fashion Dressing Room"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(430, width - 10);
        panelHeight = Math.min(236, height - 10);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        contentY = panelY + 34;
        leftX = panelX + 8;
        centerX = panelX + 130;
        rightX = panelX + 282;

        searchBox = new EditBox(font, rightX + 9, contentY + 25, 110, 14, Component.literal("Search"));
        searchBox.setMaxLength(48);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFE7ECEF);
        searchBox.setHint(Component.literal("Search...").withStyle(ChatFormatting.GRAY));
        searchBox.setResponder(value -> refreshFashionItems());
        addRenderableWidget(searchBox);
        refreshFashionItems();
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0) statusTicks--;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        refreshFashionItems();
        renderFrame(graphics);
        renderTitle(graphics);
        renderClose(graphics, mouseX, mouseY);
        renderEquippedSlots(graphics, mouseX, mouseY);
        renderPreview(graphics, mouseX, mouseY);
        renderOwnedFashion(graphics, mouseX, mouseY);
        renderActions(graphics, mouseX, mouseY);
        for (var renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderFrame(GuiGraphics graphics) {
        graphics.blit(FRAME_TEXTURE, panelX, panelY, panelWidth, panelHeight, 0, 0,
                FRAME_TEXTURE_WIDTH, FRAME_TEXTURE_HEIGHT, FRAME_TEXTURE_WIDTH, FRAME_TEXTURE_HEIGHT);
    }

    private void renderTitle(GuiGraphics graphics) {
        int plateWidth = 170;
        int x = panelX + (panelWidth - plateWidth) / 2;
        drawCentered(graphics, "Fashion Dressing Room", x, plateWidth, panelY + 12, 0xFFF4F6F7);
        graphics.drawString(font, "+", x + 7, panelY + 12, COLOR_TEAL, false);
        graphics.drawString(font, "+", x + plateWidth - 12, panelY + 12, COLOR_TEAL, false);
    }

    private void renderClose(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelX + panelWidth - 24;
        int y = panelY + 7;
        boolean hovered = inside(mouseX, mouseY, x, y, 16, 16);
        blitWidget(graphics, x, y, 16, 16, hovered ? 134 : 112, 84, 18, 18);
        drawCentered(graphics, "X", x, 16, y + 5, 0xFFFFFFFF);
    }

    private void renderEquippedSlots(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = 112;
        graphics.drawString(font, "Equipped Slots", leftX + 7, contentY + 8, 0xFFF4F6F7, false);
        int y = contentY + 24;
        for (FashionSlot slot : FashionSlot.values()) {
            boolean selected = slot == selectedSlot;
            boolean hovered = inside(mouseX, mouseY, leftX + 6, y, width - 12, 25);
            if (selected) {
                blitWidget(graphics, leftX + 6, y, width - 12, 25, 0, 28, 96, 24);
            } else if (hovered) {
                blitWidget(graphics, leftX + 6, y, width - 12, 25, 100, 0, 96, 24);
            } else {
                blitWidget(graphics, leftX + 6, y, width - 12, 25, 0, 0, 96, 24);
            }
            ItemStack equipped = localEquipped(slot);
            graphics.renderItem(iconFor(slot, equipped), leftX + 10, y + 5);
            graphics.drawString(font, label(slot), leftX + 30, y + 5, selected ? COLOR_GOLD : 0xFFE6EAED, false);
            graphics.drawString(font, trim(equipped.isEmpty() ? "Empty" : equipped.getHoverName().getString(), 70),
                    leftX + 30, y + 15, equipped.isEmpty() ? 0xFF737D84 : COLOR_MUTED, false);
            y += 28;
        }
    }

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = 142;
        int height = panelHeight - 75;
        drawCentered(graphics, "PLAYER PREVIEW", centerX, width, contentY + 10, COLOR_MUTED);
        int bodyX = centerX + width / 2;
        int bodyY = contentY + 58;
        graphics.fill(bodyX - 13, bodyY - 18, bodyX + 13, bodyY + 20, 0xFF2D3840);
        graphics.fill(bodyX - 9, bodyY - 42, bodyX + 9, bodyY - 22, 0xFFCFAE8A);
        graphics.fill(bodyX - 22, bodyY - 12, bodyX - 14, bodyY + 19, 0xFF27323A);
        graphics.fill(bodyX + 14, bodyY - 12, bodyX + 22, bodyY + 19, 0xFF27323A);
        graphics.fill(bodyX - 12, bodyY + 21, bodyX - 3, bodyY + 52, 0xFF26313A);
        graphics.fill(bodyX + 3, bodyY + 21, bodyX + 12, bodyY + 52, 0xFF26313A);
        ItemStack head = localEquipped(FashionSlot.HEAD);
        if (!head.isEmpty()) graphics.renderItem(head, bodyX - 8, bodyY - 56);
        ItemStack back = localEquipped(FashionSlot.BACK);
        if (!back.isEmpty()) graphics.renderItem(back, bodyX - 36, bodyY - 14);

        int toggleY = contentY + height + 8;
        renderToggle(graphics, centerX, toggleY, 66, "Hide Armor", hideArmor, mouseX, mouseY);
        renderToggle(graphics, centerX + 74, toggleY, 68, "Show Fashion", showFashion, mouseX, mouseY);
    }

    private void renderOwnedFashion(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = panelX + panelWidth - rightX - 8;
        graphics.drawString(font, "Owned Fashion", rightX + 7, contentY + 8, 0xFFF4F6F7, false);
        blitWidget(graphics, rightX + 6, contentY + 22, width - 12, 21, 0, 0, 96, 24);
        graphics.renderItem(new ItemStack(Items.SPYGLASS), rightX + 7, contentY + 24);

        int tabY = contentY + 48;
        int tabX = rightX + 7;
        for (FashionSlot slot : FashionSlot.values()) {
            int tabWidth = 23;
            boolean selected = slot == selectedSlot;
            boolean hovered = inside(mouseX, mouseY, tabX, tabY, tabWidth, 17);
            if (selected) {
                blitWidget(graphics, tabX, tabY, tabWidth, 17, 100, 28, 96, 24);
            } else if (hovered) {
                blitWidget(graphics, tabX, tabY, tabWidth, 17, 100, 0, 96, 24);
            } else {
                blitWidget(graphics, tabX, tabY, tabWidth, 17, 0, 0, 96, 24);
            }
            drawCentered(graphics, shortLabel(slot), tabX, tabWidth, tabY + 5, 0xFFE8ECEE);
            tabX += tabWidth + 3;
        }

        int gridX = rightX + 8;
        int gridY = contentY + 72;
        int visible = Math.min(GRID_COLUMNS * GRID_ROWS, fashionItems.size());
        for (int index = 0; index < GRID_COLUMNS * GRID_ROWS; index++) {
            int column = index % GRID_COLUMNS;
            int row = index / GRID_COLUMNS;
            int x = gridX + column * SLOT_SIZE;
            int y = gridY + row * SLOT_SIZE;
            InventoryEntry entry = index < visible ? fashionItems.get(index) : null;
            boolean selected = entry != null && entry.inventorySlot == selectedInventorySlot;
            boolean hovered = inside(mouseX, mouseY, x, y, 20, 20);
            if (selected) {
                blitWidget(graphics, x, y, 20, 20, 56, 84, 24, 24);
            } else if (hovered) {
                blitWidget(graphics, x, y, 20, 20, 28, 84, 24, 24);
            } else {
                blitWidget(graphics, x, y, 20, 20, 0, 84, 24, 24);
            }
            if (entry != null) graphics.renderItem(entry.stack, x + 2, y + 2);
        }
    }

    private void renderActions(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = panelY + panelHeight - 30;
        renderButton(graphics, rightX, y, 82, 20, "Apply", mouseX, mouseY, selectedInventorySlot >= 0, true);
        renderButton(graphics, rightX + 88, y, 58, 20, "Clear", mouseX, mouseY, true, false);
        if (statusTicks > 0 && !status.isEmpty()) {
            graphics.drawString(font, trim(status, 170), leftX, panelY + panelHeight - 9, statusColor, false);
        }
    }

    private void renderButton(GuiGraphics graphics, int x, int y, int width, int height, String label,
                              int mouseX, int mouseY, boolean active, boolean primary) {
        boolean hovered = active && inside(mouseX, mouseY, x, y, width, height);
        if (!active) {
            blitWidget(graphics, x, y, width, height, 0, 56, 96, 24);
        } else if (primary) {
            blitWidget(graphics, x, y, width, height, hovered ? 100 : 100, hovered ? 56 : 28, 96, 24);
        } else {
            blitWidget(graphics, x, y, width, height, hovered ? 100 : 0, hovered ? 0 : 56, 96, 24);
        }
        drawCentered(graphics, label, x, width, y + 6, active ? 0xFFFFFFFF : 0xFF79838A);
    }

    private void renderToggle(GuiGraphics graphics, int x, int y, int width, String label, boolean on,
                              int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, 18);
        if (on) {
            blitWidget(graphics, x, y, width, 18, 100, 28, 96, 24);
        } else if (hovered) {
            blitWidget(graphics, x, y, width, 18, 100, 0, 96, 24);
        } else {
            blitWidget(graphics, x, y, width, 18, 0, 0, 96, 24);
        }
        graphics.drawString(font, trim(label, width - 21), x + 4, y + 6, 0xFFE4E8EA, false);
        drawCentered(graphics, on ? "ON" : "OFF", x + width - 18, 15, y + 6, 0xFFFFFFFF);
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        InventoryEntry hovered = inventoryEntryAt(mouseX, mouseY);
        if (hovered != null) {
            graphics.renderTooltip(font, hovered.stack, mouseX, mouseY);
            return;
        }
        for (FashionSlot slot : FashionSlot.values()) {
            if (inside(mouseX, mouseY, leftX + 6, contentY + 24 + slot.ordinal() * 28, 100, 25)) {
                ItemStack equipped = localEquipped(slot);
                graphics.renderTooltip(font, equipped.isEmpty() ? Component.literal(label(slot)) : equipped.getHoverName(), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inside(mouseX, mouseY, panelX + panelWidth - 24, panelY + 7, 16, 16)) {
            onClose();
            return true;
        }
        for (FashionSlot slot : FashionSlot.values()) {
            if (inside(mouseX, mouseY, leftX + 6, contentY + 24 + slot.ordinal() * 28, 100, 25)) {
                selectedSlot = slot;
                selectedInventorySlot = -1;
                refreshFashionItems();
                return true;
            }
        }
        int tabX = rightX + 7;
        int tabY = contentY + 48;
        for (FashionSlot slot : FashionSlot.values()) {
            if (inside(mouseX, mouseY, tabX, tabY, 23, 17)) {
                selectedSlot = slot;
                selectedInventorySlot = -1;
                refreshFashionItems();
                return true;
            }
            tabX += 26;
        }
        InventoryEntry entry = inventoryEntryAt(mouseX, mouseY);
        if (entry != null) {
            selectedInventorySlot = entry.inventorySlot;
            return true;
        }
        int actionY = panelY + panelHeight - 30;
        if (selectedInventorySlot >= 0 && inside(mouseX, mouseY, rightX, actionY, 82, 20)) {
            PacketDistributor.sendToServer(new FashionEquipPayload(selectedSlot, selectedInventorySlot));
            selectedInventorySlot = -1;
            return true;
        }
        if (inside(mouseX, mouseY, rightX + 88, actionY, 58, 20)) {
            PacketDistributor.sendToServer(new FashionClearPayload(selectedSlot));
            selectedInventorySlot = -1;
            return true;
        }
        int toggleY = contentY + panelHeight - 75 + 8;
        if (inside(mouseX, mouseY, centerX, toggleY, 66, 18)) {
            hideArmor = !hideArmor;
            return true;
        }
        if (inside(mouseX, mouseY, centerX + 74, toggleY, 68, 18)) {
            showFashion = !showFashion;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void receiveAction(FashionActionResultPayload payload) {
        showStatus(payload.message(), payload.success());
        refreshFashionItems();
    }

    private void refreshFashionItems() {
        fashionItems.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        Inventory inventory = minecraft.player.getInventory();
        String search = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(java.util.Locale.ROOT);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            FashionSlot allowed = stack.get(ModDataComponents.FASHION_SLOT.get());
            if (allowed != selectedSlot) continue;
            if (!search.isEmpty() && !stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(search)) continue;
            fashionItems.add(new InventoryEntry(slot, stack.copy()));
        }
    }

    private InventoryEntry inventoryEntryAt(double mouseX, double mouseY) {
        int gridX = rightX + 8;
        int gridY = contentY + 72;
        if (!inside(mouseX, mouseY, gridX, gridY, GRID_COLUMNS * SLOT_SIZE, GRID_ROWS * SLOT_SIZE)) return null;
        int column = ((int) mouseX - gridX) / SLOT_SIZE;
        int row = ((int) mouseY - gridY) / SLOT_SIZE;
        int index = row * GRID_COLUMNS + column;
        return index >= 0 && index < fashionItems.size() ? fashionItems.get(index) : null;
    }

    private ItemStack localEquipped(FashionSlot slot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return ItemStack.EMPTY;
        return ClientFashionData.get(minecraft.player.getId(), slot);
    }

    private ItemStack iconFor(FashionSlot slot, ItemStack equipped) {
        if (!equipped.isEmpty()) return equipped;
        return switch (slot) {
            case HEAD -> new ItemStack(Items.LEATHER_HELMET);
            case FACE -> new ItemStack(Items.SPYGLASS);
            case BACK -> new ItemStack(Items.ELYTRA);
            case BODY -> new ItemStack(Items.LEATHER_CHESTPLATE);
            case LEGS -> new ItemStack(Items.LEATHER_LEGGINGS);
            case FEET -> new ItemStack(Items.LEATHER_BOOTS);
        };
    }

    private void showStatus(String message, boolean success) {
        status = message;
        statusColor = success ? 0xFF68D391 : 0xFFFF7B72;
        statusTicks = 90;
    }

    private String label(FashionSlot slot) {
        return switch (slot) {
            case HEAD -> "Head";
            case FACE -> "Face";
            case BACK -> "Back";
            case BODY -> "Body";
            case LEGS -> "Legs";
            case FEET -> "Feet";
        };
    }

    private String shortLabel(FashionSlot slot) {
        return switch (slot) {
            case HEAD -> "Hd";
            case FACE -> "Fc";
            case BACK -> "Bk";
            case BODY -> "Bd";
            case LEGS -> "Lg";
            case FEET -> "Ft";
        };
    }

    private void drawCentered(GuiGraphics graphics, String text, int x, int width, int y, int color) {
        graphics.drawString(font, text, x + (width - font.width(text)) / 2, y, color, false);
    }

    private void blitWidget(GuiGraphics graphics, int x, int y, int width, int height,
                            int u, int v, int sourceWidth, int sourceHeight) {
        graphics.blit(WIDGET_TEXTURE, x, y, width, height, u, v, sourceWidth, sourceHeight,
                WIDGET_TEXTURE_WIDTH, WIDGET_TEXTURE_HEIGHT);
    }

    private String trim(String text, int maxWidth) {
        return font.plainSubstrByWidth(text, Math.max(1, maxWidth));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record InventoryEntry(int inventorySlot, ItemStack stack) {
    }
}
