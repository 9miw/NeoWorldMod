package com.ambition.neoworld.registry;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.block.CyberpunkChestBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NeoWorld.MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CyberpunkChestBlockEntity>> CYBERPUNK_CHEST =
            TYPES.register("cyberpunk_chest", () -> BlockEntityType.Builder.of(CyberpunkChestBlockEntity::new, ModBlocks.CYBERPUNK_CHEST.get()).build(null));
    private ModBlockEntities() {}
}
