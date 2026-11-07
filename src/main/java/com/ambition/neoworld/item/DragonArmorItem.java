package com.ambition.neoworld.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;

/** Same source mesh in vanilla and Epic Fight render paths. */
public final class DragonArmorItem extends ArmorItem {
    private final String part;
    public DragonArmorItem(Type type, String part) {
        super(ArmorMaterials.IRON, type, new Item.Properties().durability(type.getDurability(15)));
        this.part = part;
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot,
                                            ArmorMaterial.Layer layer, boolean innerModel) {
        return ResourceLocation.fromNamespaceAndPath("neoworld", "textures/models/armor/dragon.png");
    }

    public String modelPart() { return part; }
}
