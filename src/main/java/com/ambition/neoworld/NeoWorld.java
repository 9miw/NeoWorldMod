package com.ambition.neoworld;

import org.slf4j.Logger;

import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.registry.ModBlocks;
import com.ambition.neoworld.registry.ModCreativeTabs;
import com.ambition.neoworld.registry.ModDataComponents;
import com.ambition.neoworld.datagen.ModDungeonDatapackProvider;
import com.ambition.neoworld.registry.ModItems;
import com.ambition.neoworld.registry.ModPayloads;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// ค่าตรงนี้ต้องตรงกับที่ระบุไว้ในไฟล์ META-INF/neoforge.mods.toml
@Mod(NeoWorld.MODID)
public class NeoWorld {
    public static final String MODID = "neoworld";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NeoWorld(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::gatherData);

        // ลงทะเบียนระบบรีจิสเตอร์ต่างๆ
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);

        // ลงทะเบียน Network Payloads
        modEventBus.addListener(ModPayloads::register);

        // ลงทะเบียนคลาสนี้เข้ากับ NeoForge Event Bus
        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());
        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    private void gatherData(GatherDataEvent event) {
        if (event.includeServer()) {
            event.addProvider(new ModDungeonDatapackProvider(
                    event.getGenerator().getPackOutput(),
                    event.getLookupProvider()
            ));
        }

        if (event.includeClient()) {
            event.createProvider(output -> new com.ambition.neoworld.datagen.ModLanguageProvider(output, "en_us"));
            event.createProvider(output -> new com.ambition.neoworld.datagen.ModLanguageProvider(output, "th_th"));
        }
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModItems.EXAMPLE_BLOCK_ITEM);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }
}
