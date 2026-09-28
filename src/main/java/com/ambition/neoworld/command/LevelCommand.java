package com.ambition.neoworld.command;

import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.level.LevelFormula;
import com.ambition.neoworld.level.ItemLevelEventHandler;
import com.ambition.neoworld.level.PlayerLevelManager;
import com.ambition.neoworld.item.ExperienceItem;
import com.ambition.neoworld.permission.ModPermissions;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.registry.ModDataComponents;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// คำสั่งในเกมสำหรับจัดการระบบเลเวลและ EXP (รองรับ LuckPerms)
public final class LevelCommand {

    private LevelCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("level")
            .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_LEVEL_GET, 0))
            // พิมพ์ /neoworld level หรือ /neow level เปล่าๆ -> แสดงเลเวลและ EXP ของตนเองทันที
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                showLevel(player);
                showHelp(player);
                return 1;
            })
            // /neoworld level help
            .then(Commands.literal("help")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    showHelp(player);
                    return 1;
                })
            )
            // /neoworld level get
            .then(Commands.literal("get")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_LEVEL_GET, 0))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    showLevel(player);
                    showHelp(player);
                    return 1;
                })
            )
            // /neoworld level addexp <amount> [player]
            .then(Commands.literal("addexp")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_LEVEL_ADDEXP, 2))
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        long amount = LongArgumentType.getLong(context, "amount");
                        PlayerLevelManager.addExp(player, amount);
                        context.getSource().sendSuccess(() ->
                            Component.literal("§a[NeoWorld] §fเพิ่ม EXP จำนวน §e+" + amount + " §fให้ตัวเองแล้ว"), false);
                        return 1;
                    })
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            long amount = LongArgumentType.getLong(context, "amount");
                            PlayerLevelManager.addExp(target, amount);
                            context.getSource().sendSuccess(() ->
                                Component.literal("§a[NeoWorld] §fเพิ่ม EXP จำนวน §e+" + amount + " §fให้ §b" + target.getName().getString() + " §fแล้ว"), true);
                            return 1;
                        })
                    )
                )
            )
            // /neoworld level set <level> [player]
            .then(Commands.literal("set")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_LEVEL_SET, 2))
                .then(Commands.argument("level", IntegerArgumentType.integer(1, LevelFormula.MAX_LEVEL))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        int level = IntegerArgumentType.getInteger(context, "level");
                        PlayerLevelManager.setLevel(player, level);
                        context.getSource().sendSuccess(() ->
                            Component.literal("§a[NeoWorld] §fตั้งค่าเลเวลให้ตัวเองเป็น: §e§l" + level), false);
                        return 1;
                    })
                    .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            int level = IntegerArgumentType.getInteger(context, "level");
                            PlayerLevelManager.setLevel(target, level);
                            context.getSource().sendSuccess(() ->
                                Component.literal("§a[NeoWorld] §fตั้งค่าเลเวลให้ §b" + target.getName().getString() + " §fเป็น: §e§l" + level), true);
                            return 1;
                        })
                    )
                )
            )
            // /neoworld level item set <level> และ /neoworld level item clear สำหรับไอเทมในมือ
            .then(Commands.literal("item")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_LEVEL_SET, 2))
                .then(Commands.literal("set")
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, LevelFormula.MAX_LEVEL))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ItemStack held = player.getMainHandItem();
                            if (held.isEmpty()) {
                                player.sendSystemMessage(Component.literal("§c[NeoWorld] §fกรุณาถือไอเทมที่ต้องการตั้งเลเวล"));
                                return 0;
                            }

                            int level = IntegerArgumentType.getInteger(context, "level");
                            held.set(ModDataComponents.REQUIRED_LEVEL, level);
                            player.sendSystemMessage(Component.literal("§a[NeoWorld] §fตั้งเลเวลไอเทมในมือเป็น §e" + level + " §fแล้ว"));
                            return 1;
                        })
                    )
                )
                .then(Commands.literal("clear")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        ItemStack held = player.getMainHandItem();
                        if (held.isEmpty()) {
                            player.sendSystemMessage(Component.literal("§c[NeoWorld] §fกรุณาถือไอเทมที่ต้องการล้างเลเวล"));
                            return 0;
                        }

                        held.remove(ModDataComponents.REQUIRED_LEVEL);
                        int fallbackLevel = ItemLevelEventHandler.getRequiredLevel(held);
                        if (fallbackLevel > 0) {
                            player.sendSystemMessage(Component.literal("§a[NeoWorld] §fล้างเลเวลเฉพาะไอเทมแล้ว ตอนนี้ใช้ค่าจาก config: §e" + fallbackLevel));
                        } else {
                            player.sendSystemMessage(Component.literal("§a[NeoWorld] §fล้างเลเวลไอเทมในมือแล้ว"));
                        }
                        return 1;
                    })
                )
            )
            // /neoworld level expitem set <minimum> <maximum>
            .then(Commands.literal("expitem")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_LEVEL_SET, 2))
                .then(Commands.literal("set")
                    .then(Commands.argument("minimum", LongArgumentType.longArg(1))
                        .then(Commands.argument("maximum", LongArgumentType.longArg(1))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ItemStack held = player.getMainHandItem();
                            if (!(held.getItem() instanceof ExperienceItem)) {
                                context.getSource().sendFailure(Component.literal(
                                        "§c[NeoWorld] กรุณาถือไอเทม NeoWorld EXP ในมือหลักก่อนใช้คำสั่งนี้"));
                                return 0;
                            }

                            long minimum = LongArgumentType.getLong(context, "minimum");
                            long maximum = LongArgumentType.getLong(context, "maximum");
                            if (maximum < minimum) {
                                context.getSource().sendFailure(Component.literal("§c[NeoWorld] ค่าสูงสุดต้องไม่น้อยกว่าค่าต่ำสุด"));
                                return 0;
                            }
                            held.set(ModDataComponents.EXP_MIN_VALUE, minimum);
                            held.set(ModDataComponents.EXP_VALUE, maximum);
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "§a[NeoWorld] §fตั้งค่าช่วงไอเทม EXP เป็น §b" + String.format("%,d", minimum)
                                            + " - " + String.format("%,d", maximum) + " EXP แล้ว"), false);
                            return 1;
                        })
                        )
                    )
                )
            );
    }

    private static void showLevel(ServerPlayer player) {
        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        long maxExp = LevelFormula.getRequiredExpForNextLevel(data.getLevel());
        player.sendSystemMessage(Component.literal(
            "§a[NeoWorld] §fระดับเลเวล: §e§l" + data.getLevel() +
            " §f| EXP: §b" + data.getExp() + " / " + maxExp
        ));
    }

    private static void showHelp(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6=== คำสั่ง Level ที่คลิกได้ ==="));
        player.sendSystemMessage(commandButton("/neoworld level get", "ดูเลเวลของตัวเอง", true));
        player.sendSystemMessage(commandButton("/neoworld level addexp 100", "เพิ่ม EXP ให้ตัวเอง", false));
        player.sendSystemMessage(commandButton("/neoworld level set 1", "ตั้งเลเวลตัวเอง", false));
        player.sendSystemMessage(commandButton("/neoworld level item set 1", "ตั้งเลเวลไอเทมในมือ", false));
        player.sendSystemMessage(commandButton("/neoworld level item clear", "ล้างเลเวลไอเทมในมือ", true));
        player.sendSystemMessage(commandButton("/neoworld level expitem set 10 100", "ตั้งช่วงสุ่ม EXP ในมือ", false));
    }

    private static MutableComponent commandButton(String command, String description, boolean runOnClick) {
        ClickEvent.Action action = runOnClick ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND;
        String clickHint = runOnClick ? "คลิกเพื่อรันคำสั่ง" : "คลิกเพื่อเติมคำสั่งในช่องแชท";
        return Component.literal("  " + command)
            .withStyle(style -> style
                .withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent(action, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(clickHint + ": " + command))))
            .append(Component.literal(" §7- §f" + description));
    }
}
