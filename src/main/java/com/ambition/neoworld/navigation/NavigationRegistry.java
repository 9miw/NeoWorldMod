package com.ambition.neoworld.navigation;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class NavigationRegistry {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(NeoWorld.MODID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(NeoWorld.MODID);
    private static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,NeoWorld.MODID);
    public static final DeferredBlock<NavigationMarkerBlock> MARKER=BLOCKS.register("navigation_marker",
            ()->new NavigationMarkerBlock(BlockBehaviour.Properties.of().strength(-1,3_600_000).noCollission().noOcclusion().noLootTable()));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> MARKER_ITEM=ITEMS.registerSimpleBlockItem("navigation_marker",MARKER);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<NavigationMarkerBlock.MarkerEntity>> MARKER_ENTITY=
            TYPES.register("navigation_marker",()->BlockEntityType.Builder.of(NavigationMarkerBlock.MarkerEntity::new,MARKER.get()).build(null));
    private NavigationRegistry() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); TYPES.register(bus); }
}
