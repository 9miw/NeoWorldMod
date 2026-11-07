package com.ambition.neoworld.client.gui.fashion;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.common.NeoBitmapIcon;
import static com.ambition.neoworld.client.gui.common.NeoBitmapIcon.*;
import com.ambition.neoworld.client.state.ClientFashionData;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.ambition.neoworld.item.fashion.SimpleFashionItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class FashionTooltip {
    private static final int TEXT = 0xD4D9DE;
    private static final int MUTED = 0x9EA7AE;
    private static final int TEAL = 0x66DDD5;

    private FashionTooltip() {}

    private static Component label(String key, int color, Object... args) {
        return Component.translatable("tooltip.neoworld.fashion." + key, args)
                .withStyle(style -> style.withColor(color));
    }

    private static Component icon(NeoBitmapIcon icon) {
        return icon.component().withStyle(style -> style.withColor(0xFFFFFF));
    }

    private static Component row(NeoBitmapIcon icon, Component text) {
        return Component.empty()
                .append(icon(icon))
                .append(Component.literal("  ")).append(text);
    }

    private static NeoBitmapIcon slotIcon(FashionSlot slot) {
        return switch (slot) {
            case HEAD -> FASHION_TOOLTIP_SLOT_HEAD;
            case FACE -> FASHION_TOOLTIP_SLOT_FACE;
            case BACK -> FASHION_TOOLTIP_SLOT_BACK;
            case BODY -> FASHION_TOOLTIP_SLOT_BODY;
            case LEGS -> FASHION_TOOLTIP_SLOT_LEGS;
            case FEET -> FASHION_TOOLTIP_SLOT_FEET;
        };
    }

    @SubscribeEvent
    public static void content(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof SimpleFashionItem item)) return;
        var tooltip = event.getToolTip();
        if (tooltip.isEmpty()) return;
        tooltip.set(0, tooltip.getFirst().copy().withStyle(style -> style.withColor(0xE8C46B)));
        List<Component> lines = new ArrayList<>();
        lines.add(label("type", MUTED));
        lines.add(Component.empty());
        Component slotValue = Component.empty()
                .append(icon(slotIcon(item.fashionSlot())))
                .append(Component.literal(" "))
                .append(label("slot." + item.fashionSlot().serializedName(), TEXT));
        lines.add(label("slot", TEXT, slotValue));
        lines.add(row(FASHION_TOOLTIP_WARDROBE, label("permanent", TEXT)));
        lines.add(Component.empty());
        lines.add(row(FASHION_TOOLTIP_UNLOCK, label("usage", TEAL)));
        lines.add(row(FASHION_TOOLTIP_RIGHT_CLICK, label("right_click", TEAL)));
        lines.add(row(FASHION_TOOLTIP_CONSUME, label("consume", TEXT)));
        lines.add(row(FASHION_TOOLTIP_FASHION, label("equip", TEXT)));
        lines.add(Component.empty());
        boolean owned = event.getEntity() != null && event.getEntity().level().isClientSide()
                && ClientFashionData.owns(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()));
        if (owned) lines.add(row(FASHION_TOOLTIP_OWNED, label("owned", 0x79E46B)));
        lines.add(row(FASHION_TOOLTIP_INFO, label("duplicate", MUTED)));
        // Insert after the name without replacing lore, attributes or advanced tooltip data.
        tooltip.addAll(1, lines);
    }

}

