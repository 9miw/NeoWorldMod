package com.ambition.neoworld.item.fashion;

import com.ambition.neoworld.fashion.data.FashionSlot;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.fashion.FashionSync;

public class SimpleFashionItem extends Item implements FashionRenderItem {
    private final FashionSlot slot;

    public SimpleFashionItem(FashionSlot slot, Properties properties) {
        super(properties);
        this.slot = slot;
    }

    @Override
    public FashionSlot fashionSlot() {
        return slot;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            if (!player.getData(ModAttachments.FASHION_DATA).unlock(stack)) {
                player.displayClientMessage(Component.literal("คุณมีแฟชั่นนี้ในคลังแล้ว"), true);
                return InteractionResultHolder.fail(stack);
            }
            Component name = stack.getHoverName();
            stack.shrink(1);
            FashionSync.syncTo(serverPlayer);
            player.displayClientMessage(Component.literal("ปลดล็อกแฟชั่น: ").append(name), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
