package com.ambition.neoworld.registry;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.ambition.neoworld.item.fashion.SimpleFashionItem;
import com.ambition.neoworld.item.fashion.GeckoFashionItem;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TridentItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.ambition.neoworld.item.CoinItem;
import com.ambition.neoworld.item.ExperienceItem;

// คลาสสำหรับลงทะเบียนไอเทม (Item) ทั้งหมดของมอด
public class ModItems {
    // Deferred Register สำหรับไอเทมที่อยู่ภายใต้ namespace "neoworld"
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NeoWorld.MODID);

    public static final DeferredItem<Item> UPGRADE_STONE = ITEMS.registerSimpleItem("upgrade_stone");
    public static final DeferredItem<Item> ADVANCED_UPGRADE_STONE = ITEMS.registerSimpleItem("advanced_upgrade_stone");
    public static final DeferredItem<Item> SUPREME_UPGRADE_STONE = ITEMS.registerSimpleItem("supreme_upgrade_stone");
    public static final DeferredItem<Item> DOWNGRADE_PROTECTION_SCROLL = ITEMS.registerSimpleItem("downgrade_protection_scroll");
    public static final DeferredItem<Item> BREAK_PROTECTION_SCROLL = ITEMS.registerSimpleItem("break_protection_scroll");
    public static final DeferredItem<BlockItem> UPGRADE_TABLE = ITEMS.registerSimpleBlockItem("upgrade_table", ModBlocks.UPGRADE_TABLE);

    // Duelist armor uses the exported Epic Fight meshes and Iron Duelist texture.
    public static final DeferredItem<ArmorItem> DUELIST_ARMOR_CHEST = ITEMS.register("duelist_armor_chest",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_ARMOR_LEGGINGS = ITEMS.register("duelist_armor_leggings",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_ARMOR_HELMET = ITEMS.register("duelist_armor_helmet",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_ARMOR_BOOTS = ITEMS.register("duelist_armor_boots",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(15))));

    public static final DeferredItem<ArmorItem> DUELIST_DIAMOND_ARMOR_CHEST = ITEMS.register("duelist_diamond_armor_chest",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_DIAMOND_ARMOR_LEGGINGS = ITEMS.register("duelist_diamond_armor_leggings",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_DIAMOND_ARMOR_BOOTS = ITEMS.register("duelist_diamond_armor_boots",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_DIAMOND_ARMOR_HELMET = ITEMS.register("duelist_diamond_armor_helmet",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_GOLDEN_ARMOR_CHEST = ITEMS.register("duelist_golden_armor_chest",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_GOLDEN_ARMOR_LEGGINGS = ITEMS.register("duelist_golden_armor_leggings",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_GOLDEN_ARMOR_BOOTS = ITEMS.register("duelist_golden_armor_boots",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_GOLDEN_ARMOR_HELMET = ITEMS.register("duelist_golden_armor_helmet",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_NETHERITE_ARMOR_CHEST = ITEMS.register("duelist_netherite_armor_chest",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_NETHERITE_ARMOR_LEGGINGS = ITEMS.register("duelist_netherite_armor_leggings",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_NETHERITE_ARMOR_BOOTS = ITEMS.register("duelist_netherite_armor_boots",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(15))));
    public static final DeferredItem<ArmorItem> DUELIST_NETHERITE_ARMOR_HELMET = ITEMS.register("duelist_netherite_armor_helmet",
            () -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(15))));

    // ไอเทมบล็อกตัวอย่าง "neoworld:example_block" ที่ผูกกับบล็อก EXAMPLE_BLOCK
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", ModBlocks.EXAMPLE_BLOCK);

    // ไอเทมอาหารตัวอย่าง "neoworld:example_item" มีค่าความอิ่ม 1 และ Saturation 2 สามารถกินได้ตลอดเวลา
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // ดาบตัวอย่าง: 1 base damage + 46 item damage + 3 diamond tier bonus = 50 damage
    public static final DeferredItem<SwordItem> EXAMPLE_ITEMSWORD = ITEMS.register("example_itemsword",
            () -> new SwordItem(Tiers.DIAMOND, new Item.Properties()
                    .component(ModDataComponents.REQUIRED_LEVEL.get(), 15)
                    .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 46, -2.4f))));

    // === Lunar Set (เซตตรุษจีน - อาวุธ / Weapons) ===
    public static final DeferredItem<SwordItem> LUNAR_SWORD = ITEMS.register("lunar_sword",
            () -> new SwordItem(Tiers.NETHERITE, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 4.0f, -2.4f))));
    public static final DeferredItem<SwordItem> LUNAR_GREATSWORD = ITEMS.register("lunar_greatsword",
            () -> new SwordItem(Tiers.NETHERITE, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 8.0f, -2.8f))));
    public static final DeferredItem<AxeItem> LUNAR_HAMMER = ITEMS.register("lunar_hammer",
            () -> new AxeItem(Tiers.NETHERITE, new Item.Properties()
                    .attributes(AxeItem.createAttributes(Tiers.NETHERITE, 10.0f, -3.2f))));
    public static final DeferredItem<SwordItem> LUNAR_SCYTHE = ITEMS.register("lunar_scythe",
            () -> new SwordItem(Tiers.NETHERITE, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 7.0f, -2.6f))));
    public static final DeferredItem<SwordItem> LUNAR_SPEAR = ITEMS.register("lunar_spear",
            () -> new SwordItem(Tiers.NETHERITE, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 6.0f, -2.7f))));
    public static final DeferredItem<SwordItem> LUNAR_STAFF = ITEMS.register("lunar_staff",
            () -> new SwordItem(Tiers.NETHERITE, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 5.0f, -2.5f))));
    public static final DeferredItem<TridentItem> LUNAR_TRIDENT = ITEMS.register("lunar_trident",
            () -> new TridentItem(new Item.Properties().durability(500).attributes(TridentItem.createAttributes())));
    public static final DeferredItem<ShieldItem> LUNAR_SHIELD = ITEMS.register("lunar_shield",
            () -> new ShieldItem(new Item.Properties().durability(600)));

    // === Lunar Set (เซตตรุษจีน - เครื่องมือใช้งาน / Tools) ===
    public static final DeferredItem<AxeItem> LUNAR_AXE = ITEMS.register("lunar_axe",
            () -> new AxeItem(Tiers.NETHERITE, new Item.Properties()
                    .attributes(AxeItem.createAttributes(Tiers.NETHERITE, 5.0f, -3.0f))));
    public static final DeferredItem<PickaxeItem> LUNAR_PICKAXE = ITEMS.register("lunar_pickaxe",
            () -> new PickaxeItem(Tiers.NETHERITE, new Item.Properties()
                    .attributes(PickaxeItem.createAttributes(Tiers.NETHERITE, 1.0f, -2.8f))));
    public static final DeferredItem<ShovelItem> LUNAR_SHOVEL = ITEMS.register("lunar_shovel",
            () -> new ShovelItem(Tiers.NETHERITE, new Item.Properties()
                    .attributes(ShovelItem.createAttributes(Tiers.NETHERITE, 1.5f, -3.0f))));
    public static final DeferredItem<HoeItem> LUNAR_HOE = ITEMS.register("lunar_hoe",
            () -> new HoeItem(Tiers.NETHERITE, new Item.Properties()
                    .attributes(HoeItem.createAttributes(Tiers.NETHERITE, -4.0f, 0.0f))));

    // === สกุลเงินในเกม (Currencies) ===
    // เหรียญเงิน (Silver Coin) - สำหรับใช้จ่ายทั่วไปในเซิร์ฟเวอร์
    public static final DeferredItem<CoinItem> SILVER_COIN = ITEMS.register("silver_coin",
            () -> new CoinItem(CoinItem.CoinType.SILVER, new Item.Properties().stacksTo(64)));

    // เหรียญทอง (Gold Coin) - สกุลเงินชั้นสูงสำหรับระบบตีบวก / อัปเกรด
    public static final DeferredItem<CoinItem> GOLD_COIN = ITEMS.register("gold_coin",
            () -> new CoinItem(CoinItem.CoinType.GOLD, new Item.Properties().stacksTo(64)));

    // เหรียญเพชร (Diamond Coin) - สกุลเงินพรีเมียม / เงินเติม
    public static final DeferredItem<CoinItem> DIAMOND_COIN = ITEMS.register("diamond_coin",
            () -> new CoinItem(CoinItem.CoinType.DIAMOND, new Item.Properties().stacksTo(64)));

    public static final DeferredItem<ExperienceItem> EXPERIENCE_ITEM = ITEMS.register("experience_item",
            () -> new ExperienceItem(new Item.Properties().stacksTo(64)));

    public static final DeferredItem<SimpleFashionItem> FASHION_EXAMPLE_HAT = ITEMS.register("fashion_example_hat",
            () -> new SimpleFashionItem(FashionSlot.HEAD, new Item.Properties()
                    .stacksTo(1)
                    .component(ModDataComponents.FASHION_SLOT.get(), FashionSlot.HEAD)));
    public static final DeferredItem<SimpleFashionItem> ONI_CURSE_WINGS = ITEMS.register("oni_curse_wings",
            () -> new SimpleFashionItem(FashionSlot.BACK, new Item.Properties()
                    .stacksTo(1)
                    .component(ModDataComponents.FASHION_SLOT.get(), FashionSlot.BACK)));
    public static final DeferredItem<GeckoFashionItem> DUELIST_IRON_HEAD = duelist("duelist_iron_head", FashionSlot.HEAD);
    public static final DeferredItem<GeckoFashionItem> DUELIST_IRON_BODY = duelist("duelist_iron_body", FashionSlot.BODY);
    public static final DeferredItem<GeckoFashionItem> DUELIST_IRON_LEGS = duelist("duelist_iron_legs", FashionSlot.LEGS);
    public static final DeferredItem<GeckoFashionItem> DUELIST_IRON_FEET = duelist("duelist_iron_feet", FashionSlot.FEET);

    private static DeferredItem<GeckoFashionItem> duelist(String name, FashionSlot slot) {
        return ITEMS.register(name, () -> new GeckoFashionItem(slot, new Item.Properties().stacksTo(1)
                .component(ModDataComponents.FASHION_SLOT.get(), slot)));
    }
}
