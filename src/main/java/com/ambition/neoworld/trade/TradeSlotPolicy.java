package com.ambition.neoworld.trade;

import com.ambition.neoworld.trade.menu.TradeMenu;

import net.minecraft.world.inventory.ClickType;

/** Select reference intent before allowing any vanilla item mutation path. */
public final class TradeSlotPolicy {
    public enum Decision { REJECT, REMOVE_REFERENCE, VANILLA }
    private TradeSlotPolicy() {}
    public static Decision click(int slot, int button, ClickType type, int sourceSlot, int totalSlots) {
        if (slot >= 0 && slot < TradeMenu.PREVIEW_SLOTS) {
            return slot < 9 && sourceSlot >= 0 && sourceSlot < 36 && (button == 0 || button == 1)
                    && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)
                    ? Decision.REMOVE_REFERENCE : Decision.REJECT;
        }
        if (slot >= totalSlots || slot < -1 && slot != -999) return Decision.REJECT;
        return Decision.VANILLA;
    }
}
