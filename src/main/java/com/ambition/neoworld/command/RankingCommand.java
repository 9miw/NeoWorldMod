package com.ambition.neoworld.command;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.ranking.OpenRankingPayload;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Separate registration avoids modifying the concurrently edited menu/command tree. */
@EventBusSubscriber(modid=NeoWorld.MODID)
public final class RankingCommand {
    private RankingCommand() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("neow").then(Commands.literal("ranking").executes(context -> {
            PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(),OpenRankingPayload.INSTANCE);
            return 1;
        })));
    }
}
