package com.ambition.neoworld.command.fashion;

import com.ambition.neoworld.fashion.data.FashionData;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.ambition.neoworld.fashion.FashionSync;
import com.ambition.neoworld.network.fashion.OpenFashionPayload;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.registry.ModDataComponents;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FashionCommand {
    private FashionCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("fashion")
                .then(Commands.literal("open")
                        .executes(context -> openFashion(context.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(FashionSlot.values())
                                                .map(FashionSlot::serializedName),
                                        builder))
                                .executes(context -> setFashion(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "slot")))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(FashionSlot.values())
                                                .map(FashionSlot::serializedName),
                                        builder))
                                .executes(context -> clearFashion(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "slot")))))
                .then(Commands.literal("list")
                        .executes(context -> listFashion(context.getSource())));
    }

    private static int openFashion(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FashionSync.syncTo(player);
        PacketDistributor.sendToPlayer(player, OpenFashionPayload.INSTANCE);
        return 1;
    }

    private static int setFashion(CommandSourceStack source, String slotName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FashionSlot slot = parseSlot(source, slotName);
        if (slot == null) {
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("ถือไอเทมที่ต้องการใส่ช่องแฟชั่นก่อน"));
            return 0;
        }
        FashionSlot allowedSlot = held.get(ModDataComponents.FASHION_SLOT.get());
        if (allowedSlot == null) {
            source.sendFailure(Component.literal("ไอเทมนี้ไม่ใช่ไอเทมแฟชั่น"));
            return 0;
        }
        if (allowedSlot != slot) {
            source.sendFailure(Component.literal("ไอเทมนี้ใส่ได้เฉพาะช่อง " + allowedSlot.serializedName()));
            return 0;
        }

        FashionData data = player.getData(ModAttachments.FASHION_DATA);
        ItemStack fashionStack = data.owned(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()));
        if (fashionStack.isEmpty()) {
            source.sendFailure(Component.literal("คลิกขวาไอเทมเพื่อปลดล็อกแฟชั่นเข้าคลังก่อน"));
            return 0;
        }
        if (fashionStack.get(ModDataComponents.FASHION_SLOT.get()) != slot) {
            source.sendFailure(Component.literal("แฟชั่นในคลังไม่ตรงกับช่องที่เลือก"));
            return 0;
        }
        data.set(slot, fashionStack);
        FashionSync.syncToTrackingAndSelf(player);

        source.sendSuccess(() -> Component.literal("ตั้งค่าแฟชั่นช่อง " + slot.serializedName() + " เป็น ")
                .append(fashionStack.getHoverName())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int clearFashion(CommandSourceStack source, String slotName) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FashionSlot slot = parseSlot(source, slotName);
        if (slot == null) {
            return 0;
        }

        FashionData data = player.getData(ModAttachments.FASHION_DATA);
        ItemStack previous = data.clear(slot);
        if (previous.isEmpty()) {
            source.sendFailure(Component.literal("ช่องแฟชั่น " + slot.serializedName() + " ว่างอยู่แล้ว"));
            return 0;
        }

        FashionSync.syncToTrackingAndSelf(player);
        source.sendSuccess(() -> Component.literal("ถอดแฟชั่นช่อง " + slot.serializedName() + " แล้ว")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int listFashion(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FashionData data = player.getData(ModAttachments.FASHION_DATA);
        source.sendSuccess(() -> Component.literal("ช่องแฟชั่นของคุณ:").withStyle(ChatFormatting.AQUA), false);
        for (FashionSlot slot : FashionSlot.values()) {
            ItemStack stack = data.get(slot);
            Component itemName = stack.isEmpty() ? Component.literal("-") : stack.getHoverName();
            source.sendSuccess(() -> Component.literal(slot.serializedName() + ": ").append(itemName), false);
        }
        return 1;
    }

    private static FashionSlot parseSlot(CommandSourceStack source, String slotName) {
        return FashionSlot.fromName(slotName).orElseGet(() -> {
            source.sendFailure(Component.literal("ไม่พบช่องแฟชั่น '" + slotName + "' ใช้ได้: " + FashionSlot.names()));
            return null;
        });
    }

}
