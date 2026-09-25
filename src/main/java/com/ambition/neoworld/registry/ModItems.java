package com.ambition.neoworld.registry;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.ambition.neoworld.item.CoinItem;

// คลาสสำหรับลงทะเบียนไอเทม (Item) ทั้งหมดของมอด
public class ModItems {
    // Deferred Register สำหรับไอเทมที่อยู่ภายใต้ namespace "neoworld"
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NeoWorld.MODID);

    // ไอเทมบล็อกตัวอย่าง "neoworld:example_block" ที่ผูกกับบล็อก EXAMPLE_BLOCK
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", ModBlocks.EXAMPLE_BLOCK);

    // ไอเทมอาหารตัวอย่าง "neoworld:example_item" มีค่าความอิ่ม 1 และ Saturation 2 สามารถกินได้ตลอดเวลา
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder()
            .alwaysEdible().nutrition(1).saturationModifier(2f).build()));

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
}
