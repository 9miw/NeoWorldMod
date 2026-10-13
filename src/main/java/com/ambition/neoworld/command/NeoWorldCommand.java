package com.ambition.neoworld.command;

import com.ambition.neoworld.command.dungeon.DungeonCommand;
import com.ambition.neoworld.command.economy.EconomyCommand;
import com.ambition.neoworld.command.fashion.FashionCommand;
import com.ambition.neoworld.command.level.LevelCommand;
import com.ambition.neoworld.command.party.PartyCommand;
import com.ambition.neoworld.command.pvp.PvpArenaCommand;
import com.ambition.neoworld.command.title.TitleCommand;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.hub.OpenMainHubPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Central registration point for the /neoworld command tree and its aliases. */
@EventBusSubscriber(modid = NeoWorld.MODID)
public final class NeoWorldCommand {

    private NeoWorldCommand() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralCommandNode<CommandSourceStack> economy = EconomyCommand.buildCommand("eco").build();

        LiteralArgumentBuilder<CommandSourceStack> rootBuilder = Commands.literal("neoworld")
            .executes(context -> openHub(context.getSource().getPlayerOrException()))
            .then(Commands.literal("hub")
                    .executes(context -> openHub(context.getSource().getPlayerOrException())))
            .then(Commands.literal("activity").executes(context -> {
                PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(),
                        com.ambition.neoworld.network.activity.OpenActivityPayload.INSTANCE);
                return 1;
            }))
            .then(economy)
            .then(Commands.literal("economy").redirect(economy))
            .then(LevelCommand.buildCommand())
            .then(FashionCommand.buildCommand())
            .then(PartyCommand.buildCommand())
            .then(TitleCommand.buildCommand())
            .then(DungeonCommand.buildCommand())
            .then(PvpArenaCommand.buildCommand())
            .then(MarketCommand.buildCommand())
            .then(WorldManagementCommand.buildCommand());

        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(rootBuilder);
        dispatcher.register(Commands.literal("neow").redirect(root));
    }

    private static int openHub(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, OpenMainHubPayload.INSTANCE);
        return 1;
    }
}
