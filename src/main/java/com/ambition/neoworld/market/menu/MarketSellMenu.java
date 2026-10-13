package com.ambition.neoworld.market.menu;

import com.ambition.neoworld.market.MarketResult;
import com.ambition.neoworld.market.service.MarketService;
import com.ambition.neoworld.registry.ModMenus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-owned escrow slot plus the player's real inventory. */
public final class MarketSellMenu extends AbstractContainerMenu {
    public static final int WIDTH = 440;
    public static final int HEIGHT = 248;
    public static final int SELL_SLOT_X = 294;
    public static final int SELL_SLOT_Y = 51;
    public static final int INVENTORY_X = 28;
    public static final int INVENTORY_Y = 68;
    public static final int SLOT_PITCH = 22;
    public static final long MAX_DURATION_TICKS = 30L * 24L * 60L * 60L * 20L;

    private final SimpleContainer listing = new SimpleContainer(1);
    private final long listingFeeSilver;
    private final int salesTaxRateBps;

    public MarketSellMenu(int containerId, Inventory inventory, long listingFeeSilver, int salesTaxRateBps) {
        super(ModMenus.MARKET_SELL.get(), containerId);
        this.listingFeeSilver = Math.max(0L, listingFeeSilver);
        this.salesTaxRateBps = Math.max(0, salesTaxRateBps);

        addSlot(new Slot(listing, 0, SELL_SLOT_X, SELL_SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column,
                        INVENTORY_X + column * SLOT_PITCH, INVENTORY_Y + row * SLOT_PITCH));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column,
                    INVENTORY_X + column * SLOT_PITCH, INVENTORY_Y + 3 * SLOT_PITCH + 14));
        }
    }

    public long listingFeeSilver() {
        return listingFeeSilver;
    }

    public int salesTaxRateBps() {
        return salesTaxRateBps;
    }

    public MarketService.ListingResult submit(ServerPlayer player, int quantity, long unitPrice, long durationTicks) {
        if (player.containerMenu != this || quantity <= 0) {
            return new MarketService.ListingResult(MarketResult.INVALID_QUANTITY, java.util.OptionalLong.empty());
        }
        if (durationTicks <= 0 || durationTicks > MAX_DURATION_TICKS) {
            return new MarketService.ListingResult(MarketResult.INVALID_DURATION, java.util.OptionalLong.empty());
        }
        ItemStack source = listing.getItem(0);
        MarketService.ListingResult result = MarketService.createListing(
                player, source, quantity, unitPrice, durationTicks);
        if (result.result() == MarketResult.SUCCESS) {
            listing.setChanged();
            broadcastChanges();
        }
        return result;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = sourceSlot.getItem();
        ItemStack original = source.copy();
        if (index == 0) {
            if (!moveItemStackTo(source, 1, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) sourceSlot.set(ItemStack.EMPTY);
        else sourceSlot.setChanged();
        sourceSlot.onTake(player, source);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) clearContainer(player, listing);
    }
}
