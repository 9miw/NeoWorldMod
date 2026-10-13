package com.ambition.neoworld.command.pvp;

import com.ambition.neoworld.pvp.room.PvpRoomManager;
import com.ambition.neoworld.pvp.ranked.PvpRatingRepository;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class PvpRankedCommand {
    private PvpRankedCommand() { }
    public static LiteralArgumentBuilder<CommandSourceStack> queue() {
        return Commands.literal("queue").then(Commands.argument("arena",StringArgumentType.word()).executes(ctx -> {
            var player=ctx.getSource().getPlayerOrException();
            try { PvpRoomManager.get(ctx.getSource().getServer()).queue(player,StringArgumentType.getString(ctx,"arena")); return 1; }
            catch (IllegalArgumentException exception) { ctx.getSource().sendFailure(Component.literal(exception.getMessage())); return 0; }
        }));
    }
    public static LiteralArgumentBuilder<CommandSourceStack> unqueue() {
        return Commands.literal("unqueue").executes(ctx -> {
            try { PvpRoomManager.get(ctx.getSource().getServer()).unqueue(ctx.getSource().getPlayerOrException()); return 1; }
            catch (IllegalArgumentException exception) { ctx.getSource().sendFailure(Component.literal(exception.getMessage())); return 0; }
        });
    }
    public static LiteralArgumentBuilder<CommandSourceStack> rank() {
        return Commands.literal("rank").executes(ctx -> {
            var player=ctx.getSource().getPlayerOrException();
            try {
                var rating=PvpRatingRepository.player(player.getUUID(),player.getGameProfile().getName());
                ctx.getSource().sendSuccess(() -> Component.literal("[PvP] Rating " + rating.rating() + " | W " + rating.wins() + " / L " + rating.losses()),false); return 1;
            } catch (java.sql.SQLException exception) { ctx.getSource().sendFailure(Component.literal("Rating database unavailable")); return 0; }
        });
    }
    public static LiteralArgumentBuilder<CommandSourceStack> top() {
        return Commands.literal("top").executes(ctx -> {
            try {
                var top=PvpRatingRepository.top(); int rank=0;
                for (var rating:top) { String line="#"+(++rank)+" "+rating.name()+" | "+rating.rating(); ctx.getSource().sendSuccess(() -> Component.literal(line),false); }
                if (top.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal("No completed ranked wins/losses yet"),false);
                return top.size();
            } catch (java.sql.SQLException exception) { ctx.getSource().sendFailure(Component.literal("Rating database unavailable")); return 0; }
        });
    }
}
