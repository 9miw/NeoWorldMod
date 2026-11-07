package com.ambition.neoworld.navigation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Initial graph once per login, then changed records only. No player-path computations. */
public final class NavigationGraphSync {
    private static final Map<MinecraftServer,Runtime> SERVERS=new IdentityHashMap<>();
    private static final class Runtime {
        NavigationGraphSnapshot snapshot;
        final Map<UUID,Long> sent=new HashMap<>(),requests=new HashMap<>();
        Runtime(NavigationGraphSnapshot snapshot){this.snapshot=snapshot;}
    }
    private NavigationGraphSync() {}
    private static Runtime runtime(MinecraftServer server){return SERVERS.computeIfAbsent(server,s->new Runtime(NavigationGraphSnapshot.capture(NavigationSavedData.get(s).graph())));}
    public static void login(ServerPlayer player) {
        var runtime=runtime(player.getServer());
        var current=NavigationGraphSnapshot.capture(NavigationSavedData.get(player.getServer()).graph());
        for(var page:NavigationGraphSnapshot.pages(null,current))PacketDistributor.sendToPlayer(player,page);
        runtime.sent.put(player.getUUID(),current.revision());
    }
    public static void request(ServerPlayer player) {
        var runtime=runtime(player.getServer());long now=player.getServer().getTickCount();
        Long last=runtime.requests.get(player.getUUID());if(last!=null&&now-last<100)return;
        runtime.requests.put(player.getUUID(),now);login(player);
    }
    public static void tick(MinecraftServer server) {
        if(server.getTickCount()%10!=0||server.getPlayerList().getPlayers().isEmpty())return;
        var runtime=runtime(server);var graph=NavigationSavedData.get(server).graph();
        var previous=runtime.snapshot;
        var current=previous.revision()==graph.revision()?previous:NavigationGraphSnapshot.capture(graph);
        var delta=NavigationGraphSnapshot.pages(previous,current);
        List<com.ambition.neoworld.network.navigation.NavigationGraphPayload> reset=null;
        for(var player:server.getPlayerList().getPlayers()) {
            Long sent=runtime.sent.get(player.getUUID());
            if(sent!=null&&sent==current.revision())continue;
            if(sent!=null&&sent==previous.revision()) {
                for(var page:delta)PacketDistributor.sendToPlayer(player,page);
            } else {
                if(reset==null)reset=NavigationGraphSnapshot.pages(null,current);
                for(var page:reset)PacketDistributor.sendToPlayer(player,page);
            }
            runtime.sent.put(player.getUUID(),current.revision());
        }
        runtime.snapshot=current;
    }
    public static void logout(ServerPlayer player){var runtime=SERVERS.get(player.getServer());if(runtime!=null){runtime.sent.remove(player.getUUID());runtime.requests.remove(player.getUUID());}}
    public static void clear(MinecraftServer server){SERVERS.remove(server);}
}
