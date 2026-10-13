package com.ambition.neoworld.command.pvp;

import com.ambition.neoworld.pvp.room.PvpRoomManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PvpRoomCommand {
    private PvpRoomCommand() { }
    @FunctionalInterface private interface Action { void run(PvpRoomManager manager, ServerPlayer player); }

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("room")
                .then(Commands.literal("create").then(Commands.argument("arena", StringArgumentType.word())
                        .executes(ctx -> run(ctx.getSource(), (manager, player) -> manager.create(player, StringArgumentType.getString(ctx, "arena"))))))
                .then(Commands.literal("join").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(ctx -> run(ctx.getSource(), (manager, player) -> manager.join(player, IntegerArgumentType.getInteger(ctx, "id"))))))
                .then(Commands.literal("ready").executes(ctx -> run(ctx.getSource(), (manager, player) -> manager.ready(player, true))))
                .then(Commands.literal("unready").executes(ctx -> run(ctx.getSource(), (manager, player) -> manager.ready(player, false))))
                .then(Commands.literal("start").executes(ctx -> run(ctx.getSource(), PvpRoomManager::start)))
                .then(Commands.literal("leave").executes(ctx -> run(ctx.getSource(), PvpRoomManager::leave)))
                .then(Commands.literal("cancel").executes(ctx -> run(ctx.getSource(), PvpRoomManager::cancel)))
                .then(Commands.literal("info").executes(ctx -> run(ctx.getSource(), (manager, player) -> {
                    var room = manager.room(player.getUUID());
                    if (room == null) throw new IllegalArgumentException("You are not in a PvP room");
                    player.sendSystemMessage(Component.literal("[PvP] Room " + room.id() + " | " + room.arena() + " | " + room.state()));
                    for (var id : room.members()) {
                        var member = player.getServer().getPlayerList().getPlayer(id);
                        player.sendSystemMessage(Component.literal((member == null ? id.toString() : member.getGameProfile().getName())
                                + (id.equals(room.owner()) ? " (owner)" : "") + " | ready=" + room.ready(id)));
                    }
                })))
                .then(Commands.literal("list").executes(ctx -> {
                    var rooms = PvpRoomManager.get(ctx.getSource().getServer()).rooms();
                    ctx.getSource().sendSuccess(() -> Component.literal("[PvP] Rooms: " + rooms.size()), false);
                    for (var room : rooms) ctx.getSource().sendSuccess(() -> Component.literal(
                            room.id() + " | " + room.arena() + " | " + room.members().size() + "/2 | " + room.state()), false);
                    return rooms.size();
                }));
    }
    private static int run(CommandSourceStack source, Action action) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            action.run(PvpRoomManager.get(source.getServer()), player);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("[PvP] " + exception.getMessage()));
            return 0;
        }
    }
}
