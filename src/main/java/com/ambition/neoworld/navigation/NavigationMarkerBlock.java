package com.ambition.neoworld.navigation;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.*;

public final class NavigationMarkerBlock extends BaseEntityBlock {
    public static final MapCodec<NavigationMarkerBlock> CODEC=simpleCodec(NavigationMarkerBlock::new);
    public NavigationMarkerBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return context instanceof EntityCollisionContext entity && entity.getEntity() instanceof Player player && player.isCreative()
                ? Block.box(3,0,3,13,10,13) : Shapes.empty();
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new MarkerEntity(pos,state); }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved) {
        super.onPlace(state,level,pos,old,moved);
        if(!old.is(this) && level instanceof ServerLevel server) NavigationSavedData.get(server.getServer()).placed(server,pos);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved) {
        if(!next.is(this) && level instanceof ServerLevel server) NavigationSavedData.get(server.getServer()).removed(server,pos);
        super.onRemove(state,level,pos,next,moved);
    }
    public static final class MarkerEntity extends BlockEntity {
        public MarkerEntity(BlockPos pos,BlockState state) { super(NavigationRegistry.MARKER_ENTITY.get(),pos,state); }
    }
}
