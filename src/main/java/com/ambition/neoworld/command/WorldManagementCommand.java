package com.ambition.neoworld.command;

import com.ambition.neoworld.world.WorldManagementManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Administrative, server-only world commands. */
public final class WorldManagementCommand {
    private WorldManagementCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("world")
                .requires(source -> source.hasPermission(2))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("list").executes(WorldManagementCommand::list))
                .then(Commands.literal("tp")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(worldArgument().then(Commands.argument("position", Vec3Argument.vec3())
                                        .executes(WorldManagementCommand::teleport)))))
                .then(Commands.literal("random")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(worldArgument()
                                        .executes(context -> random(context, 1000))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10_000))
                                                .executes(context -> random(context, IntegerArgumentType.getInteger(context, "radius")))))))
                .then(Commands.literal("reset")
                        .then(Commands.literal("add")
                                .then(worldArgument().then(Commands.argument("days", IntegerArgumentType.integer(1, 3650))
                                        .executes(WorldManagementCommand::add))))
                        .then(Commands.literal("remove").then(worldArgument().executes(WorldManagementCommand::remove)))
                        .then(Commands.literal("now").then(worldArgument()
                                .executes(context -> {
                                    ResourceLocation id = ResourceLocationArgument.getId(context, "world");
                                    context.getSource().sendSuccess(() -> Component.literal("ยืนยันรีด้วย /neoworld world reset now "
                                            + id + " confirm — บล็อก สิ่งปลูกสร้าง และของในโลกจะถูกรีเซ็ต"), false);
                                    return 1;
                                })
                                .then(Commands.literal("confirm").executes(WorldManagementCommand::reset)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> worldArgument() {
        return Commands.argument("world", ResourceLocationArgument.id())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        manager(context).worldIds(), builder));
    }

    private static WorldManagementManager manager(CommandContext<CommandSourceStack> context) {
        return WorldManagementManager.get(context.getSource().getServer());
    }

    private static int help(CommandSourceStack source) {
        for (String command : new String[] {
                "ระบบจัดการโลก (แอดมิน): /neoworld world list",
                "/neoworld world tp <ผู้เล่น> <โลก> <x> <y> <z>",
                "/neoworld world random <ผู้เล่น> <โลก> [รัศมี 1–10000; ค่าเริ่มต้น 1000]",
                "/neoworld world reset add <โลก> <วัน> | reset remove <โลก>",
                "/neoworld world reset now <โลก> confirm"}) {
            source.sendSuccess(() -> Component.literal(command), false);
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        try {
            var worlds = manager(context).describeWorlds();
            for (String world : worlds) context.getSource().sendSuccess(() -> Component.literal(world), false);
            return worlds.size();
        } catch (RuntimeException exception) {
            return error(context.getSource(), exception);
        }
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var players = EntityArgument.getPlayers(context, "players");
        Vec3 position = Vec3Argument.getVec3(context, "position");
        int moved = 0;
        try {
            WorldManagementManager manager = manager(context);
            ServerLevel target = manager.loadedWorld(ResourceLocationArgument.getId(context, "world"));
            for (ServerPlayer player : players) {
                try {
                    if (manager.teleport(player, target, position)) moved++;
                    else context.getSource().sendFailure(Component.literal("ย้าย " + player.getGameProfile().getName() + " ไม่สำเร็จ"));
                } catch (RuntimeException exception) {
                    error(context.getSource(), exception);
                }
            }
            final int count = moved;
            if (count > 0) context.getSource().sendSuccess(() -> Component.literal("ย้ายผู้เล่น " + count + " คนไป "
                    + target.dimension().location() + " ที่ " + position.x + " " + position.y + " " + position.z), true);
            return count;
        } catch (RuntimeException exception) {
            return error(context.getSource(), exception);
        }
    }

    private static int random(CommandContext<CommandSourceStack> context, int radius) throws CommandSyntaxException {
        var players = EntityArgument.getPlayers(context, "players");
        try {
            WorldManagementManager manager = manager(context);
            ServerLevel target = manager.loadedWorld(ResourceLocationArgument.getId(context, "world"));
            int queued = 0;
            for (ServerPlayer player : players) {
                if (manager.queueRandom(player, target, context.getSource(), radius)) queued++;
                else context.getSource().sendFailure(Component.literal("คิวสุ่มเต็มหรือ " + player.getGameProfile().getName() + " มีคำสั่งสุ่มค้างอยู่"));
            }
            final int count = queued;
            if (count > 0) context.getSource().sendSuccess(() -> Component.literal("กำลังหาจุดสุ่มที่ปลอดภัยให้ผู้เล่น " + count + " คน"), false);
            return count;
        } catch (RuntimeException exception) {
            return error(context.getSource(), exception);
        }
    }

    private static int add(CommandContext<CommandSourceStack> context) {
        try {
            ResourceLocation id = ResourceLocationArgument.getId(context, "world");
            int days = IntegerArgumentType.getInteger(context, "days");
            manager(context).addReset(id, days);
            context.getSource().sendSuccess(() -> Component.literal("ตั้งรี " + id + " ทุก " + days + " วันตามเวลาจริงแล้ว"), true);
            return 1;
        } catch (RuntimeException exception) {
            return error(context.getSource(), exception);
        }
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        try {
            ResourceLocation id = ResourceLocationArgument.getId(context, "world");
            manager(context).removeReset(id);
            context.getSource().sendSuccess(() -> Component.literal("ยกเลิกรีอัตโนมัติของ " + id + " แล้ว"), true);
            return 1;
        } catch (RuntimeException exception) {
            return error(context.getSource(), exception);
        }
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        try {
            ResourceLocation id = ResourceLocationArgument.getId(context, "world");
            manager(context).requestReset(id);
            context.getSource().sendSuccess(() -> Component.literal("เพิ่ม " + id + " เข้าคิวรีทันทีแล้ว"), true);
            return 1;
        } catch (RuntimeException exception) {
            return error(context.getSource(), exception);
        }
    }

    private static int error(CommandSourceStack source, RuntimeException exception) {
        source.sendFailure(Component.literal(exception.getMessage() == null ? "คำสั่งจัดการโลกไม่สำเร็จ" : exception.getMessage()));
        return 0;
    }
}
