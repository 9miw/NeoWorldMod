package com.ambition.neoworld.client.gui.common;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/** Presentation tiers: per-stack override, then tags, then vanilla rarity; independent of upgrades. */
public enum NeoTooltipRarity {
    COMMON("common", 0xAEB7BF),
    UNCOMMON("uncommon", 0x49D995),
    RARE("rare", 0x31C6EF),
    EPIC("epic", 0xBA68F4),
    LEGENDARY("legendary", 0xF6CC58);

    public final TagKey<Item> tag;
    public final String id;
    public final ResourceLocation texture;
    public final ResourceLocation partsTexture;
    public final int color;

    NeoTooltipRarity(String name, int color) {
        id = name;
        tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "rarity/" + name));
        texture = ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/common/tooltip/" + name + ".png");
        partsTexture = ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/common/tooltip/deepslate/" + name + "_parts.png");
        this.color = color;
    }

    public boolean premium() { return this == EPIC || this == LEGENDARY; }

    public int cornerSize() { return 6 + ordinal(); }

    public int padding() { return 8 + ordinal(); }

    // Measured native rail profiles, recorded in deepslate/geometry.json.
    public int railOffset() { return 1; }

    public int railOffsetY() { return 1; }

    public static NeoTooltipRarity of(ItemStack stack) {
        String override = stack.get(ModDataComponents.ITEM_RARITY.get());
        if (override != null) {
            for (NeoTooltipRarity rarity : values()) {
                if (rarity.id.equals(override)) return rarity;
            }
        }
        // Highest tier wins when a data pack accidentally assigns more than one tag.
        for (int i = values().length - 1; i >= 0; i--) {
            if (stack.is(values()[i].tag)) return values()[i];
        }
        // getRarity() promotes enchanted equipment; frame identity should remain stable.
        return switch (stack.getOrDefault(DataComponents.RARITY, Rarity.COMMON)) {
            case COMMON -> COMMON;
            case UNCOMMON -> UNCOMMON;
            case RARE -> RARE;
            case EPIC -> EPIC;
        };
    }
}
