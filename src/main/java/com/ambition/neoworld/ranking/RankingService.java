package com.ambition.neoworld.ranking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.ranking.RankingDataPayload;
import com.ambition.neoworld.network.ranking.RankingRequestPayload;
import com.ambition.neoworld.pvp.ranked.PvpRankingCache;
import com.ambition.neoworld.pvp.ranked.PvpRatingRepository;
import com.ambition.neoworld.ranking.RankingTable.Category;
import com.ambition.neoworld.ranking.RankingTable.Entry;
import com.ambition.neoworld.ranking.RankingTable.View;
import com.ambition.neoworld.registry.ModAttachments;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=NeoWorld.MODID)
public final class RankingService {
    private static final Map<MinecraftServer,RankingService> SERVICES=new WeakHashMap<>();
    private final Map<ServerPlayer,Long> requests=new WeakHashMap<>();
    private final RankingSnapshotCache<Map<UUID,Entry>> saved=new RankingSnapshotCache<>(30_000,() -> System.nanoTime()/1_000_000);
    private final Map<UUID,RankingSnapshotCache<RankingAppearance>> appearances=new HashMap<>();
    private RankingService() { }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SERVICES.remove(event.getServer()); }
    public static void invalidateAppearance(MinecraftServer server,UUID player) {
        var service=SERVICES.get(server); if(service!=null) service.appearances.remove(player);
    }
    public static void handle(ServerPlayer player,RankingRequestPayload request) {
        var server=player.getServer();
        var service=SERVICES.computeIfAbsent(server,ignored -> new RankingService());
        long now=System.nanoTime()/1_000_000;
        Long previous=service.requests.get(player);
        if(previous!=null && now-previous<500) {
            reply(player,request,"กรุณารอสักครู่แล้วลองใหม่",new View(List.of(),null,0)); return;
        }
        service.requests.put(player,now);
        CompletableFuture<View> future;
        if(request.category()==Category.PVP) {
            future=PvpRankingCache.player(player.getUUID(),player.getGameProfile().getName())
                    .thenCombine(PvpRankingCache.top(),(own,top) -> new View(top.stream().map(RankingService::pvp).toList(),pvp(own.player()),own.position()));
        } else {
            // Capture live attachments on the owning server thread, never from the I/O worker.
            var online=new HashMap<UUID,Entry>();
            for(var live:server.getPlayerList().getPlayers()) {
                var data=live.getData(ModAttachments.PLAYER_DATA);
                online.put(live.getUUID(),new Entry(live.getUUID(),live.getGameProfile().getName(),data.getLevel(),data.getExp(),data.getSilver(),0,0,0,data.getTotalExp()));
            }
            var path=server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            var snapshot=service.saved.get(() -> {
                try { return SavedRankingPlayers.read(path); }
                catch(Exception error) { throw new CompletionException(error); }
            },Util.ioPool());
            future=snapshot.thenApplyAsync(saved -> {
                var merged=new HashMap<>(saved); merged.putAll(online);
                return RankingTable.select(request.category(),List.copyOf(merged.values()),player.getUUID());
            },Util.ioPool());
        }
        future.whenComplete((view,error) -> server.execute(() -> {
            if(server.getPlayerList().getPlayer(player.getUUID())!=player) return;
            if(error!=null) {
                NeoWorld.LOGGER.warn("Unable to load {} ranking",request.category(),error);
                reply(player,request,"โหลดข้อมูลอันดับไม่สำเร็จ กรุณาลองใหม่",new View(List.of(),null,0));
            } else {
                var named=new View(view.leaders().stream().map(e -> name(server,e)).toList(),
                        view.personal()==null ? null : name(server,view.personal()),view.position());
                if(request.category()==Category.LEVEL) { reply(player,request,"",named); return; }
                var previews=named.leaders().stream().limit(3).map(entry -> {
                    var live=server.getPlayerList().getPlayer(entry.id());
                    CompletableFuture<RankingAppearance> appearance;
                    if(live!=null) appearance=CompletableFuture.completedFuture(live.getData(ModAttachments.RANKING_APPEARANCE));
                    else {
                        var cache=service.appearances.computeIfAbsent(entry.id(),ignored -> new RankingSnapshotCache<>(30_000,() -> System.nanoTime()/1_000_000));
                        var path=server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(entry.id()+".dat");
                        var registries=server.registryAccess();
                        appearance=cache.get(() -> {
                            try { return SavedRankingPlayers.appearance(path,registries); }
                            catch(Exception failure) {
                                NeoWorld.LOGGER.warn("Unable to read ranking appearance for {}",entry.id(),failure);
                                return RankingAppearance.EMPTY;
                            }
                        },Util.ioPool());
                    }
                    return appearance.thenApply(snapshot -> new RankingDataPayload.Preview(entry.id(),snapshot));
                }).toList();
                CompletableFuture.allOf(previews.toArray(CompletableFuture[]::new)).whenComplete((ignored,previewError) -> server.execute(() -> {
                    if(server.getPlayerList().getPlayer(player.getUUID())!=player) return;
                    if(previewError!=null) {
                        NeoWorld.LOGGER.warn("Unable to load podium previews",previewError);
                        reply(player,request,"โหลดรูปลักษณ์ไม่สำเร็จ กรุณาลองใหม่",new View(List.of(),null,0));
                    } else PacketDistributor.sendToPlayer(player,new RankingDataPayload(request.request(),request.category(),"",named,
                            previews.stream().map(CompletableFuture::join).toList()));
                }));
            }
        }));
    }
    private static Entry pvp(PvpRatingRepository.Rating rating) {
        return new Entry(UUID.fromString(rating.id()),rating.name(),1,0,0,rating.rating(),rating.wins(),rating.losses());
    }
    private static Entry name(MinecraftServer server,Entry entry) {
        var online=server.getPlayerList().getPlayer(entry.id());
        if(online!=null) return entry.named(online.getGameProfile().getName());
        var cache=server.getProfileCache();
        return cache==null ? entry : cache.get(entry.id()).map(p -> entry.named(p.getName())).orElse(entry);
    }
    private static void reply(ServerPlayer player,RankingRequestPayload request,String error,View view) {
        PacketDistributor.sendToPlayer(player,new RankingDataPayload(request.request(),request.category(),error,view));
    }
}
