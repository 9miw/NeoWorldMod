package com.ambition.neoworld.datagen;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModBlocks;
import com.ambition.neoworld.registry.ModItems;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageProvider extends LanguageProvider {
    private final String locale;

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, NeoWorld.MODID, locale);
        this.locale = locale;
    }

    @Override
    protected void addTranslations() {
        if ("th_th".equals(locale)) {
            addThaiTranslations();
        } else {
            addEnglishTranslations();
        }
    }

    private void addEnglishTranslations() {
        add("tooltip.neoworld.fashion.type", "Fashion unlock item");
        add("tooltip.neoworld.fashion.slot", "Slot: %s");
        add("tooltip.neoworld.fashion.slot.head", "Head");
        add("tooltip.neoworld.fashion.slot.face", "Face");
        add("tooltip.neoworld.fashion.slot.back", "Back");
        add("tooltip.neoworld.fashion.slot.body", "Body");
        add("tooltip.neoworld.fashion.slot.legs", "Legs");
        add("tooltip.neoworld.fashion.slot.feet", "Feet");
        add("tooltip.neoworld.fashion.permanent", "Permanently unlocks in your wardrobe");
        add("tooltip.neoworld.fashion.usage", "How to use");
        add("tooltip.neoworld.fashion.right_click", "Hold the item and right-click");
        add("tooltip.neoworld.fashion.consume", "Consumes 1 item on successful unlock");
        add("tooltip.neoworld.fashion.equip", "Select and equip in the fashion screen");
        add("tooltip.neoworld.fashion.owned", "Already in your wardrobe");
        add("tooltip.neoworld.fashion.duplicate", "Duplicates will not consume the item");

        add("item.neoworld.upgrade_stone", "Upgrade Stone");
        add("item.neoworld.advanced_upgrade_stone", "Advanced Upgrade Stone");
        add("item.neoworld.supreme_upgrade_stone", "Supreme Upgrade Stone");
        add("item.neoworld.downgrade_protection_scroll", "Downgrade Protection Scroll");
        add("item.neoworld.break_protection_scroll", "Break Protection Scroll");
        add("block.neoworld.upgrade_table", "Upgrade Table");
        add("key.neoworld.player_actions", "Player actions");
        add("key.categories.neoworld", "NeoWorld");
        add("itemGroup.neoworld", "NeoWorld");
        addBlock(ModBlocks.EXAMPLE_BLOCK, "Example Block");
        addItem(ModItems.EXAMPLE_ITEM, "Example Item");
        addItem(ModItems.EXAMPLE_ITEMSWORD, "Example Item Sword");
        addItem(ModItems.DUELIST_ARMOR_CHEST, "เสื้อเกราะดูเอลลิสต์");
        addItem(ModItems.DUELIST_ARMOR_LEGGINGS, "กางเกงเกราะดูเอลลิสต์");
        addItem(ModItems.DUELIST_ARMOR_BOOTS, "รองเท้าเกราะดูเอลลิสต์");
        addItem(ModItems.DUELIST_ARMOR_HELMET, "หมวกเกราะดูเอลลิสต์");
        addItem(ModItems.DUELIST_DIAMOND_ARMOR_CHEST, "เสื้อเกราะดูเอลลิสต์เพชร");
        addItem(ModItems.DUELIST_DIAMOND_ARMOR_LEGGINGS, "กางเกงเกราะดูเอลลิสต์เพชร");
        addItem(ModItems.DUELIST_DIAMOND_ARMOR_BOOTS, "รองเท้าเกราะดูเอลลิสต์เพชร");
        addItem(ModItems.DUELIST_DIAMOND_ARMOR_HELMET, "หมวกเกราะดูเอลลิสต์เพชร");
        addItem(ModItems.DUELIST_GOLDEN_ARMOR_CHEST, "เสื้อเกราะดูเอลลิสต์ทอง");
        addItem(ModItems.DUELIST_GOLDEN_ARMOR_LEGGINGS, "กางเกงเกราะดูเอลลิสต์ทอง");
        addItem(ModItems.DUELIST_GOLDEN_ARMOR_BOOTS, "รองเท้าเกราะดูเอลลิสต์ทอง");
        addItem(ModItems.DUELIST_GOLDEN_ARMOR_HELMET, "หมวกเกราะดูเอลลิสต์ทอง");
        addItem(ModItems.DUELIST_NETHERITE_ARMOR_CHEST, "เสื้อเกราะดูเอลลิสต์เนเธอไรต์");
        addItem(ModItems.DUELIST_NETHERITE_ARMOR_LEGGINGS, "กางเกงเกราะดูเอลลิสต์เนเธอไรต์");
        addItem(ModItems.DUELIST_NETHERITE_ARMOR_BOOTS, "รองเท้าเกราะดูเอลลิสต์เนเธอไรต์");
        addItem(ModItems.DUELIST_NETHERITE_ARMOR_HELMET, "หมวกเกราะดูเอลลิสต์เนเธอไรต์");
        addItem(ModItems.SILVER_COIN, "Silver Coin");
        addItem(ModItems.GOLD_COIN, "Gold Coin");
        addItem(ModItems.DIAMOND_COIN, "Diamond Coin");
        addItem(ModItems.EXPERIENCE_ITEM, "NeoWorld EXP");
        addItem(ModItems.FASHION_EXAMPLE_HAT, "Example Fashion Hat");
        addItem(ModItems.DUELIST_IRON_HEAD, "Iron Duelist Hat");
        addItem(ModItems.DUELIST_IRON_BODY, "Iron Duelist Coat");
        addItem(ModItems.DUELIST_IRON_LEGS, "Iron Duelist Leggings");
        addItem(ModItems.DUELIST_IRON_FEET, "Iron Duelist Boots");

        add("tooltip.neoworld.silver_coin.desc", "Common Currency - Used for general trading & market");
        add("tooltip.neoworld.gold_coin.desc", "High-tier Currency - Used for enhancement & blacksmith upgrades");
        add("tooltip.neoworld.diamond_coin.desc", "Premium Currency - Used for cash shop, cosmetics & ranks");
        add("tooltip.neoworld.coin.action", "Right-Click to deposit 1 | Shift + Right-Click to deposit all");

        add("message.neoworld.deposit_silver", "Deposited %s Silver Coin(s). Current Balance: %s Silver");
        add("message.neoworld.deposit_gold", "Deposited %s Gold Coin(s). Current Balance: %s Gold");
        add("message.neoworld.deposit_diamond", "Deposited %s Diamond Coin(s). Current Balance: %s Diamond");

        addEnglishConfigTranslations();
    }

    private void addThaiTranslations() {
        add("tooltip.neoworld.fashion.type", "ไอเทมปลดล็อกแฟชั่น");
        add("tooltip.neoworld.fashion.slot", "ตำแหน่ง: %s");
        add("tooltip.neoworld.fashion.slot.head", "ศีรษะ");
        add("tooltip.neoworld.fashion.slot.face", "ใบหน้า");
        add("tooltip.neoworld.fashion.slot.back", "หลัง");
        add("tooltip.neoworld.fashion.slot.body", "ลำตัว");
        add("tooltip.neoworld.fashion.slot.legs", "ขา");
        add("tooltip.neoworld.fashion.slot.feet", "เท้า");
        add("tooltip.neoworld.fashion.permanent", "ปลดล็อกเข้าคลังแฟชั่นถาวร");
        add("tooltip.neoworld.fashion.usage", "วิธีใช้");
        add("tooltip.neoworld.fashion.right_click", "ถือไอเทมแล้วคลิกขวา");
        add("tooltip.neoworld.fashion.consume", "ใช้ไอเทม 1 ชิ้นเมื่อปลดล็อกสำเร็จ");
        add("tooltip.neoworld.fashion.equip", "เลือกสวมใส่ในหน้าแฟชั่น");
        add("tooltip.neoworld.fashion.owned", "มีในคลังแล้ว");
        add("tooltip.neoworld.fashion.duplicate", "มีในคลังแล้วจะไม่เสียไอเทม");

        add("item.neoworld.upgrade_stone", "หินตีบวกพื้นฐาน");
        add("item.neoworld.advanced_upgrade_stone", "หินตีบวกขั้นสูง");
        add("item.neoworld.supreme_upgrade_stone", "หินตีบวกขั้นสูงสุด");
        add("item.neoworld.downgrade_protection_scroll", "ใบป้องกันระดับลด");
        add("item.neoworld.break_protection_scroll", "ใบป้องกันแตก");
        add("block.neoworld.upgrade_table", "โต๊ะตีบวก");
        add("key.neoworld.player_actions", "เมนูแอคชั่นผู้เล่น");
        add("key.categories.neoworld", "NeoWorld");
        add("itemGroup.neoworld", "NeoWorld");
        addBlock(ModBlocks.EXAMPLE_BLOCK, "บล็อกตัวอย่าง");
        addItem(ModItems.EXAMPLE_ITEM, "ไอเทมตัวอย่าง");
        addItem(ModItems.EXAMPLE_ITEMSWORD, "ดาบไอเทมตัวอย่าง");
        addItem(ModItems.DUELIST_ARMOR_CHEST, "เสื้อเกราะดูเอลลิสต์");
        addItem(ModItems.DUELIST_ARMOR_LEGGINGS, "กางเกงเกราะดูเอลลิสต์");
        addItem(ModItems.DUELIST_ARMOR_BOOTS, "รองเท้าเกราะดูเอลลิสต์");
        addItem(ModItems.DUELIST_ARMOR_HELMET, "หมวกเกราะดูเอลลิสต์");
        addItem(ModItems.DUELIST_DIAMOND_ARMOR_CHEST, "เสื้อเกราะดูเอลลิสต์เพชร");
        addItem(ModItems.DUELIST_DIAMOND_ARMOR_LEGGINGS, "กางเกงเกราะดูเอลลิสต์เพชร");
        addItem(ModItems.DUELIST_DIAMOND_ARMOR_BOOTS, "รองเท้าเกราะดูเอลลิสต์เพชร");
        addItem(ModItems.DUELIST_DIAMOND_ARMOR_HELMET, "หมวกเกราะดูเอลลิสต์เพชร");
        addItem(ModItems.DUELIST_GOLDEN_ARMOR_CHEST, "เสื้อเกราะดูเอลลิสต์ทอง");
        addItem(ModItems.DUELIST_GOLDEN_ARMOR_LEGGINGS, "กางเกงเกราะดูเอลลิสต์ทอง");
        addItem(ModItems.DUELIST_GOLDEN_ARMOR_BOOTS, "รองเท้าเกราะดูเอลลิสต์ทอง");
        addItem(ModItems.DUELIST_GOLDEN_ARMOR_HELMET, "หมวกเกราะดูเอลลิสต์ทอง");
        addItem(ModItems.DUELIST_NETHERITE_ARMOR_CHEST, "เสื้อเกราะดูเอลลิสต์เนเธอไรต์");
        addItem(ModItems.DUELIST_NETHERITE_ARMOR_LEGGINGS, "กางเกงเกราะดูเอลลิสต์เนเธอไรต์");
        addItem(ModItems.DUELIST_NETHERITE_ARMOR_BOOTS, "รองเท้าเกราะดูเอลลิสต์เนเธอไรต์");
        addItem(ModItems.DUELIST_NETHERITE_ARMOR_HELMET, "หมวกเกราะดูเอลลิสต์เนเธอไรต์");
        addItem(ModItems.SILVER_COIN, "เหรียญเงิน (Silver Coin)");
        addItem(ModItems.GOLD_COIN, "เหรียญทอง (Gold Coin)");
        addItem(ModItems.DIAMOND_COIN, "เหรียญเพชร (Diamond Coin)");
        addItem(ModItems.EXPERIENCE_ITEM, "ไอเทม EXP ของ NeoWorld");
        addItem(ModItems.FASHION_EXAMPLE_HAT, "หมวกแฟชั่นตัวอย่าง");
        addItem(ModItems.DUELIST_IRON_HEAD, "หมวกดูเอลลิสต์เหล็ก");
        addItem(ModItems.DUELIST_IRON_BODY, "เสื้อดูเอลลิสต์เหล็ก");
        addItem(ModItems.DUELIST_IRON_LEGS, "กางเกงดูเอลลิสต์เหล็ก");
        addItem(ModItems.DUELIST_IRON_FEET, "รองเท้าดูเอลลิสต์เหล็ก");

        add("tooltip.neoworld.silver_coin.desc", "สกุลเงินทั่วไป - ใช้จ่ายและซื้อขายทั่วไปในเซิร์ฟเวอร์");
        add("tooltip.neoworld.gold_coin.desc", "สกุลเงินชั้นสูง - ใช้สำหรับระบบตีบวกและอัปเกรดอุปกรณ์");
        add("tooltip.neoworld.diamond_coin.desc", "สกุลเงินพรีเมียม - เงินเติมสำหรับแคชช็อป, ยศ, และแฟชั่น");
        add("tooltip.neoworld.coin.action", "คลิกขวาเพื่อฝาก 1 เหรียญ | Shift + คลิกขวาเพื่อฝากทั้งหมด");

        add("message.neoworld.deposit_silver", "ฝากเหรียญเงินสำเร็จ +%s เหรียญ (ยอดปัจจุบัน: %s Silver)");
        add("message.neoworld.deposit_gold", "ฝากเหรียญทองสำเร็จ +%s เหรียญ (ยอดปัจจุบัน: %s Gold)");
        add("message.neoworld.deposit_diamond", "ฝากเหรียญเพชรสำเร็จ +%s เหรียญ (ยอดปัจจุบัน: %s Diamond)");

        addThaiConfigTranslations();
    }

    private void addEnglishConfigTranslations() {
        add("neoworld.configuration.title", "NeoWorld Configs");
        add("neoworld.configuration.section.neoworld.common.toml", "NeoWorld Configs");
        add("neoworld.configuration.section.neoworld.common.toml.title", "NeoWorld Configs");
        add("neoworld.configuration.items", "Item List");
        add("neoworld.configuration.logDirtBlock", "Log Dirt Block");
        add("neoworld.configuration.magicNumberIntroduction", "Magic Number Text");
        add("neoworld.configuration.magicNumber", "Magic Number");
    }

    private void addThaiConfigTranslations() {
        add("neoworld.configuration.title", "การตั้งค่า NeoWorld");
        add("neoworld.configuration.section.neoworld.common.toml", "การตั้งค่าทั่วไป");
        add("neoworld.configuration.section.neoworld.common.toml.title", "การตั้งค่าทั่วไป");
        add("neoworld.configuration.items", "รายการไอเทม");
        add("neoworld.configuration.logDirtBlock", "บันทึก Log บล็อกดิน");
        add("neoworld.configuration.magicNumberIntroduction", "ข้อความ Magic Number");
        add("neoworld.configuration.magicNumber", "ตัวเลข Magic Number");
    }
}
