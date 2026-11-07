package com.ambition.neoworld.upgrade;

import com.ambition.neoworld.economy.PlayerEconomyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class UpgradeTableBlock extends Block {
    public static final com.mojang.serialization.MapCodec<UpgradeTableBlock> CODEC = simpleCodec(UpgradeTableBlock::new);
    public UpgradeTableBlock(Properties properties) { super(properties); }
    @Override protected com.mojang.serialization.MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server && !player.isSpectator()) {
            long token = server.getRandom().nextLong();
            server.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new UpgradeMenu(id, inventory, pos, token),
                    Component.literal("ตีบวกอุปกรณ์")), buffer -> { buffer.writeBlockPos(pos); buffer.writeLong(token); });
            PlayerEconomyManager.syncToClient(server);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
