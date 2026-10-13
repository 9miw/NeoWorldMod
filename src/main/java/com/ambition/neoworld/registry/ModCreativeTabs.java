package com.ambition.neoworld.registry;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// คลาสสำหรับลงทะเบียนแท็บหมวดหมู่ในโหมดสร้างสรรค์ (Creative Mode Tabs) ของมอด
public class ModCreativeTabs {
    // Deferred Register สำหรับแท็บหมวดหมู่ Creative Mode
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NeoWorld.MODID);

    // แท็บหลัก "neoworld:neoworld_tab" วางต่อจากแท็บ Combat (ต่อสู้) และใช้ไอคอนเป็น EXAMPLE_ITEM
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> NEOWORLD_TAB = CREATIVE_MODE_TABS.register("neoworld_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.neoworld")) // คีย์แปลภาษาสำหรับชื่อแท็บ
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.UPGRADE_STONE.get());
                output.accept(ModItems.ADVANCED_UPGRADE_STONE.get());
                output.accept(ModItems.SUPREME_UPGRADE_STONE.get());
                output.accept(ModItems.DOWNGRADE_PROTECTION_SCROLL.get());
                output.accept(ModItems.BREAK_PROTECTION_SCROLL.get());
                output.accept(ModItems.UPGRADE_TABLE.get());
                output.accept(ModItems.EXAMPLE_ITEM.get()); // เพิ่มไอเทมเข้าไปในแท็บนี้
                output.accept(ModItems.EXAMPLE_ITEMSWORD.get()); // ดาบเลเวล 15 (ค่าเริ่มต้น)
                for (int upgradeLevel = 1; upgradeLevel <= com.ambition.neoworld.upgrade.UpgradeData.MAX_LEVEL; upgradeLevel++) {
                    ItemStack upgradedSword = new ItemStack(ModItems.EXAMPLE_ITEMSWORD.get());
                    com.ambition.neoworld.upgrade.UpgradeData.set(upgradedSword, upgradeLevel, 0);
                    output.accept(upgradedSword);
                }
                output.accept(ModItems.DUELIST_ARMOR_CHEST.get());
                output.accept(ModItems.DUELIST_ARMOR_LEGGINGS.get());
                output.accept(ModItems.DUELIST_ARMOR_BOOTS.get());
                output.accept(ModItems.DUELIST_ARMOR_HELMET.get());
                output.accept(ModItems.DUELIST_DIAMOND_ARMOR_CHEST.get());
                output.accept(ModItems.DUELIST_DIAMOND_ARMOR_LEGGINGS.get());
                output.accept(ModItems.DUELIST_DIAMOND_ARMOR_BOOTS.get());
                output.accept(ModItems.DUELIST_DIAMOND_ARMOR_HELMET.get());
                output.accept(ModItems.DUELIST_GOLDEN_ARMOR_CHEST.get());
                output.accept(ModItems.DUELIST_GOLDEN_ARMOR_LEGGINGS.get());
                output.accept(ModItems.DUELIST_GOLDEN_ARMOR_BOOTS.get());
                output.accept(ModItems.DUELIST_GOLDEN_ARMOR_HELMET.get());
                output.accept(ModItems.DUELIST_NETHERITE_ARMOR_CHEST.get());
                output.accept(ModItems.DUELIST_NETHERITE_ARMOR_LEGGINGS.get());
                output.accept(ModItems.DUELIST_NETHERITE_ARMOR_BOOTS.get());
                output.accept(ModItems.DUELIST_NETHERITE_ARMOR_HELMET.get());

                ItemStack level30Sword = new ItemStack(ModItems.EXAMPLE_ITEMSWORD.get());
                level30Sword.set(ModDataComponents.REQUIRED_LEVEL.get(), 30);
                output.accept(level30Sword); // ไอเทม ID เดียวกัน แต่ Data Component ต่างกัน

                output.accept(ModItems.SILVER_COIN.get());  // เหรียญเงิน
                output.accept(ModItems.GOLD_COIN.get());    // เหรียญทอง
                output.accept(ModItems.DIAMOND_COIN.get()); // เหรียญเพชร
                output.accept(ModItems.EXPERIENCE_ITEM.get()); // EXP
                output.accept(ModItems.FASHION_EXAMPLE_HAT.get()); // หมวกแฟชั่นตัวอย่าง
                output.accept(ModItems.ONI_CURSE_WINGS.get());
                output.accept(ModItems.DUELIST_IRON_HEAD.get());
                output.accept(ModItems.DUELIST_IRON_BODY.get());
                output.accept(ModItems.DUELIST_IRON_LEGS.get());
                output.accept(ModItems.DUELIST_IRON_FEET.get());

                // === Lunar Set (เซตตรุษจีน) ===
                output.accept(ModItems.LUNAR_SWORD.get());
                output.accept(ModItems.LUNAR_GREATSWORD.get());
                output.accept(ModItems.LUNAR_AXE.get());
                output.accept(ModItems.LUNAR_HAMMER.get());
                output.accept(ModItems.LUNAR_SCYTHE.get());
                output.accept(ModItems.LUNAR_SPEAR.get());
                output.accept(ModItems.LUNAR_STAFF.get());
                output.accept(ModItems.LUNAR_TRIDENT.get());
                output.accept(ModItems.LUNAR_SHIELD.get());
                output.accept(ModItems.LUNAR_PICKAXE.get());
                output.accept(ModItems.LUNAR_SHOVEL.get());
                output.accept(ModItems.LUNAR_HOE.get());
            }).build());
}
