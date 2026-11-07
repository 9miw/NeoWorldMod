package com.ambition.neoworld.world.structure;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.integration.WorldStructureIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class StructureCaptureEvents {
    private StructureCaptureEvents() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) { WorldStructureIntegration.ensure(event.getServer()); }
    @SubscribeEvent public static void left(PlayerInteractEvent.LeftClickBlock event) {
        if(!StructureCaptureService.isTool(event.getItemStack()))return;
        event.setCanceled(true);
        if(event.getAction()==PlayerInteractEvent.LeftClickBlock.Action.START && event.getEntity() instanceof ServerPlayer player)
            StructureCaptureService.get(player.getServer()).select(player,true,event.getPos());
    }
    @SubscribeEvent public static void right(PlayerInteractEvent.RightClickBlock event) {
        if(event.getHand()!=InteractionHand.MAIN_HAND || !StructureCaptureService.isTool(event.getItemStack()))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        if(event.getEntity() instanceof ServerPlayer player)StructureCaptureService.get(player.getServer()).select(player,false,event.getPos());
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { StructureCaptureService.get(event.getServer()).tick(); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)StructureCaptureService.get(player.getServer()).cancel(player.getUUID());
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        StructureCaptureService.get(event.getServer()).stop();WorldStructureIntegration.stop(event.getServer());
    }
}
