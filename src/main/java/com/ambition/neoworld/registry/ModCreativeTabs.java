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
                output.accept(ModItems.EXAMPLE_ITEM.get()); // เพิ่มไอเทมเข้าไปในแท็บนี้
                output.accept(ModItems.EXAMPLE_ITEMSWORD.get()); // ดาบเลเวล 15 (ค่าเริ่มต้น)

                ItemStack level30Sword = new ItemStack(ModItems.EXAMPLE_ITEMSWORD.get());
                level30Sword.set(ModDataComponents.REQUIRED_LEVEL.get(), 30);
                output.accept(level30Sword); // ไอเทม ID เดียวกัน แต่ Data Component ต่างกัน

                output.accept(ModItems.SILVER_COIN.get());  // เหรียญเงิน
                output.accept(ModItems.GOLD_COIN.get());    // เหรียญทอง
                output.accept(ModItems.DIAMOND_COIN.get()); // เหรียญเพชร
                output.accept(ModItems.EXPERIENCE_ITEM.get()); // EXP
            }).build());
}
