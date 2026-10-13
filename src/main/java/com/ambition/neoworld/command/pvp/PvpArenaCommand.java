package com.ambition.neoworld.command.pvp;

import com.ambition.neoworld.pvp.PvpDimensions;
import com.ambition.neoworld.pvp.arena.PvpArenaData;
import com.ambition.neoworld.pvp.arena.PvpArenaTemplates;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class PvpArenaCommand {
    private PvpArenaCommand() { }

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("pvp")
                .executes(ctx -> {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(ctx.getSource().getPlayerOrException(),com.ambition.neoworld.network.pvp.OpenPvpPayload.INSTANCE);
                    return 1;
                })
                .then(PvpRankedCommand.queue())
                .then(PvpRankedCommand.unqueue())
                .then(PvpRankedCommand.rank())
                .then(PvpRankedCommand.top())
                .then(PvpRoomCommand.buildCommand())
                .then(Commands.literal("arena").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("structure", ResourceLocationArgument.id())
                                                .executes(ctx -> registerArena(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        ResourceLocationArgument.getId(ctx, "structure"))))))
                        .then(Commands.literal("validate")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> validate(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("list").executes(ctx -> {
                            var arenas = PvpArenaData.get(ctx.getSource().getServer()).all();
                            ctx.getSource().sendSuccess(() -> Component.literal("PvP arenas: " + arenas), false);
                            return arenas.size();
                        })));
    }

    private static int registerArena(CommandSourceStack source, String id, ResourceLocation template) {
        if (!id.matches("[a-z0-9_]+")) return fail(source, "Arena ID must use a-z, 0-9 or underscore");
        var data = PvpArenaData.get(source.getServer());
        if (data.template(id) != null) return fail(source, "Arena ID already exists: " + id);
        if (!inspect(source, template)) return 0;
        if (!data.register(id, template)) return fail(source, "Could not register arena: " + id);
        source.sendSuccess(() -> Component.literal("Registered PvP arena " + id + " -> " + template), true);
        return 1;
    }

    private static int validate(CommandSourceStack source, String id) {
        var template = PvpArenaData.get(source.getServer()).template(id);
        if (template == null) return fail(source, "Unknown arena: " + id);
        if (!inspect(source, template)) return 0;
        source.sendSuccess(() -> Component.literal("PvP arena " + id + ": both spawn markers are valid; bounds use saved structure size"), false);
        return 1;
    }

    private static boolean inspect(CommandSourceStack source, ResourceLocation template) {
        var level = source.getServer().getLevel(PvpDimensions.LEVEL);
        if (level == null) { fail(source, "PvP dimension is unavailable"); return false; }
        try {
            PvpArenaTemplates.inspect(level, template);
            return true;
        } catch (IllegalArgumentException exception) {
            fail(source, exception.getMessage());
            return false;
        }
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
