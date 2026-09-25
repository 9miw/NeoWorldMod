package com.ambition.neoworld.command;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.level.LevelFormula;
import com.ambition.neoworld.level.PlayerLevelManager;
import com.ambition.neoworld.permission.ModPermissions;
import com.ambition.neoworld.registry.ModAttachments;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// คำสั่งในเกมสำหรับจัดการระบบเลเวลและ EXP (รองรับ LuckPerms)
@EventBusSubscriber(modid = NeoWorld.MODID)
public class LevelCommand {

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 1. ลงทะเบียนคำสั่งตรง: /level
        dispatcher.register(buildLevelTree("level"));

        // 2. ลงทะเบียนภายใต้คำสั่งหลัก: /neoworld level
        dispatcher.register(
            Commands.literal("neoworld")
                .then(buildLevelTree("level"))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildLevelTree(String rootName) {
        return Commands.literal(rootName)
            .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_LEVEL_GET, 0))
            // พิมพ์ /level เปล่าๆ -> แสดงเลเวลและ EXP ของตนเองทันที
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                showLevel(player);
                return 1;
            })
            // /level get
            .then(Commands.literal("get")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_LEVEL_GET, 0))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    showLevel(player);
                    return 1;
                })
            )
            // /level addexp <amount> [player]
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
            // /level set <level> [player]
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
}
