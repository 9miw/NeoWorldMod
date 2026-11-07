package com.ambition.neoworld.navigation;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(modid=NeoWorld.MODID)
public final class NavigationEvents {
    private record Work(ServerLevel level,BlockPos position) {}
    private static final java.util.Map<MinecraftServer,java.util.LinkedHashMap<String,Work>> PENDING=new java.util.IdentityHashMap<>();
    private NavigationEvents() {}
    public static void enqueue(ServerLevel level,BlockPos pos) {
        PENDING.computeIfAbsent(level.getServer(),s->new java.util.LinkedHashMap<>()).put(NavigationSavedData.id(level,pos),new Work(level,pos.immutable()));
    }
    public static void rebuild(ServerLevel level) {
        var data=NavigationSavedData.get(level.getServer());
        for(var n:data.graph().nodes()) if(n.dimension().equals(level.dimension().location().toString())) {
            var pos=BlockPos.containing(n.x(),n.y(),n.z()); if(level.hasChunkAt(pos)) enqueue(level,pos);
        }
    }
    @SubscribeEvent public static void chunk(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getChunk() instanceof LevelChunk chunk)) return;
        var positions=chunk.getBlockEntities().values().stream().filter(e->e instanceof NavigationMarkerBlock.MarkerEntity)
                .map(e->e.getBlockPos().immutable()).toList();
        var chunkPos=chunk.getPos();
        level.getServer().execute(()->{
            chunk.getBlockEntities().values().stream()
                    .filter(entity -> entity instanceof noppes.npcs.blocks.tiles.TileWaypoint)
                    .forEach(entity -> NavigationLocationDirectory.observe(level,
                            (noppes.npcs.blocks.tiles.TileWaypoint) entity));
            positions.forEach(p->enqueue(level,p));
            // Also refresh neighboring endpoints: their diagonal corridor may cross this marker-free chunk.
            var graph=NavigationSavedData.get(level.getServer()).graph();
            double margin=NavigationSavedData.get(level.getServer()).linkRadius()+1;
            for(var n:graph.nodes()) if(n.dimension().equals(level.dimension().location().toString())) {
                var p=BlockPos.containing(n.x(),n.y(),n.z());
                if(n.x()>=chunkPos.getMinBlockX()-margin && n.x()<=chunkPos.getMaxBlockX()+margin
                        && n.z()>=chunkPos.getMinBlockZ()-margin && n.z()<=chunkPos.getMaxBlockZ()+margin) enqueue(level,p);
            }
        });
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var pending=PENDING.get(event.getServer());
        if(pending!=null) for(int i=0;i<2&&!pending.isEmpty();i++) {
            var it=pending.entrySet().iterator(); Work work=it.next().getValue(); it.remove();
            if(!work.level().hasChunkAt(work.position())) continue;
            var data=NavigationSavedData.get(event.getServer());
            if(work.level().getBlockState(work.position()).is(NavigationRegistry.MARKER.get())) data.placed(work.level(),work.position());
            else data.removed(work.level(),work.position());
        }
        NavigationGraphSync.tick(event.getServer());
        QuestNavigationService.tick(event.getServer());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) NavigationGraphSync.login(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) { QuestNavigationService.stop(player); NavigationGraphSync.logout(player); }
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // Clear the old target once, then publish it for the new dimension; client recomputes locally.
        if(event.getEntity() instanceof ServerPlayer player) QuestNavigationService.invalidate(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { QuestNavigationService.clear(event.getServer()); NavigationGraphSync.clear(event.getServer()); PENDING.remove(event.getServer()); }
    @SubscribeEvent public static void place(BlockEvent.EntityPlaceEvent event) {
        if(event.getPlacedBlock().is(NavigationRegistry.MARKER.get()) && event.getLevel() instanceof ServerLevel
                && (!(event.getEntity() instanceof ServerPlayer player)||!player.isCreative()||!player.hasPermissions(2))) event.setCanceled(true);
    }
    @SubscribeEvent public static void destroy(BlockEvent.BreakEvent event) {
        if(event.getState().is(NavigationRegistry.MARKER.get()) && (!event.getPlayer().isCreative()||!event.getPlayer().hasPermissions(2))) event.setCanceled(true);
    }
    @EventBusSubscriber(modid=NeoWorld.MODID,bus=EventBusSubscriber.Bus.MOD)
    public static final class Creative {
        @SubscribeEvent public static void tab(BuildCreativeModeTabContentsEvent event) {
            if(event.getTabKey()==CreativeModeTabs.OP_BLOCKS && event.hasPermissions()) event.accept(NavigationRegistry.MARKER_ITEM.get());
        }
    }
}
