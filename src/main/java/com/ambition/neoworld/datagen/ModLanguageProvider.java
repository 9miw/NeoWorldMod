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
        boolean thai = "th_th".equals(locale);
        add("gui.neoworld.loot_box.effects_on", thai ? "เอฟเฟกต์: เปิด" : "Effects: On");
        add("gui.neoworld.loot_box.effects_off", thai ? "เอฟเฟกต์: ปิด" : "Effects: Off");
        add("gui.neoworld.loot_box.additional", thai ? "จำนวนกล่องที่จะเปิดเพิ่ม (1–64)" : "Additional boxes to open (1–64)");
        add("gui.neoworld.loot_box.auto", thai ? "เปิดเพิ่มอัตโนมัติ" : "Auto-open more");
        add("gui.neoworld.loot_box.stop", thai ? "หยุด" : "Stop");
        add("gui.neoworld.loot_box.remaining", thai ? "เหลือ %s กล่อง" : "%s boxes left");
        add("gui.neoworld.loot_box.save_failed", thai ? "เปลี่ยนเอฟเฟกต์แล้ว แต่บันทึกค่าลงไฟล์ไม่สำเร็จ" : "Effects changed, but the preference could not be saved");
        addItem(ModItems.LOOT_BOX, thai ? "กล่องสุ่ม (ทดสอบ)" : "Loot Box (Test)");
        add("item.neoworld.loot_box.configured", thai ? "กล่องสุ่ม: %s" : "Loot Box: %s");
        add("tooltip.neoworld.loot_box.use", thai ? "คลิกขวาเพื่อเปิดกล่องและรับรางวัลสุ่ม" : "Right-click to open and receive random rewards");
        add("message.neoworld.loot_box.unconfigured", thai ? "รางวัลหรือจำนวนที่สุ่มไม่ถูกต้อง ยังไม่หักกล่อง" : "Invalid rewards or draw count. No box was consumed.");
        add("gui.neoworld.loot_box.rewards", thai ? "ได้รับรางวัล %s รายการ" : "Received %s reward entries");
        add("gui.neoworld.loot_box.empty", thai ? "ไม่ได้รับรางวัล" : "No rewards");
        add("message.neoworld.loot_box.full", thai ? "พื้นที่กระเป๋าไม่เพียงพอ ยังไม่หักกล่อง" : "Not enough inventory space. No box was consumed.");
        add("gui.neoworld.loot_box.title", thai ? "เปิดกล่องสุ่ม" : "Opening Loot Box");
        add("gui.neoworld.loot_box.shaking", thai ? "มีอะไรขยับอยู่ข้างใน…" : "Something is moving inside…");
        add("gui.neoworld.loot_box.opening", thai ? "กำลังเปิดกล่อง…" : "Opening…");
        add("gui.neoworld.loot_box.reward", thai ? "ได้รับ: %s × %s" : "Received: %s × %s");
        add("message.neoworld.combat_logout.warning", thai
                ? "กำลังต่อสู้: %s วินาที · ออกเกมแล้วตัวละครจะอยู่ต่อ 60 วินาที"
                : "In combat: %s seconds · Logging out leaves your character for 60 seconds");
        add("message.neoworld.combat_logout.safe", thai
                ? "พ้นสถานะต่อสู้แล้ว สามารถออกเกมได้ตามปกติ"
                : "Combat ended. You can log out normally.");
        addItem(ModItems.DRAGON_HELMET, thai ? "หมวกเกราะมังกร" : "Dragon Helmet");
        addItem(ModItems.DRAGON_CHESTPLATE, thai ? "เสื้อเกราะมังกร" : "Dragon Chestplate");
        addItem(ModItems.DRAGON_LEGGINGS, thai ? "กางเกงเกราะมังกร" : "Dragon Leggings");
        addItem(ModItems.DRAGON_BOOTS, thai ? "รองเท้าเกราะมังกร" : "Dragon Boots");
        add("gui.neoworld.quest_navigation.track", "th_th".equals(locale) ? "ติดตาม" : "Track");
        add("gui.neoworld.quest_navigation.track_hint", "th_th".equals(locale) ? "ติดตามเควสต์ที่เลือกและกลับเข้าเกม" : "Track the selected quest and return to the world");
        add("gui.neoworld.quest_navigation.select_hint", "th_th".equals(locale) ? "เลือกเควสต์ก่อนเพื่อติดตาม" : "Select a quest first");
        add("block.neoworld.navigation_marker", "th_th".equals(locale) ? "จุดนำทางเควสต์ (Creative)" : "Quest Navigation Marker (Creative)");
        if ("th_th".equals(locale)) {
            addThaiTranslations();
        } else {
            addEnglishTranslations();
        }
    }

    private void addEnglishTranslations() {
        add("tooltip.neoworld.shared.scroll.protection", "Prevents downgrade and reset; consumed on every attempt.");
        add("tooltip.neoworld.shared.scroll.booster", "Adds 10 percentage points to success, capped at 100; consumed every attempt.");
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
        add("item.neoworld.protection_scroll", "Upgrade Protection Scroll");
        add("item.neoworld.success_booster", "Success Booster Stone");
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
        addItem(ModItems.DIAMOND_COIN, "Diamond Coin");
        addItem(ModItems.EXPERIENCE_ITEM, "NeoWorld EXP");
        addItem(ModItems.FASHION_EXAMPLE_HAT, "Example Fashion Hat");
        addItem(ModItems.DUELIST_IRON_HEAD, "Iron Duelist Hat");
        addItem(ModItems.DUELIST_IRON_BODY, "Iron Duelist Coat");
        addItem(ModItems.DUELIST_IRON_LEGS, "Iron Duelist Leggings");
        addItem(ModItems.DUELIST_IRON_FEET, "Iron Duelist Boots");

        add("tooltip.neoworld.silver_coin.desc", "Common Currency - Used for general trading & market");
        add("tooltip.neoworld.diamond_coin.desc", "Premium Currency - Used for cash shop, cosmetics & ranks");
        add("tooltip.neoworld.coin.action", "Right-Click to deposit 1 | Shift + Right-Click to deposit all");

        add("message.neoworld.deposit_silver", "Deposited %s Silver Coin(s). Current Balance: %s Silver");
        add("message.neoworld.deposit_diamond", "Deposited %s Diamond Coin(s). Current Balance: %s Diamond");

        addEnglishConfigTranslations();
    }

    private void addThaiTranslations() {
        add("tooltip.neoworld.shared.scroll.protection", "ป้องกันลดระดับและแตกกลับ +0 ใช้หมดทุกครั้งที่ตีบวก");
        add("tooltip.neoworld.shared.scroll.booster", "เพิ่มโอกาสสำเร็จ 10 จุดเปอร์เซ็นต์ สูงสุด 100 ใช้หมดทุกครั้งที่ตีบวก");
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
        add("item.neoworld.protection_scroll", "ใบป้องกันตีบวก");
        add("item.neoworld.success_booster", "หินเพิ่มโอกาสสำเร็จ");
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
        addItem(ModItems.DIAMOND_COIN, "เหรียญเพชร (Diamond Coin)");
        addItem(ModItems.EXPERIENCE_ITEM, "ไอเทม EXP ของ NeoWorld");
        addItem(ModItems.FASHION_EXAMPLE_HAT, "หมวกแฟชั่นตัวอย่าง");
        addItem(ModItems.DUELIST_IRON_HEAD, "หมวกดูเอลลิสต์เหล็ก");
        addItem(ModItems.DUELIST_IRON_BODY, "เสื้อดูเอลลิสต์เหล็ก");
        addItem(ModItems.DUELIST_IRON_LEGS, "กางเกงดูเอลลิสต์เหล็ก");
        addItem(ModItems.DUELIST_IRON_FEET, "รองเท้าดูเอลลิสต์เหล็ก");

        add("tooltip.neoworld.silver_coin.desc", "สกุลเงินทั่วไป - ใช้จ่ายและซื้อขายทั่วไปในเซิร์ฟเวอร์");
        add("tooltip.neoworld.diamond_coin.desc", "สกุลเงินพรีเมียม - เงินเติมสำหรับแคชช็อป, ยศ, และแฟชั่น");
        add("tooltip.neoworld.coin.action", "คลิกขวาเพื่อฝาก 1 เหรียญ | Shift + คลิกขวาเพื่อฝากทั้งหมด");

        add("message.neoworld.deposit_silver", "ฝากเหรียญเงินสำเร็จ +%s เหรียญ (ยอดปัจจุบัน: %s Silver)");
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
