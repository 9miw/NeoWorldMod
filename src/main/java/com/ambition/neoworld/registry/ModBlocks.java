package com.ambition.neoworld.registry;

import com.ambition.neoworld.NeoWorld;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

// คลาสสำหรับลงทะเบียนบล็อก (Block) ทั้งหมดของมอด
public class ModBlocks {
    // Deferred Register สำหรับบล็อกที่อยู่ภายใต้ namespace "neoworld"
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NeoWorld.MODID);

    public static final DeferredBlock<Block> UPGRADE_TABLE = BLOCKS.register("upgrade_table",
            () -> new com.ambition.neoworld.upgrade.UpgradeTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
                    .strength(3.5F).requiresCorrectToolForDrops()));

    public static final DeferredBlock<com.ambition.neoworld.block.CyberpunkChestBlock> CYBERPUNK_CHEST = BLOCKS.register("cyberpunk_chest",
            () -> new com.ambition.neoworld.block.CyberpunkChestBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.5F).noOcclusion().sound(net.minecraft.world.level.block.SoundType.METAL)));

    // บล็อกตัวอย่าง "neoworld:example_block" มีคุณสมบัติพื้นฐานและสีบนแผนที่เป็นสีหิน (MapColor.STONE)
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
}
