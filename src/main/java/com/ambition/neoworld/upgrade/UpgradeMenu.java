package com.ambition.neoworld.upgrade;

import com.ambition.neoworld.registry.ModBlocks;
import com.ambition.neoworld.registry.ModItems;
import com.ambition.neoworld.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Four real escrow slots; previews are text only, never pickable item copies. */
public final class UpgradeMenu extends AbstractContainerMenu {
    public static final int WIDTH = 360;
    public static final int HEIGHT = 232;
    public static final int INVENTORY_X = 185;
    public static final int INVENTORY_Y = 151;
    public static final int HOTBAR_Y = 207;
    public static final int SLOT_PITCH = 18;
    public final long session;
    private final Player owner;
    private final BlockPos position;
    private final SimpleContainer inputs = new SimpleContainer(4);
    private final ContainerData data = new SimpleContainerData(6);
    private final ItemStack[] snapshot = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private int revision;
    private long lastAttemptTick = Long.MIN_VALUE;

    public UpgradeMenu(int id, Inventory inventory, BlockPos pos, long session) {
        super(ModMenus.UPGRADE.get(), id);
        this.owner = inventory.player;
        this.position = pos.immutable();
        this.session = session;
        addSlot(new Slot(inputs, 0, 28, 54) {
            @Override public boolean mayPlace(ItemStack stack) { return UpgradeService.eligible(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new Slot(inputs, 1, 30, 94) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(ModItems.UPGRADE_STONE.get())
                    || stack.is(ModItems.ADVANCED_UPGRADE_STONE.get()); }
        });
        addSlot(new Slot(inputs, 2, 82, 94) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(ModItems.PROTECTION_SCROLL.get()); }
        });
        addSlot(new Slot(inputs, 3, 134, 94) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(ModItems.SUCCESS_BOOSTER.get()); }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * SLOT_PITCH, INVENTORY_Y + row * SLOT_PITCH));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, INVENTORY_X + col * SLOT_PITCH, HOTBAR_Y));
        addDataSlots(data);
    }

    public int revision() { return (data.get(0) & 65535) | (data.get(1) & 65535) << 16; }
    public int resultCode() { return data.get(2); }
    public int resultLevel() { return data.get(3); }
    public int resultOutcome() { return data.get(4); }
    public int resultSequence() { return data.get(5) & 65535; }

    private void nextRevision() {
        revision++;
        data.set(0, revision & 65535);
        data.set(1, revision >>> 16);
    }

    @Override public void broadcastChanges() {
        if (!owner.level().isClientSide()) {
            boolean changed = false;
            for (int index = 0; index < 4; index++) {
                if (!ItemStack.matches(inputs.getItem(index), snapshot[index])) {
                    snapshot[index] = inputs.getItem(index).copy();
                    changed = true;
                }
            }
            if (changed) nextRevision();
        }
        super.broadcastChanges();
    }

    public void submit(ServerPlayer player, long requestSession, int requestRevision) {
        if (player != owner || player.containerMenu != this || requestSession != session || !stillValid(player)) return;
        // Refresh snapshots before comparing; inventory edits invalidate a prior preview.
        broadcastChanges();
        if (requestRevision != revision || !getCarried().isEmpty()) return;
        long tick = player.serverLevel().getGameTime();
        if (lastAttemptTick != Long.MIN_VALUE && tick - lastAttemptTick < 5) return;
        lastAttemptTick = tick;
        ItemStack scroll = inputs.getItem(2);
        var protection = scroll.isEmpty() ? UpgradeRules.Protection.NONE
                : UpgradeRules.Protection.FULL;
        var attempt = UpgradeService.attemptInContainer(player, inputs, 4, 0, snapshot[0].copy(), 1,
                scroll.isEmpty() ? -1 : 2, inputs.getItem(3).isEmpty() ? -1 : 3, protection);
        data.set(2, attempt.error() == UpgradeService.Error.NONE ? 1 : 2 + attempt.error().ordinal());
        data.set(3, attempt.result() == null ? UpgradeData.level(inputs.getItem(0)) : attempt.result().level());
        data.set(4, attempt.result() == null ? -1
                : attempt.result().rolled() != attempt.result().applied() ? 4 : attempt.result().applied().ordinal());
        // Last field is an acknowledgement, including repeated identical results.
        data.set(5, (resultSequence() + 1) & 65535);
        nextRevision();
        broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        return player == owner && player.isAlive() && !player.isSpectator()
                && player.level().getBlockState(position).is(ModBlocks.UPGRADE_TABLE.get())
                && player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) <= 64;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 4) {
            if (!moveItemStackTo(stack, 4, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = slots.get(0).mayPlace(stack) ? 0 : slots.get(1).mayPlace(stack) ? 1 : slots.get(2).mayPlace(stack) ? 2 : slots.get(3).mayPlace(stack) ? 3 : -1;
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) clearContainer(player, inputs);
    }
}
