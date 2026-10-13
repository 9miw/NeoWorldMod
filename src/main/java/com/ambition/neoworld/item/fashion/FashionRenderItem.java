package com.ambition.neoworld.item.fashion;

import com.ambition.neoworld.fashion.data.FashionSlot;

public interface FashionRenderItem {
    FashionSlot fashionSlot();

    default FashionRenderMode fashionRenderMode() {
        return FashionRenderMode.VANILLA_ITEM;
    }
}
