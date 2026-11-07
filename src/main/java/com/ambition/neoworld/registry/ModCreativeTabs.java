package com.ambition.neoworld.registry;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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
                output.accept(ModItems.LOOT_BOX.get());
                output.accept(ModItems.UPGRADE_STONE.get());
                output.accept(ModItems.ADVANCED_UPGRADE_STONE.get());
                output.accept(ModItems.PROTECTION_SCROLL.get());
                output.accept(ModItems.SUCCESS_BOOSTER.get());
                output.accept(ModItems.UPGRADE_TABLE.get());
                output.accept(ModItems.EXAMPLE_ITEM.get()); // เพิ่มไอเทมเข้าไปในแท็บนี้
                output.accept(ModItems.EXAMPLE_ITEMSWORD.get()); // ดาบเลเวล 15 (ค่าเริ่มต้น)
                // Stable shuffled examples: rebuilding the tab must not replace its sample stacks.
                List<Item> lunarWeapons = List.of(ModItems.LUNAR_SWORD.get(), ModItems.LUNAR_GREATSWORD.get(),
                        ModItems.LUNAR_HAMMER.get(), ModItems.LUNAR_SCYTHE.get(), ModItems.LUNAR_SPEAR.get(),
                        ModItems.LUNAR_STAFF.get(), ModItems.LUNAR_TRIDENT.get(), ModItems.LUNAR_AXE.get());
                Random sampleRandom = new Random(0x4C554E4152L);
                List<Item> sampleItems = new ArrayList<>(lunarWeapons);
                List<String> rarityNames = List.of("common", "uncommon", "rare", "epic", "legendary");
                List<String> sampleRarities = new ArrayList<>();
                int sampleCount = com.ambition.neoworld.upgrade.UpgradeData.MAX_LEVEL;
                while (sampleItems.size() < sampleCount) {
                    sampleItems.add(lunarWeapons.get(sampleRandom.nextInt(lunarWeapons.size())));
                }
                for (int i = 0; i < sampleCount; i++) {
                    sampleRarities.add(rarityNames.get(i % rarityNames.size()));
                }
                Collections.shuffle(sampleItems, sampleRandom);
                Collections.shuffle(sampleRarities, sampleRandom);
                for (int upgradeLevel = 1; upgradeLevel <= sampleCount; upgradeLevel++) {
                    ItemStack sample = new ItemStack(sampleItems.get(upgradeLevel - 1));
                    sample.set(ModDataComponents.ITEM_RARITY.get(), sampleRarities.get(upgradeLevel - 1));
                    com.ambition.neoworld.upgrade.UpgradeData.set(sample, upgradeLevel, 0);
                    output.accept(sample);
                }
                output.accept(ModItems.DRAGON_HELMET.get());
                output.accept(ModItems.DRAGON_CHESTPLATE.get());
                output.accept(ModItems.DRAGON_LEGGINGS.get());
                output.accept(ModItems.DRAGON_BOOTS.get());
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
                output.accept(ModItems.CYBERPUNK_ASSAULT_RIFLE.get());
                output.accept(ModItems.CYBERPUNK_AXE.get());
                output.accept(ModItems.CYBERPUNK_BACK.get());
                output.accept(ModItems.CYBERPUNK_BATON.get());
                output.accept(ModItems.CYBERPUNK_BOW.get());
                output.accept(ModItems.CYBERPUNK_CHEST.get());
                output.accept(ModItems.CYBERPUNK_CROSSBOW.get());
                output.accept(ModItems.CYBERPUNK_DAGGER.get());
                output.accept(ModItems.CYBERPUNK_FISHING_ROD.get());
                output.accept(ModItems.CYBERPUNK_GAUNTLET.get());
                output.accept(ModItems.CYBERPUNK_GREATSWORD.get());
                output.accept(ModItems.CYBERPUNK_HAMMER.get());
                output.accept(ModItems.CYBERPUNK_HOE.get());
                output.accept(ModItems.CYBERPUNK_KEY.get());
                output.accept(ModItems.CYBERPUNK_PICKAXE.get());
                output.accept(ModItems.CYBERPUNK_SHIELD.get());
                output.accept(ModItems.CYBERPUNK_SHOTGUN.get());
                output.accept(ModItems.CYBERPUNK_SHOVEL.get());
                output.accept(ModItems.CYBERPUNK_SPEAR.get());
                output.accept(ModItems.CYBERPUNK_SWORD.get());
                output.accept(ModItems.CYBERPUNK_HAT.get());
            }).build());
}
