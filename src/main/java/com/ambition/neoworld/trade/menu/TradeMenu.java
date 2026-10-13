package com.ambition.neoworld.trade.menu;

import com.ambition.neoworld.trade.service.TradeManager;
import com.ambition.neoworld.trade.TradeSlotPolicy;

import java.util.List;
import java.util.UUID;
import com.ambition.neoworld.registry.ModMenus;
import com.ambition.neoworld.trade.TradePlan.Offer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Real inventory slots plus protected reference-only offer slots, never an escrow container. */
public final class TradeMenu extends AbstractContainerMenu {
    public static final int WIDTH = 312, HEIGHT = 232, PREVIEW_SLOTS = 18;
    public static final int INVENTORY_X = 76, INVENTORY_Y = 122;
    private final UUID sessionId;
    private final SimpleContainer previews = new SimpleContainer(PREVIEW_SLOTS);
    private final int[] sourceSlots = new int[9];
    private boolean removing;

    public TradeMenu(int id, Inventory inventory, UUID sessionId) {
        super(ModMenus.TRADE.get(), id);
        this.sessionId = sessionId;
        java.util.Arrays.fill(sourceSlots, -1);
        for (int side = 0; side < 2; side++) {
            for (int cell = 0; cell < 9; cell++) {
                addSlot(new PreviewSlot(previews, side * 9 + cell, 13 + side * 156 + cell % 3 * 18, 33 + cell / 3 * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 54));
    }
    public UUID sessionId() { return sessionId; }
    public boolean belongsTo(UUID id) { return sessionId.equals(id); }
    public boolean isRemoving() { return removing; }

    public static final class PreviewSlot extends Slot {
        PreviewSlot(SimpleContainer container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
    }
    public void updateOffers(List<Offer> mine, List<Offer> theirs) {
        java.util.Arrays.fill(sourceSlots, -1);
        for (int cell = 0; cell < 9; cell++) {
            previews.setItem(cell, cell < mine.size() ? mine.get(cell).item().copy() : ItemStack.EMPTY);
            previews.setItem(cell + 9, cell < theirs.size() ? theirs.get(cell).item().copy() : ItemStack.EMPTY);
            if (cell < mine.size()) sourceSlots[cell] = mine.get(cell).slot();
        }
    }
    @Override public boolean stillValid(Player player) {
        return !(player instanceof ServerPlayer serverPlayer)
                || TradeManager.get(serverPlayer.getServer()).menuValid(serverPlayer, sessionId);
    }
    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        int source = slotId >= 0 && slotId < 9 ? sourceSlots[slotId] : -1;
        TradeSlotPolicy.Decision decision = TradeSlotPolicy.click(slotId, button, type, source, slots.size());
        if (decision != TradeSlotPolicy.Decision.VANILLA) {
            if (decision == TradeSlotPolicy.Decision.REMOVE_REFERENCE && player instanceof ServerPlayer serverPlayer) {
                TradeManager.get(serverPlayer.getServer()).offerFromMenu(serverPlayer, sessionId, sourceSlots[slotId], 0);
            }
            return; // Never pass previews into vanilla pickup/swap/throw/clone paths.
        }
        super.clicked(slotId, button, type, player);
        if (player instanceof ServerPlayer serverPlayer) TradeManager.get(serverPlayer.getServer()).inventoryChanged(serverPlayer, sessionId);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index >= PREVIEW_SLOTS && index < slots.size() && player instanceof ServerPlayer serverPlayer) {
            Slot slot = slots.get(index);
            if (slot.hasItem()) TradeManager.get(serverPlayer.getServer()).offerFromMenu(serverPlayer, sessionId,
                    slot.getContainerSlot(), slot.getItem().getCount());
        }
        return ItemStack.EMPTY; // Shift-click selects a reference; it does not transfer the underlying stack.
    }
    @Override public boolean canDragTo(Slot slot) { return !(slot instanceof PreviewSlot) && super.canDragTo(slot); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return !(slot instanceof PreviewSlot) && super.canTakeItemForPickAll(stack, slot); }
    @Override public void removed(Player player) {
        // Vanilla returns the real cursor stack. Never clearContainer(previews): they are copies.
        removing = true;
        try {
            super.removed(player);
            if (player instanceof ServerPlayer serverPlayer) TradeManager.get(serverPlayer.getServer()).cancel(player.getUUID(), "ปิดหน้าต่างแลกเปลี่ยน");
        } finally { removing = false; }
    }
}
