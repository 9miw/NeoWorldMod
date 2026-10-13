package com.ambition.neoworld.trade.event;

import com.ambition.neoworld.trade.service.TradeManager;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class TradeEventHandler {
    private TradeEventHandler() {}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("trade")
                .then(Commands.argument("player", EntityArgument.player()).executes(context -> {
                    ServerPlayer sender = context.getSource().getPlayerOrException();
                    String error = TradeManager.get(sender.getServer()).request(sender, EntityArgument.getPlayer(context, "player"));
                    if (error != null) { context.getSource().sendFailure(Component.literal(error)); return 0; }
                    return 1;
                })));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { TradeManager.get(event.getServer()).tick(); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TradeManager.get(player.getServer()).disconnect(player.getUUID());
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TradeManager.get(player.getServer()).cancel(player.getUUID(), "ผู้เล่นเสียชีวิต");
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TradeManager.get(player.getServer()).cancel(player.getUUID(), "ผู้เล่นเปลี่ยนมิติ");
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) { TradeManager.stop(event.getServer()); }
}
