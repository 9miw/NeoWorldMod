package com.ambition.neoworld.registry;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// คลาสสำหรับลงทะเบียนแท็บหมวดหมู่ในโหมดสร้างสรรค์ (Creative Mode Tabs) ของมอด
public class ModCreativeTabs {
    // Deferred Register สำหรับแท็บหมวดหมู่ Creative Mode
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NeoWorld.MODID);

    // แท็บตัวอย่าง "neoworld:example_tab" วางต่อจากแท็บ Combat (ต่อสู้) และใช้ไอคอนเป็น EXAMPLE_ITEM
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.neoworld")) // คีย์แปลภาษาสำหรับชื่อแท็บ
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.EXAMPLE_ITEM.get()); // เพิ่มไอเทมเข้าไปในแท็บนี้
                output.accept(ModItems.SILVER_COIN.get());  // เหรียญเงิน
                output.accept(ModItems.GOLD_COIN.get());    // เหรียญทอง
                output.accept(ModItems.DIAMOND_COIN.get()); // เหรียญเพชร
            }).build());
}
