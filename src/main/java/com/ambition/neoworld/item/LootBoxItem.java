package com.ambition.neoworld.item;

import com.ambition.neoworld.lootbox.LootBoxService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import java.util.List;

public final class LootBoxItem extends Item {
    public LootBoxItem(Properties properties) { super(properties); }

    @Override public Component getName(ItemStack stack) {
        String id = stack.get(com.ambition.neoworld.registry.ModDataComponents.LOOT_BOX_ID.get());
        return id == null ? super.getName(stack) : Component.translatable("item.neoworld.loot_box.configured", id);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) return InteractionResultHolder.success(player.getItemInHand(hand));
        boolean opened = player instanceof ServerPlayer serverPlayer && LootBoxService.open(serverPlayer, hand);
        return opened ? InteractionResultHolder.consume(player.getItemInHand(hand))
                : InteractionResultHolder.fail(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return context.getPlayer() == null ? InteractionResult.PASS
                : use(context.getLevel(), context.getPlayer(), context.getHand()).getResult();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.neoworld.loot_box.use").withStyle(ChatFormatting.GRAY));
    }
}
