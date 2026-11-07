package com.ambition.neoworld.network.pvp;

import java.util.List;
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
            load(player,request,request.action()==PvpGuiRequestPayload.Action.QUERY || request.action()==PvpGuiRequestPayload.Action.TOP ? "" : "กรุณารอสักครู่แล้วลองใหม่"); return;
        }
        if(request.action()!=PvpGuiRequestPayload.Action.QUERY && request.action()!=PvpGuiRequestPayload.Action.TOP) persistent.putLong(key,tick);
        var manager=PvpRoomManager.get(server); String message="";
        try {
            switch(request.action()) {
                case QUERY, TOP -> { }
                case CREATE -> manager.create(player,request.arena());
                case QUEUE -> manager.queue(player);
                case UNQUEUE -> manager.unqueue(player);
                case READY, UNREADY -> throw new IllegalArgumentException("ใช้ปุ่มต่อสู้เพื่อเปิดหน้ายืนยันคู่ต่อสู้");
                case START -> manager.start(player);
                case LEAVE -> manager.leave(player);
                case INVITE -> { manager.invite(player,java.util.UUID.fromString(request.arena())); message="ส่งคำเชิญแล้ว (หมดอายุใน 30 วินาที)"; }
                case ACCEPT_INVITE -> manager.answerInvitation(player,java.util.UUID.fromString(request.arena()),true);
                case DECLINE_INVITE -> manager.answerInvitation(player,java.util.UUID.fromString(request.arena()),false);
            }
        } catch(IllegalArgumentException exception) {
            com.ambition.neoworld.pvp.room.PvpSoundEffects.error(player);
            message=exception.getMessage();
            if(request.action()==PvpGuiRequestPayload.Action.ACCEPT_INVITE || request.action()==PvpGuiRequestPayload.Action.DECLINE_INVITE)
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[PvP] "+message));
        }
        load(player,request,message);
    }
    public static void push(ServerPlayer player) {
        load(player,new PvpGuiRequestPayload(new java.util.UUID(0,0),PvpGuiRequestPayload.Action.QUERY,""),"");
    }
    private static void load(ServerPlayer player,PvpGuiRequestPayload request,String message) {
        var server=player.getServer();
        String resultMessage=message==null ? "ดำเนินการไม่สำเร็จ" : message.substring(0,Math.min(256,message.length()));
        var playerId=player.getUUID(); var name=player.getGameProfile().getName();
        // Queries use the existing database pool off the game thread; gameplay snapshots remain server-thread owned.
        var top=request.action()==PvpGuiRequestPayload.Action.TOP
                ? com.ambition.neoworld.pvp.ranked.PvpRankingCache.top()
                : java.util.concurrent.CompletableFuture.completedFuture(List.<PvpRatingRepository.Rating>of());
        var room=PvpRoomManager.get(server).room(playerId);
        var members=room==null ? List.<java.util.UUID>of() : room.members();
        var ratings=new java.util.HashMap<java.util.UUID,java.util.concurrent.CompletableFuture<PvpRatingRepository.View>>();
        for(var id:members) {
            var online=server.getPlayerList().getPlayer(id);
            if(online!=null) ratings.put(id,com.ambition.neoworld.pvp.ranked.PvpRankingCache.player(id,online.getGameProfile().getName()).exceptionally(error -> null));
        }
        var memberRatings=java.util.concurrent.CompletableFuture.allOf(ratings.values().toArray(java.util.concurrent.CompletableFuture[]::new));
        com.ambition.neoworld.pvp.ranked.PvpRankingCache.player(playerId,name).thenCombine(top,(personal,leaders) ->
                new RatingView(true,personal.player().rating(),personal.position(),leaders.stream()
                        .map(entry -> new PvpGuiDataPayload.Leader(entry.name(),entry.rating())).toList()))
        .exceptionally(error -> new RatingView(false,0,0,List.of())).thenCombine(memberRatings,(view,ignored) -> view).thenAccept(view -> server.execute(() -> {
            if(server.getPlayerList().getPlayer(playerId)==player) {
                var memberViews=new java.util.HashMap<java.util.UUID,PvpRatingRepository.View>();
                ratings.forEach((id,future) -> { var value=future.getNow(null); if(value!=null) memberViews.put(id,value); });
                send(player,request,resultMessage,view,memberViews);
            }
        }));
    }
    private record RatingView(boolean available,int rating,int position,List<PvpGuiDataPayload.Leader> top) { }
    private static void send(ServerPlayer player,PvpGuiRequestPayload request,String message,RatingView view,java.util.Map<java.util.UUID,PvpRatingRepository.View> memberViews) {
        var server=player.getServer(); var manager=PvpRoomManager.get(server);
        var room=manager.room(player.getUUID());
        var arenas=PvpArenaData.get(server).all().entrySet().stream().filter(entry -> entry.getValue().validated() && !entry.getValue().pieces().isEmpty())
                .map(java.util.Map.Entry::getKey).filter(id -> id.length()<=64 && !com.ambition.neoworld.pvp.arena.PvpCaptureService.isSaving(server,id)).sorted().limit(128).toList();
        var members=room==null ? List.<PvpGuiDataPayload.Member>of() : room.members().stream().map(id -> {
            var online=server.getPlayerList().getPlayer(id);
            var rating=memberViews.get(id);
            var preview=online==null ? null : manager.preview(online,rating==null ? -1 : rating.player().rating());
            if(preview!=null && rating!=null) preview=new PvpMatchOfferPayload.Player(preview.id(),preview.name(),preview.rating(),rating.position(),preview.equipment());
            return new PvpGuiDataPayload.Member(online==null ? "Offline" : online.getGameProfile().getName(),id.equals(room.owner()),false,preview);
        }).toList();
        if(!view.available() && message.isEmpty()) message="ข้อมูลจัดอันดับยังไม่พร้อม";
        PacketDistributor.sendToPlayer(player,new PvpGuiDataPayload(request.request(),message,view.available(),view.rating(),view.position(),manager.queued(player.getUUID()),
                room==null ? 0 : room.id(),room==null ? "" : room.state().name(),room!=null && room.owner().equals(player.getUUID()),
                false,room!=null && room.canStart(),room!=null && manager.isRanked(room.id()),arenas,members,view.top(),room==null ? "" : room.arena()));
    }
}
