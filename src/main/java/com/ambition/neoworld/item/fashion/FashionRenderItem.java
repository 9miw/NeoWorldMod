package com.ambition.neoworld.item.fashion;

import com.ambition.neoworld.data.fashion.FashionSlot;

public interface FashionRenderItem {
    FashionSlot fashionSlot();

    default FashionRenderMode fashionRenderMode() {
        return FashionRenderMode.VANILLA_ITEM;
    }
}
