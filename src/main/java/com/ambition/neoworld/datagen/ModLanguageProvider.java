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
        add("itemGroup.neoworld", "NeoWorld");
        addBlock(ModBlocks.EXAMPLE_BLOCK, "Example Block");
        addItem(ModItems.EXAMPLE_ITEM, "Example Item");
        addItem(ModItems.EXAMPLE_ITEMSWORD, "Example Item Sword");
        addItem(ModItems.SILVER_COIN, "Silver Coin");
        addItem(ModItems.GOLD_COIN, "Gold Coin");
        addItem(ModItems.DIAMOND_COIN, "Diamond Coin");
        addItem(ModItems.EXPERIENCE_ITEM, "NeoWorld EXP");

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
        add("itemGroup.neoworld", "NeoWorld");
        addBlock(ModBlocks.EXAMPLE_BLOCK, "บล็อกตัวอย่าง");
        addItem(ModItems.EXAMPLE_ITEM, "ไอเทมตัวอย่าง");
        addItem(ModItems.EXAMPLE_ITEMSWORD, "ดาบไอเทมตัวอย่าง");
        addItem(ModItems.SILVER_COIN, "เหรียญเงิน (Silver Coin)");
        addItem(ModItems.GOLD_COIN, "เหรียญทอง (Gold Coin)");
        addItem(ModItems.DIAMOND_COIN, "เหรียญเพชร (Diamond Coin)");
        addItem(ModItems.EXPERIENCE_ITEM, "ไอเทม EXP ของ NeoWorld");

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
