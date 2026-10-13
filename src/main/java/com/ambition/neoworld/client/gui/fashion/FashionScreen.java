package com.ambition.neoworld.client.gui.fashion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.client.state.ClientFashionData;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.ambition.neoworld.network.fashion.FashionActionResultPayload;
import com.ambition.neoworld.network.fashion.FashionClearPayload;
import com.ambition.neoworld.network.fashion.FashionEquipPayload;
import com.ambition.neoworld.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FashionScreen extends Screen {
    private static final ResourceLocation CATEGORY_PANEL = ResourceLocation.fromNamespaceAndPath(
            NeoWorld.MODID, "textures/gui/fashion/fashion_category_panel.png");
    private static final ResourceLocation WARDROBE_PANEL = fashionTexture("wardrobe_panel");
    private static final ResourceLocation SEARCH_NORMAL = fashionTexture("search_normal");
    private static final ResourceLocation SEARCH_FOCUS = fashionTexture("search_focus");
    private static final ResourceLocation SEARCH_ICON = fashionTexture("search_icon");
    private static final ResourceLocation DETAIL = fashionTexture("wardrobe_detail");
    private static final int SOURCE_WIDTH = 430;
    private static final int SOURCE_HEIGHT = 236;
    private static final int TEXT = 0xFFE7ECEF;
    private static final int MUTED = 0xFFABB4BA;
    private static final int TEAL = 0xFF2FB5A7;
    private static final int GOLD = 0xFFF2C14E;
    private static final int SLOT_GAP = 2;

    private final Map<FashionSlot, FashionCategoryButton> categories = new EnumMap<>(FashionSlot.class);
    private final List<WardrobeEntry> entries = new ArrayList<>();
    private final List<FashionSlotButton> tiles = new ArrayList<>();
    private EditBox search;
    private FashionActionButton equip;
    private FashionActionButton clear;
    private FashionActionButton previous;
    private FashionActionButton next;
    private FashionSlot category = FashionSlot.HEAD;
    private ResourceLocation selectedId;
    private int page;
    private int x;
    private int y;
    private int panelWidth;
    private int panelHeight;
    private int leftX;
    private int leftWidth;
    private int centerX;
    private int centerWidth;
    private int rightX;
    private int rightWidth;
    private int contentY;
    private int rowY;
    private int rowHeight;
    private int gridX;
    private int gridY;
    private int slotSize;
    private int searchY;
    private int detailY;
    private int columns;
    private int rows;
    private int pagerY;
    private int actionY;
    private int pendingTicks;
    private int statusTicks;
    private int ownedCount;
    private float previewYaw;
    private boolean rotatingPreview;
    private String status = "";
    private int statusColor = TEXT;

    public FashionScreen() {
        super(Component.literal("คลังแฟชั่น"));
    }

    @Override
    protected void init() {
        rotatingPreview = false;
        String query = search == null ? "" : search.getValue();
        panelWidth = Math.min(SOURCE_WIDTH, width - 8);
        panelHeight = Math.min(SOURCE_HEIGHT, height - 8);
        x = (width - panelWidth) / 2;
        y = (height - panelHeight) / 2;
        leftX = sx(14);
        leftWidth = sx(114) - leftX;
        centerX = sx(136);
        centerWidth = sx(238) - centerX;
        rightX = sx(250);
        rightWidth = sx(416) - rightX;
        contentY = sy(39);
        actionY = y + panelHeight - 29;
        rowY = contentY + 17;
        rowHeight = Math.min(27, (actionY + 18 - rowY) / FashionSlot.values().length);
        searchY = contentY + 15;
        gridY = searchY + 17;
        detailY = actionY - 26;
        pagerY = detailY - 19;
        columns = Math.max(1, Math.min(5, (rightWidth + SLOT_GAP) / (22 + SLOT_GAP)));
        int availableGridHeight = pagerY - gridY - 3;
        rows = Math.max(1, Math.min(3, (availableGridHeight + SLOT_GAP) / (22 + SLOT_GAP)));
        slotSize = Math.min(32, Math.min((rightWidth - (columns - 1) * SLOT_GAP) / columns,
                (availableGridHeight - (rows - 1) * SLOT_GAP) / rows));
        gridX = rightX + (rightWidth - gridWidth()) / 2;

        categories.clear();
        tiles.clear();
        for (FashionSlot slot : FashionSlot.values()) {
            categories.put(slot, addRenderableWidget(new FashionCategoryButton(
                    Button.builder(Component.literal(label(slot)), ignored -> selectCategory(slot))
                            .bounds(leftX, rowY + slot.ordinal() * rowHeight, leftWidth, rowHeight - 2)
                            .tooltip(Tooltip.create(Component.literal(label(slot)))))));
        }
        addRenderableWidget(new FashionWindowFrame.CloseButton(
                Button.builder(Component.literal("ปิด"), ignored -> onClose())
                        .bounds(sx(422) - 16, sy(15), 16, 16)
                        .tooltip(Tooltip.create(Component.literal("ปิด")))));
        addRenderableWidget(new NeoButton(Button.builder(NeoGuiIcons.icon(NeoGuiIcons.REFRESH), ignored -> {
                    previewYaw = 0;
                    rotatingPreview = false;
                }).bounds(centerX + centerWidth - 18, contentY - 4, 18, 18)
                .tooltip(Tooltip.create(Component.literal("กลับด้านหน้า")))).textScrolling(false));
        search = new EditBox(font, rightX + 18, searchY + 3, rightWidth - 23, 10,
                Component.literal("ค้นหาแฟชั่น"));
        search.setMaxLength(48);
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setHint(Component.literal("ค้นหา...").withStyle(ChatFormatting.GRAY));
        search.setValue(query);
        search.setResponder(value -> {
            page = 0;
            refreshEntries(true);
        });
        addRenderableWidget(search);
        previous = wardrobeButton("<", rightX + rightWidth / 2 - 43, pagerY, 20, 16, false,
                () -> changePage(-1), "หน้าก่อนหน้า");
        next = wardrobeButton(">", rightX + rightWidth / 2 + 23, pagerY, 20, 16, false,
                () -> changePage(1), "หน้าถัดไป");
        int equipWidth = (rightWidth - 4) * 2 / 3;
        equip = wardrobeButton("ใส่", rightX, actionY, equipWidth, 16, true,
                this::equipSelected, "ใส่แฟชั่นที่เลือก");
        clear = wardrobeButton("ถอด", rightX + equipWidth + 4, actionY, rightWidth - equipWidth - 4, 16,
                false, this::clearEquipped, "ถอดแฟชั่นหมวดนี้");
        refreshEntries(true);
    }

    private FashionActionButton wardrobeButton(String text, int bx, int by, int bw, int bh, boolean primary,
                                               Runnable action, String tooltip) {
        return addRenderableWidget(new FashionActionButton(
                Button.builder(Component.literal(text), ignored -> action.run()).bounds(bx, by, bw, bh)
                        .tooltip(Tooltip.create(Component.literal(tooltip))), primary));
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTicks > 0) statusTicks--;
        if (pendingTicks > 0 && --pendingTicks == 0) showStatus("ไม่ได้รับการตอบกลับ ลองอีกครั้ง", false);
        refreshEntries(false);
    }

    private void refreshEntries(boolean force) {
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        List<WardrobeEntry> refreshed = new ArrayList<>();
        ownedCount = 0;
        ClientFashionData.wardrobe().forEach((id, stack) -> {
            if (stack.get(ModDataComponents.FASHION_SLOT.get()) == category) {
                ownedCount++;
                if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    refreshed.add(new WardrobeEntry(id, stack));
                }
            }
        });
        refreshed.sort(Comparator.comparing(entry -> entry.id.toString()));
        boolean changed = !entries.stream().map(WardrobeEntry::id).toList()
                .equals(refreshed.stream().map(WardrobeEntry::id).toList());
        entries.clear();
        entries.addAll(refreshed);
        if (selectedId != null && entries.stream().noneMatch(entry -> entry.id.equals(selectedId))) selectedId = null;
        if (selectedId == null && query.isEmpty()) {
            ItemStack wearing = equipped(category);
            if (!wearing.isEmpty()) {
                ResourceLocation wearingId = BuiltInRegistries.ITEM.getKey(wearing.getItem());
                if (entries.stream().anyMatch(entry -> entry.id.equals(wearingId))) selectedId = wearingId;
            }
        }
        page = Math.min(page, pageCount() - 1);
        if (force || changed) rebuildTiles();
        updateControls();
    }

    private void rebuildTiles() {
        tiles.forEach(this::removeWidget);
        tiles.clear();
        int end = Math.min(entries.size(), (page + 1) * pageSize());
        for (int i = page * pageSize(); i < end; i++) {
            WardrobeEntry entry = entries.get(i);
            int index = i - page * pageSize();
            tiles.add(addRenderableWidget(new FashionSlotButton(
                    Button.builder(entry.stack.getHoverName(), ignored -> { selectedId = entry.id; updateControls(); })
                            .bounds(gridX + index % columns * slotPitch(), gridY + index / columns * slotPitch(),
                                    slotSize, slotSize))));
        }
    }

    private void updateControls() {
        ItemStack equipped = equipped(category);
        ResourceLocation equippedId = equipped.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(equipped.getItem());
        boolean same = selectedId != null && selectedId.equals(equippedId);
        equip.active = pendingTicks == 0 && selectedId != null && !same;
        equip.setMessage(Component.literal(pendingTicks > 0 ? "รอ..." : same ? "ใส่อยู่" : "ใส่"));
        clear.active = pendingTicks == 0 && !equipped.isEmpty();
        previous.active = page > 0;
        next.active = page + 1 < pageCount();
        categories.forEach((slot, button) -> {
            button.selected(slot == category);
            ItemStack stack = equipped(slot);
            button.setTooltip(Tooltip.create(Component.literal(label(slot) + ": ")
                    .append(stack.isEmpty() ? Component.literal("ยังไม่ได้ใส่") : stack.getHoverName())));
        });
        for (int i = 0; i < tiles.size(); i++) {
            WardrobeEntry entry = entries.get(page * pageSize() + i);
            tiles.get(i).state(entry.id.equals(selectedId), entry.id.equals(equippedId));
            tiles.get(i).setMessage(entry.stack.getHoverName().copy()
                    .append(entry.id.equals(equippedId) ? " กำลังใส่" : ""));
        }
    }

    private void selectCategory(FashionSlot slot) {
        category = slot;
        selectedId = null;
        page = 0;
        refreshEntries(true);
    }

    private void changePage(int delta) {
        int target = Math.clamp(page + delta, 0, pageCount() - 1);
        if (target == page) return;
        page = target;
        rebuildTiles();
        updateControls();
    }

    private void equipSelected() {
        if (!equip.active) return;
        pendingTicks = 100;
        PacketDistributor.sendToServer(new FashionEquipPayload(category, selectedId));
        updateControls();
    }

    private void clearEquipped() {
        if (!clear.active) return;
        pendingTicks = 100;
        PacketDistributor.sendToServer(new FashionClearPayload(category));
        updateControls();
    }

    public void receiveAction(FashionActionResultPayload payload) {
        pendingTicks = 0;
        showStatus(payload.message(), payload.success());
        refreshEntries(false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        FashionWindowFrame.render(graphics, x, sy(14), panelWidth, y + panelHeight - sy(14));
        FashionWindowFrame.title(graphics, sx(154), y, sx(276) - sx(154), sy(28) - y);
        graphics.fill(sx(8), sy(34), sx(120), sy(224), 0xFF10181B);
        graphics.blit(CATEGORY_PANEL, sx(8), sy(34), sx(120) - sx(8), sy(224) - sy(34),
                0, 0, 384, 728, 384, 728);
        graphics.fill(sx(244), sy(34), sx(422), sy(224), 0xFF10181B);
        graphics.blit(WARDROBE_PANEL, sx(244), sy(34), sx(422) - sx(244), sy(224) - sy(34),
                0, 0, 576, 728, 576, 728);
        centered(graphics, title.getString(), sx(164), sx(266) - sx(164),
                y + (sy(28) - y - font.lineHeight) / 2, TEXT);
        clipped(graphics, "หมวดแฟชั่น", leftX, contentY, leftWidth, TEXT);
        centered(graphics, "ตัวละคร", centerX, centerWidth - 22, contentY, MUTED);
        clipped(graphics, "คลังแฟชั่น", rightX, contentY, rightWidth, TEXT);
        boolean searchHovered = mouseX >= rightX && mouseX < rightX + rightWidth
                && mouseY >= searchY && mouseY < searchY + 14;
        graphics.blit(search.isFocused() || searchHovered ? SEARCH_FOCUS : SEARCH_NORMAL,
                rightX, searchY, rightWidth, 14, 0, 0, 512, 48, 512, 48);
        graphics.blit(SEARCH_ICON, rightX + 4, searchY + 2, 10, 10, 0, 0, 16, 16, 16, 16);
        for (int index = tiles.size(); index < pageSize(); index++) {
            FashionSlotButton.renderEmpty(graphics, gridX + index % columns * slotPitch(),
                    gridY + index / columns * slotPitch(), slotSize);
        }
        renderPlayer(graphics, mouseX, mouseY);
        renderDetails(graphics);
        centered(graphics, (page + 1) + " / " + pageCount(), rightX + rightWidth / 2 - 22, 44, pagerY + 4, MUTED);
        if (entries.isEmpty()) centered(graphics, ownedCount == 0 ? "คลังว่าง" : "ไม่พบแฟชั่น",
                rightX, rightWidth, gridY + (rows * slotPitch() - SLOT_GAP - font.lineHeight) / 2, MUTED);
        if (statusTicks > 0) clipped(graphics, status, x + 10, y + panelHeight - 9, panelWidth - 20, statusColor);
        for (var renderable : renderables) renderable.render(graphics, mouseX, mouseY, partialTick);
        renderCategoryContents(graphics);
        renderTileContents(graphics, mouseX, mouseY);
    }

    private void renderPlayer(GuiGraphics graphics, int mouseX, int mouseY) {
        if (minecraft.player == null) return;
        int top = previewTop();
        int bottom = previewBottom();
        int scale = Math.max(12, Math.min(centerWidth / 2, (bottom - top - 8) / 2));
        var player = minecraft.player;
        float bodyYaw = player.yBodyRot;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        float oldHeadYaw = player.yHeadRotO;
        float headYaw = player.yHeadRot;
        graphics.enableScissor(centerX, top, centerX + centerWidth, bottom);
        try {
            // Keep the head aligned with the body; restore gameplay orientation after drawing.
            player.yBodyRot = 180F + previewYaw;
            player.setYRot(player.yBodyRot);
            player.setXRot(0);
            player.yHeadRot = player.yBodyRot;
            player.yHeadRotO = player.yBodyRot;
            float entityScale = player.getScale();
            InventoryScreen.renderEntityInInventory(graphics,
                    centerX + centerWidth / 2F, (top + bottom) / 2F, scale / entityScale,
                    new Vector3f(0, player.getBbHeight() / 2F + 0.0625F * entityScale, 0),
                    new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), player);
        } finally {
            player.yBodyRot = bodyYaw;
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yHeadRotO = oldHeadYaw;
            player.yHeadRot = headYaw;
            graphics.disableScissor();
        }
        centered(graphics, minecraft.player.getName().getString(), centerX, centerWidth, actionY + 5, MUTED);
    }

    private void renderCategoryContents(GuiGraphics graphics) {
        categories.forEach((slot, button) -> {
            ItemStack stack = equipped(slot);
            if (button.getHeight() >= 16) graphics.renderItem(stack.isEmpty() ? categoryIcon(slot) : stack,
                    button.getX() + 3, button.getY() + (button.getHeight() - 16) / 2);
            int labelX = button.getX() + 23;
            int labelY = button.getY() + (button.getHeight() >= 24 ? 3 : (button.getHeight() - font.lineHeight) / 2);
            int textWidth = button.getWidth() - 37;
            clipped(graphics, label(slot), labelX, labelY, textWidth, TEXT);
            if (button.getHeight() >= 24) {
                clipped(graphics, stack.isEmpty() ? "ว่าง" : stack.getHoverName().getString(),
                        labelX, labelY + 10, textWidth, MUTED);
            }
            graphics.drawString(font, ">", button.getX() + button.getWidth() - 10,
                    button.getY() + (button.getHeight() - font.lineHeight) / 2,
                    slot == category ? GOLD : MUTED, false);
        });
    }

    private void renderTileContents(GuiGraphics graphics, int mouseX, int mouseY) {
        ItemStack current = equipped(category);
        ResourceLocation equippedId = current.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(current.getItem());
        WardrobeEntry hovered = null;
        for (int i = 0; i < tiles.size(); i++) {
            FashionSlotButton tile = tiles.get(i);
            WardrobeEntry entry = entries.get(page * pageSize() + i);
            int iconX = tile.getX() + (slotSize - 16) / 2;
            int iconY = tile.getY() + Math.max(2, (slotSize - 16) / 2 - (entry.id.equals(equippedId) ? 2 : 0));
            graphics.renderItem(entry.stack, iconX, iconY);
            if (tile.isMouseOver(mouseX, mouseY)) hovered = entry;
        }
        if (hovered != null) graphics.renderTooltip(font, List.of(hovered.stack.getHoverName(),
                Component.literal(hovered.id.equals(equippedId) ? "กำลังใส่" : "ปลดล็อกแล้ว")
                        .withStyle(hovered.id.equals(equippedId) ? ChatFormatting.AQUA : ChatFormatting.GRAY)),
                java.util.Optional.empty(), mouseX, mouseY);
    }

    private void renderDetails(GuiGraphics graphics) {
        WardrobeEntry selected = entries.stream().filter(entry -> entry.id.equals(selectedId)).findFirst().orElse(null);
        graphics.blit(DETAIL, rightX, detailY, rightWidth, 24, 0, 0, 512, 100, 512, 100);
        FashionSlotButton.renderEmpty(graphics, rightX + 3, detailY + 2, 20);
        if (selected != null) graphics.renderItem(selected.stack, rightX + 5, detailY + 4);
        int textX = rightX + 27;
        int textWidth = rightWidth - 31;
        clipped(graphics, selected == null ? "เลือกแฟชั่น" : selected.stack.getHoverName().getString(),
                textX, detailY + 3, textWidth, TEXT);
        boolean wearing = selected != null && !equipped(category).isEmpty()
                && BuiltInRegistries.ITEM.getKey(equipped(category).getItem()).equals(selected.id);
        String count = Integer.toString(ownedCount);
        int countWidth = font.width(count);
        clipped(graphics, wearing ? "กำลังใส่" : "ปลดล็อกแล้ว", textX, detailY + 14,
                textWidth - countWidth - 4, wearing ? TEAL : MUTED);
        graphics.drawString(font, count, rightX + rightWidth - countWidth - 4, detailY + 14, MUTED, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && minecraft.player != null && mouseX >= centerX && mouseX < centerX + centerWidth
                && mouseY >= previewTop() && mouseY < previewBottom()) {
            setFocused(null);
            rotatingPreview = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && rotatingPreview) {
            previewYaw = Mth.wrapDegrees(previewYaw + (float) (deltaX * 360.0 / Math.max(1, centerWidth)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasRotating = button == 0 && rotatingPreview;
        if (button == 0) rotatingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button) || wasRotating;
    }

    @Override
    public void removed() {
        rotatingPreview = false;
        super.removed();
    }

    private int previewTop() { return contentY + 17; }
    private int previewBottom() { return actionY - 5; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= gridX && mouseX < gridX + gridWidth()
                && mouseY >= gridY && mouseY < gridY + rows * slotPitch() - SLOT_GAP && scrollY != 0) {
            changePage(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private ItemStack equipped(FashionSlot slot) {
        return minecraft.player == null ? ItemStack.EMPTY : ClientFashionData.get(minecraft.player.getId(), slot);
    }

    private ItemStack categoryIcon(FashionSlot slot) {
        return new ItemStack(switch (slot) {
            case HEAD -> Items.LEATHER_HELMET;
            case FACE -> Items.SPYGLASS;
            case BACK -> Items.ELYTRA;
            case BODY -> Items.LEATHER_CHESTPLATE;
            case LEGS -> Items.LEATHER_LEGGINGS;
            case FEET -> Items.LEATHER_BOOTS;
        });
    }

    private String label(FashionSlot slot) {
        return switch (slot) {
            case HEAD -> "หัว";
            case FACE -> "ใบหน้า";
            case BACK -> "หลัง";
            case BODY -> "ลำตัว";
            case LEGS -> "ขา";
            case FEET -> "เท้า";
        };
    }

    private void showStatus(String message, boolean success) {
        status = message;
        statusColor = success ? 0xFF68D391 : 0xFFFF7B72;
        statusTicks = 100;
    }

    private void clipped(GuiGraphics graphics, String text, int tx, int ty, int available, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text, Math.max(1, available)), tx, ty, color, false);
    }

    private void centered(GuiGraphics graphics, String text, int tx, int available, int ty, int color) {
        String clipped = font.plainSubstrByWidth(text, Math.max(1, available));
        graphics.drawString(font, clipped, tx + (available - font.width(clipped)) / 2, ty, color, false);
    }

    private int sx(int value) { return x + Math.round(value * panelWidth / (float) SOURCE_WIDTH); }
    private int sy(int value) { return y + Math.round(value * panelHeight / (float) SOURCE_HEIGHT); }
    private int pageSize() { return columns * rows; }
    private int slotPitch() { return slotSize + SLOT_GAP; }
    private int gridWidth() { return columns * slotPitch() - SLOT_GAP; }
    private static ResourceLocation fashionTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/fashion/fashion_" + name + ".png");
    }
    private int pageCount() { return Math.max(1, (entries.size() + pageSize() - 1) / pageSize()); }

    @Override
    public boolean isPauseScreen() { return false; }

    private record WardrobeEntry(ResourceLocation id, ItemStack stack) {}
}
