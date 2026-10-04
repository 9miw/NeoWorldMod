package com.ambition.neoworld.item.fashion;

import com.ambition.neoworld.data.fashion.FashionSlot;

import net.minecraft.world.item.Item;

public class GeckoFashionItem extends Item implements FashionRenderItem {
    private final FashionSlot slot;

    public GeckoFashionItem(FashionSlot slot, Properties properties) {
        super(properties);
        this.slot = slot;
    }

    @Override
    public FashionSlot fashionSlot() {
        return slot;
    }

    @Override
    public FashionRenderMode fashionRenderMode() {
        return FashionRenderMode.GECKOLIB_MODEL;
    }
}
