package com.ambition.neoworld.client.gui.fashion;

import com.ambition.neoworld.NeoWorld;
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
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "fashion_tooltip");
    private static final int TEXT = 0xD4D9DE;
    private static final int MUTED = 0x9EA7AE;
    private static final int TEAL = 0x66DDD5;

    private FashionTooltip() {}

    private static Component label(String key, int color, Object... args) {
        return Component.translatable("tooltip.neoworld.fashion." + key, args)
                .withStyle(style -> style.withColor(color));
    }

    private static Component icon(int icon) {
        return Component.literal(String.valueOf((char) (0xE200 + icon)))
                .withStyle(style -> style.withFont(FONT).withColor(0xFFFFFF));
    }

    private static Component row(int icon, Component text) {
        return Component.empty()
                .append(icon(icon))
                .append(Component.literal("  ")).append(text);
    }

    private static int slotIcon(FashionSlot slot) {
        return switch (slot) {
            case HEAD -> 8;
            case FACE -> 9;
            case BACK -> 10;
            case BODY -> 11;
            case LEGS -> 12;
            case FEET -> 13;
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
        lines.add(row(3, label("permanent", TEXT)));
        lines.add(Component.empty());
        lines.add(row(4, label("usage", TEAL)));
        lines.add(row(2, label("right_click", TEAL)));
        lines.add(row(5, label("consume", TEXT)));
        lines.add(row(0, label("equip", TEXT)));
        lines.add(Component.empty());
        boolean owned = event.getEntity() != null && event.getEntity().level().isClientSide()
                && ClientFashionData.owns(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()));
        if (owned) lines.add(row(6, label("owned", 0x79E46B)));
        lines.add(row(7, label("duplicate", MUTED)));
        // Insert after the name without replacing lore, attributes or advanced tooltip data.
        tooltip.addAll(1, lines);
    }

}

