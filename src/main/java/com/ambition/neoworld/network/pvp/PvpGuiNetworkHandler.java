package com.ambition.neoworld.network.pvp;

import java.util.List;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.pvp.arena.PvpArenaData;
import com.ambition.neoworld.pvp.room.PvpRoomManager;
import com.ambition.neoworld.pvp.ranked.PvpRatingRepository;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PvpGuiNetworkHandler {
    private PvpGuiNetworkHandler() { }
    public static void handle(ServerPlayer player,PvpGuiRequestPayload request) {
        var server=player.getServer(); if(server==null) return;
        long tick=server.getTickCount(); var persistent=player.getPersistentData();
        String key="neoworld_pvp_gui_request_tick";
        if(persistent.contains(key) && tick>=persistent.getLong(key) && tick-persistent.getLong(key)<5) {
            send(player,request,"กรุณารอสักครู่แล้วลองใหม่",new RatingView(false,0,0,List.of())); return;
        }
        persistent.putLong(key,tick);
        var manager=PvpRoomManager.get(server); String message="";
        try {
            switch(request.action()) {
                case QUERY, TOP -> { }
                case CREATE -> manager.create(player,request.arena());
                case QUEUE -> manager.queue(player,request.arena());
                case UNQUEUE -> manager.unqueue(player);
                case READY -> manager.ready(player,true);
                case UNREADY -> manager.ready(player,false);
                case START -> manager.start(player);
                case LEAVE -> manager.leave(player);
            }
        } catch(IllegalArgumentException exception) { message=exception.getMessage(); }
        String resultMessage=message==null ? "ดำเนินการไม่สำเร็จ" : message.substring(0,Math.min(256,message.length()));
        var playerId=player.getUUID(); var name=player.getGameProfile().getName();
        // Queries use the existing database pool off the game thread; gameplay snapshots remain server-thread owned.
        var top=request.action()==PvpGuiRequestPayload.Action.TOP
                ? com.ambition.neoworld.pvp.ranked.PvpRankingCache.top()
                : java.util.concurrent.CompletableFuture.completedFuture(List.<PvpRatingRepository.Rating>of());
        com.ambition.neoworld.pvp.ranked.PvpRankingCache.player(playerId,name).thenCombine(top,(personal,leaders) ->
                new RatingView(true,personal.player().rating(),personal.position(),leaders.stream()
                        .map(entry -> new PvpGuiDataPayload.Leader(entry.name(),entry.rating())).toList()))
        .exceptionally(error -> new RatingView(false,0,0,List.of())).thenAccept(view -> server.execute(() -> {
            if(server.getPlayerList().getPlayer(playerId)==player) send(player,request,resultMessage,view);
        }));
    }
    private record RatingView(boolean available,int rating,int position,List<PvpGuiDataPayload.Leader> top) { }
    private static void send(ServerPlayer player,PvpGuiRequestPayload request,String message,RatingView view) {
        var server=player.getServer(); var manager=PvpRoomManager.get(server);
        var room=manager.room(player.getUUID());
        var arenas=PvpArenaData.get(server).all().keySet().stream().filter(id -> id.length()<=64).sorted().limit(128).toList();
        var members=room==null ? List.<PvpGuiDataPayload.Member>of() : room.members().stream().map(id -> {
            var online=server.getPlayerList().getPlayer(id);
            return new PvpGuiDataPayload.Member(online==null ? "Offline" : online.getGameProfile().getName(),id.equals(room.owner()),room.ready(id));
        }).toList();
        if(!view.available() && message.isEmpty()) message="ข้อมูลจัดอันดับยังไม่พร้อม";
        PacketDistributor.sendToPlayer(player,new PvpGuiDataPayload(request.request(),message,view.available(),view.rating(),view.position(),manager.queued(player.getUUID()),
                room==null ? 0 : room.id(),room==null ? "" : room.state().name(),room!=null && room.owner().equals(player.getUUID()),
                room!=null && room.ready(player.getUUID()),room!=null && room.canStart(),room!=null && manager.isRanked(room.id()),arenas,members,view.top()));
    }
}
