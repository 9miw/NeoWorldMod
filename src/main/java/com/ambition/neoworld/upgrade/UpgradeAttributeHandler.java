package com.ambition.neoworld.upgrade;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import yesman.epicfight.registry.entries.EpicFightAttributes;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class UpgradeAttributeHandler {
    private static final String PREFIX = "upgrade_bonus/";

    private UpgradeAttributeHandler() {}

    private static boolean ownModifier(AttributeModifier modifier) {
        return modifier.id().getNamespace().equals(NeoWorld.MODID) && modifier.id().getPath().startsWith(PREFIX);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttributes(ItemAttributeModifierEvent event) {
        // Replace only our derived modifiers; never persist them on the stack or mutate its capability.
        event.removeIf(entry -> ownModifier(entry.modifier()));
        if (!UpgradeConfig.SPEC.isLoaded()) return;
        var stack = event.getItemStack();
        int level = UpgradeData.level(stack);
        if (level <= 0 || level > UpgradeData.MAX_LEVEL || !UpgradeService.eligible(stack)) return;
        if (stack.getItem() instanceof ArmorItem armor) {
            EquipmentSlot slot = armor.getEquipmentSlot();
            EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(slot);
            add(event, Attributes.ARMOR, "armor/" + slot.getName(),
                    UpgradeBonusMath.flat(level, UpgradeConfig.ARMOR_PER_LEVEL.get()), group);
            add(event, EpicFightAttributes.STUN_ARMOR, "stun_armor/" + slot.getName(),
                    UpgradeBonusMath.flat(level, UpgradeConfig.STUN_ARMOR_PER_LEVEL.get()), group);
        } else {
            double baseDamage = originalWeaponDamage(event.getDefaultModifiers());
            // Explicit opt-in equipment without a weapon damage modifier receives no combat bonus.
            if (baseDamage <= 0) return;
            add(event, Attributes.ATTACK_DAMAGE, "damage/mainhand",
                    UpgradeBonusMath.damage(baseDamage, level, UpgradeConfig.DAMAGE_PERCENT_PER_LEVEL.get()),
                    EquipmentSlotGroup.MAINHAND);
            add(event, EpicFightAttributes.IMPACT, "impact/mainhand",
                    UpgradeBonusMath.flat(level, UpgradeConfig.IMPACT_PER_LEVEL.get()), EquipmentSlotGroup.MAINHAND);
        }
    }

    /** Standard player base damage (1) plus the item's original modifiers, excluding enchantments/effects. */
    static double originalWeaponDamage(ItemAttributeModifiers original) {
        AttributeInstance damage = new AttributeInstance(Attributes.ATTACK_DAMAGE, ignored -> {});
        damage.setBaseValue(1.0);
        boolean hasDamage = false;
        for (var entry : original.modifiers()) {
            if (entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.slot().test(EquipmentSlot.MAINHAND)
                    && !ownModifier(entry.modifier())) {
                damage.addOrReplacePermanentModifier(entry.modifier());
                hasDamage = true;
            }
        }
        return hasDamage ? Math.max(0.0, damage.getValue()) : 0.0;
    }

    private static void add(ItemAttributeModifierEvent event, Holder<Attribute> attribute, String suffix,
                            double amount, EquipmentSlotGroup slot) {
        if (amount <= 0 || !Double.isFinite(amount)) return;
        event.replaceModifier(attribute, new AttributeModifier(
                ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, PREFIX + suffix),
                amount, AttributeModifier.Operation.ADD_VALUE), slot);
    }
}
