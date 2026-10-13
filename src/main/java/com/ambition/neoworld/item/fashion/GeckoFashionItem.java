package com.ambition.neoworld.item.fashion;

import com.ambition.neoworld.fashion.data.FashionSlot;

public class GeckoFashionItem extends SimpleFashionItem {
    public GeckoFashionItem(FashionSlot slot, Properties properties) {
        super(slot, properties);
    }

    @Override
    public FashionRenderMode fashionRenderMode() {
        return FashionRenderMode.GECKOLIB_MODEL;
    }
}
