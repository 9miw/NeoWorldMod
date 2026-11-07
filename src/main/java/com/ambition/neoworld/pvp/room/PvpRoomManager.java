package com.ambition.neoworld.pvp.room;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.ambition.neoworld.pvp.ranked.PvpRatingRepository;
import com.ambition.neoworld.pvp.ranked.RankedMatch;
import com.ambition.neoworld.pvp.ranked.PvpResultOutbox;
import com.ambition.neoworld.pvp.ranked.MatchConfirmation;
import com.ambition.neoworld.pvp.ranked.PvpQueuePenaltyData;
import com.ambition.neoworld.network.pvp.PvpMatchOfferPayload;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.pvp.PvpDimensions;
import com.ambition.neoworld.pvp.arena.PvpArenaData;
import com.ambition.neoworld.pvp.arena.PvpArenaTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class PvpRoomManager {
    private static final Map<MinecraftServer, PvpRoomManager> MANAGERS = new HashMap<>();
    private final MinecraftServer server;
    private final PvpSoundEffects sounds=new PvpSoundEffects();
    private final Map<UUID,Long> countdownSounds=new HashMap<>();
    private final Map<Integer, PvpRoom> rooms = new LinkedHashMap<>();
    private final Map<UUID, Integer> membership = new HashMap<>();
    private final Map<Integer, Run> runs = new HashMap<>();
    private final Map<Integer,PendingLoad> placements=new LinkedHashMap<>();
    private static final class PendingLoad implements AutoCloseable {
        final List<com.ambition.neoworld.pvp.arena.PvpArenaPiece> pieces;
        final java.util.ArrayList<PvpArenaTemplates.ResolvedPiece> resolved=new java.util.ArrayList<>();
        com.ambition.neoworld.pvp.arena.PvpArenaPlacement placement;
        long started=-1;
        PendingLoad(List<com.ambition.neoworld.pvp.arena.PvpArenaPiece> pieces) { this.pieces=List.copyOf(pieces); }
        @Override public void close() { if(placement!=null) placement.close(); }
    }
    private final Map<Integer,RoundIntermission> intermissions=new HashMap<>();
    private final Map<Integer,PvpClientLoadBarrier> clientLoads=new HashMap<>();
    private final ArrayDeque<Cleanup> cleanup = new ArrayDeque<>();
    private final Map<UUID, QueueEntry> queue = new LinkedHashMap<>();
    private final Map<Integer, RankedMatch> ranked = new HashMap<>();
    private final Map<String, Long> recentPairs = new HashMap<>();
    private record QueueEntry(int rating, long entered) { }
    private record Offer(MatchConfirmation confirmation,QueueEntry first,QueueEntry second,
            PvpMatchOfferPayload.Player a,PvpMatchOfferPayload.Player b,int privateRoom,String arena) { }
    private boolean warnedNoRankedArena;
    private final Map<UUID,Offer> offers=new LinkedHashMap<>();
    private final PvpInvitationBook invitations=new PvpInvitationBook();
    private int nextId = 1;
    private long ticks;
    private record Run(int slot, BlockPos origin, PvpArenaTemplates.Layout layout, long started) { }
    private static final class Cleanup {
        final int slot; final PvpRecoveryData.Region region; int index;
        Cleanup(int slot, PvpRecoveryData.Region region) { this.slot = slot; this.region = region; }
    }
    private PvpRoomManager(MinecraftServer server) {
        this.server = server;
        PvpRecoveryData.get(server).regions().forEach((slot, region) -> cleanup.add(new Cleanup(slot, region)));
    }
    public static PvpRoomManager get(MinecraftServer server) { return MANAGERS.computeIfAbsent(server, PvpRoomManager::new); }
    public static void stop(MinecraftServer server) { MANAGERS.remove(server); com.ambition.neoworld.pvp.ranked.PvpRankingCache.clear(); }
    public void shutdown() { sounds.clear(); countdownSounds.clear(); for(var offer:List.copyOf(offers.values())) removeOffer(offer); for(UUID id:List.copyOf(queue.keySet())) removeQueue(id); for (var room : List.copyOf(rooms.values())) close(room, "Server stopping; room closed without penalty"); }
    public List<PvpRoom> rooms() { return List.copyOf(rooms.values()); }
    public boolean queued(UUID player) { return queue.containsKey(player); }
    private Offer offer(UUID player) { return offers.values().stream().filter(entry -> entry.confirmation().contains(player)).findFirst().orElse(null); }
    private void checkOffer(ServerPlayer player) { if(offer(player.getUUID())!=null) throw new IllegalArgumentException("Confirm your current opponent first"); }
    public void sendQueueState(ServerPlayer player) {
        var entry=queue.get(player.getUUID());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,entry==null
                ? com.ambition.neoworld.network.pvp.PvpQueueStatePayload.EMPTY
                : new com.ambition.neoworld.network.pvp.PvpQueueStatePayload(true,entry.rating(),(int)Math.min(Integer.MAX_VALUE,Math.max(0,(ticks-entry.entered())/20))));
    }
    private boolean removeQueue(UUID id) {
        boolean removed=queue.remove(id)!=null;
        var player=server.getPlayerList().getPlayer(id); if(player!=null) sendQueueState(player);
        return removed;
    }
    public boolean isRanked(int room) { return ranked.containsKey(room); }
    public boolean arenaInUse(String arena) {
        return rooms.values().stream().anyMatch(room -> room.arena().equals(arena))
                || offers.values().stream().anyMatch(offer -> offer.arena().equals(arena));
    }
    public PvpRoom room(UUID player) { return rooms.get(membership.get(player)); }
    public boolean inArena(UUID player) { var room = room(player); return room != null && (room.state()==PvpRoom.State.CLIENT_LOADING || room.state()==PvpRoom.State.COUNTDOWN || room.state()==PvpRoom.State.ACTIVE); }
    public boolean canFight(UUID first, UUID second) {
        var room = room(first);
        return room != null && room == room(second) && room.state() == PvpRoom.State.ACTIVE;
    }
    private void eligible(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSleeping()
                || player.serverLevel().dimension().equals(PvpDimensions.LEVEL)
                || PvpRecoveryData.get(server).point(player.getUUID()) != null) {
            throw new IllegalArgumentException("Leave your current activity before joining PvP");
        }
    }
    public int create(ServerPlayer player, String arena) {
        eligible(player);
        checkOffer(player);
        if (queue.containsKey(player.getUUID())) throw new IllegalArgumentException("Leave the ranked queue first");
        if (room(player.getUUID()) != null) throw new IllegalArgumentException("You already have a room");
        if (PvpArenaData.get(server).get(arena)==null || !PvpArenaData.get(server).get(arena).validated()) throw new IllegalArgumentException("Unknown or empty arena: " + arena);
        if(com.ambition.neoworld.pvp.arena.PvpCaptureService.isSaving(server,arena)) throw new IllegalArgumentException("สนามกำลังบันทึก กรุณารอสักครู่");
        var room = new PvpRoom(nextId++, arena, player.getUUID());
        rooms.put(room.id(), room); membership.put(player.getUUID(), room.id());
        message(room, "สร้างห้องแล้ว ใช้ปุ่มเชิญผู้เล่นเพื่อเชิญคู่ต่อสู้");
        return room.id();
    }
    public void join(ServerPlayer player, int id) {
        eligible(player);
        checkOffer(player);
        if (queue.containsKey(player.getUUID()) || ranked.containsKey(id)) throw new IllegalArgumentException("Ranked opponents are selected by matchmaking");
        if (room(player.getUUID()) != null) throw new IllegalArgumentException("You already have a room");
        PvpRoom room = rooms.get(id);
        if (room == null) throw new IllegalArgumentException("Unknown room: " + id);
        if(room.members().stream().anyMatch(member -> offer(member)!=null)) throw new IllegalArgumentException("กำลังยืนยันคู่ต่อสู้");
        room.join(player.getUUID()); membership.put(player.getUUID(), id);
        invitations.revokeRoom(id);
        message(room, player.getGameProfile().getName() + " เข้าห้องแล้ว หัวหน้าห้องสามารถกดต่อสู้ได้");
        notifyRoom(room);
    }
    private PvpRoom require(ServerPlayer player) {
        var room = room(player.getUUID());
        if (room == null) throw new IllegalArgumentException("You are not in a PvP room");
        return room;
    }
    private void notifyRoom(PvpRoom room) {
        for(UUID id:room.members()) {
            var player=server.getPlayerList().getPlayer(id);
            if(player!=null) com.ambition.neoworld.network.pvp.PvpGuiNetworkHandler.push(player);
        }
    }
    private void reopenRoom(PvpRoom room) {
        for(UUID id:room.members()) {
            var player=server.getPlayerList().getPlayer(id);
            if(player!=null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,com.ambition.neoworld.network.pvp.OpenPvpPayload.INSTANCE);
        }
    }
    public void invite(ServerPlayer owner,UUID targetId) {
        var room=require(owner); checkOffer(owner);
        if(!room.owner().equals(owner.getUUID()) || room.state()!=PvpRoom.State.WAITING || room.members().size()!=1 || isRanked(room.id()))
            throw new IllegalArgumentException("เฉพาะหัวหน้าห้องที่ยังมีที่ว่างเท่านั้นที่เชิญได้");
        var target=server.getPlayerList().getPlayer(targetId);
        if(target==null) throw new IllegalArgumentException("ผู้เล่นไม่ได้ออนไลน์");
        eligible(target); checkOffer(target);
        if(room(targetId)!=null || queued(targetId)) throw new IllegalArgumentException("ผู้เล่นกำลังอยู่ในห้องหรือคิว PvP");
        var invitation=invitations.create(room.id(),owner.getUUID(),targetId,ticks);
        sounds.emit(target,PvpSoundEffects.Cue.INVITED,ticks);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(target,new com.ambition.neoworld.network.pvp.PvpInvitationPayload(invitation.token(),room.id(),owner.getGameProfile().getName()));
    }
    public void answerInvitation(ServerPlayer player,UUID token,boolean accept) {
        var invitation=invitations.take(token,player.getUUID(),ticks);
        var room=rooms.get(invitation.room());
        if(room==null || !room.owner().equals(invitation.owner())) throw new IllegalArgumentException("ห้องนี้ปิดแล้ว");
        if(!accept) { message(room,player.getGameProfile().getName()+" ปฏิเสธคำเชิญ"); return; }
        join(player,room.id());
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,com.ambition.neoworld.network.pvp.OpenPvpPayload.INSTANCE);
    }
    public void leave(ServerPlayer player) {
        var room = require(player);
        var confirmation=offer(player.getUUID()); if(confirmation!=null && confirmation.privateRoom()>0) failOffer(confirmation,null);
        if (ranked.containsKey(room.id())) { forfeit(room, player.getUUID()); return; }
        if (room.owner().equals(player.getUUID()) || room.state() != PvpRoom.State.WAITING) close(room, "Room closed");
        else { room.leave(player.getUUID()); membership.remove(player.getUUID()); message(room, "ผู้เล่นออกจากห้อง"); notifyRoom(room); }
    }
    public void cancel(ServerPlayer player) {
        var room = require(player);
        if (ranked.containsKey(room.id())) { forfeit(room, player.getUUID()); return; }
        if (!room.owner().equals(player.getUUID())) throw new IllegalArgumentException("Only the owner can cancel this room");
        close(room, "Owner cancelled the room");
    }
    public void start(ServerPlayer owner) {
        var room=require(owner);
        if(!room.owner().equals(owner.getUUID()) || room.state()!=PvpRoom.State.WAITING || room.members().size()!=2 || isRanked(room.id()))
            throw new IllegalArgumentException("เฉพาะหัวหน้าห้องที่มีผู้เล่นครบ 2 คนเท่านั้นที่เริ่มได้");
        var opponent=server.getPlayerList().getPlayer(room.members().get(1));
        if(opponent==null) throw new IllegalArgumentException("คู่ต่อสู้ไม่ได้ออนไลน์");
        eligible(owner); eligible(opponent); checkOffer(owner); checkOffer(opponent);
        invitations.revokeRoom(room.id());
        var c=new MatchConfirmation(owner.getUUID(),opponent.getUUID(),ticks);
        var offer=new Offer(c,null,null,preview(owner,-1),preview(opponent,-1),room.id(),room.arena());
        offers.put(c.id,offer); sendOffer(offer); offerSound(offer,PvpSoundEffects.Cue.FOUND); loadOfferPositions(offer);
    }
    private void startArena(ServerPlayer owner) {
        var room = require(owner);
        if (!room.owner().equals(owner.getUUID())) throw new IllegalArgumentException("Only the owner can start");
        if (!room.canStart()) throw new IllegalArgumentException("Two players are required");
        ServerLevel level = server.getLevel(PvpDimensions.LEVEL);
        if (level == null) throw new IllegalArgumentException("PvP dimension is unavailable");
        var pieces = PvpArenaData.get(server).pieces(room.arena());
        if(pieces.isEmpty()) throw new IllegalArgumentException("Arena has no structures");
        var players = room.members().stream().map(server.getPlayerList()::getPlayer).toList();
        for (ServerPlayer player : players) {
            if (player == null) throw new IllegalArgumentException("Both players must be online");
            eligible(player);
        }
        room.beginLoading();
        placements.put(room.id(),new PendingLoad(pieces));
        message(room,"กำลังเตรียมสนาม กรุณารอที่เดิม ระบบจะวาร์ปเมื่อสนามพร้อม");
    }
    private void initializePlacement(PvpRoom room,PendingLoad pending,ServerLevel level) {
        var layout=PvpArenaTemplates.assemble(pending.resolved); var size=layout.size();
        var recovery = PvpRecoveryData.get(server);
        int slot = 0;
        while (recovery.regions().containsKey(slot)) slot++;
        if (slot >= 128) throw new IllegalArgumentException("All PvP slots are occupied or being cleaned");
        BlockPos origin = new BlockPos(1_000_000 + slot * 1024, 32, 1_000_000);
        if (!level.getWorldBorder().isWithinBounds(origin)
                || !level.getWorldBorder().isWithinBounds(origin.offset(size.getX()-1, 0, size.getZ()-1))) {
            throw new IllegalArgumentException("PvP slot is outside the world border");
        }
        recovery.reserve(slot, new PvpRecoveryData.Region(origin, size.getX(), size.getY(), size.getZ()));
        Run run = new Run(slot, origin, layout, ticks);
        runs.put(room.id(), run);
        pending.placement=new com.ambition.neoworld.pvp.arena.PvpArenaPlacement(level,layout,origin);
    }
    private void tickPlacements() {
        if(placements.isEmpty()) return;
        int id=placements.keySet().iterator().next(); var room=rooms.get(id); var placement=placements.get(id);
        if(room==null) { placements.remove(id).close(); return; }
        try {
            if(placement.started<0) placement.started=ticks;
            if(ticks-placement.started>12000) throw new IllegalArgumentException("หมดเวลาเตรียมสนาม");
            for(UUID playerId:room.members()) {
                var player=server.getPlayerList().getPlayer(playerId);
                if(player==null) throw new IllegalArgumentException("ผู้เล่นออกระหว่างเตรียมสนาม");
                eligible(player);
            }
            if(placement.placement==null) {
                var level=server.getLevel(PvpDimensions.LEVEL);
                if(level==null) throw new IllegalArgumentException("PvP dimension is unavailable");
                if(placement.resolved.size()<placement.pieces.size()) {
                    placement.resolved.add(PvpArenaTemplates.resolve(level,placement.pieces.get(placement.resolved.size())));
                    if(ticks%100==0) message(room,"กำลังอ่านสนาม "+placement.resolved.size()+"/"+placement.pieces.size()+" ชิ้น");
                    return;
                }
                initializePlacement(room,placement,level); return;
            }
            if(placement.placement.tick()) {
                finishLoading(room);
                placements.remove(id); placement.close();
            } else if(ticks%100==0) message(room,"กำลังเตรียมสนาม "+placement.placement.percent()+"%");
        } catch(RuntimeException exception) {
            close(room,"เตรียมสนามไม่สำเร็จ ยกเลิกโดยไม่หักคะแนน: "+exception.getMessage());
            NeoWorld.LOGGER.warn("PvP room {} preparation failed",id,exception);
        }
    }
    private void finishLoading(PvpRoom room) {
        var run=runs.get(room.id()); var layout=run.layout(); var origin=run.origin();
        var level=server.getLevel(PvpDimensions.LEVEL);
        for(var marker:layout.markers().values()) level.setBlock(origin.offset(marker),Blocks.AIR.defaultBlockState(),2);
        for(String name:List.of("pvp_spawn_a","pvp_spawn_b")) {
            var spawn=layout.point(name,origin);
            if(!level.getBlockState(spawn).getCollisionShape(level,spawn).isEmpty()
                    || !level.getBlockState(spawn.above()).getCollisionShape(level,spawn.above()).isEmpty()
                    || !level.getBlockState(spawn.below()).isCollisionShapeFullBlock(level,spawn.below()))
                throw new IllegalArgumentException("Spawn requires empty feet/head cells and a solid floor: "+name);
        }
        var players=room.members().stream().map(server.getPlayerList()::getPlayer).toList();
        for(var player:players) eligible(player);
        for(var player:players) PvpRecoveryData.get(server).capture(player);
        run=new Run(run.slot(),origin,layout,ticks); runs.put(room.id(),run);
        var match=ranked.get(room.id());
        if(match!=null) resetRound(room,match);
        else {
            for(int i=0;i<players.size();i++) {
                var player=players.get(i); var spawn=layout.point(i==0 ? "pvp_spawn_a" : "pvp_spawn_b",origin);
                player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
                player.teleportTo(level,spawn.getX()+0.5,spawn.getY(),spawn.getZ()+0.5,i==0 ? -90 : 90,0); player.fallDistance=0;
            }
            waitForClients(room);
        }
        notifyRoom(room);
    }
    private void waitForClients(PvpRoom room) {
        room.awaitClients();
        var barrier=new PvpClientLoadBarrier(room.members(),ticks); clientLoads.put(room.id(),barrier);
        var run=runs.get(room.id());
        for(int i=0;i<room.members().size();i++) {
            var player=server.getPlayerList().getPlayer(room.members().get(i));
            var spawn=run.layout().point(i==0 ? "pvp_spawn_a" : "pvp_spawn_b",run.origin());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.ambition.neoworld.network.pvp.PvpArenaLoadPayload(barrier.token(),true,spawn));
        }
        preparation(room,run,0);
        message(room,"สนามพร้อม กำลังรอเครื่องผู้เล่นทั้งสองโหลดพื้นที่ (สูงสุด 60 วินาที)");
    }
    public void arenaReady(ServerPlayer player,UUID token) {
        var room=room(player.getUUID());
        if(room==null || room.state()!=PvpRoom.State.CLIENT_LOADING) return;
        var barrier=clientLoads.get(room.id()); var run=runs.get(room.id());
        if(barrier==null || run==null || !player.isAlive() || !player.serverLevel().dimension().equals(PvpDimensions.LEVEL)
                || player.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.ADVENTURE) return;
        int index=room.members().indexOf(player.getUUID()); if(index<0) return;
        var spawn=run.layout().point(index==0 ? "pvp_spawn_a" : "pvp_spawn_b",run.origin());
        if(player.distanceToSqr(spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5)>1) return;
        barrier.accept(token,player.getUUID(),ticks);
    }
    private void clearClientLoad(PvpRoom room) {
        var barrier=clientLoads.remove(room.id()); if(barrier==null) return;
        for(var id:room.members()) {
            var player=server.getPlayerList().getPlayer(id);
            if(player!=null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    com.ambition.neoworld.network.pvp.PvpArenaLoadPayload.closed(barrier.token()));
        }
    }
    private void tickClientLoad(PvpRoom room,Run run) {
        var barrier=clientLoads.get(room.id());
        if(barrier==null || barrier.expired(ticks)) {
            close(room,"โหลดสนามไม่ทันเวลา ยกเลิกก่อนเริ่มสู้โดยไม่หัก Rating"); return;
        }
        for(var id:room.members()) {
            var player=server.getPlayerList().getPlayer(id);
            if(player==null || !player.isAlive() || !player.serverLevel().dimension().equals(PvpDimensions.LEVEL)
                    || player.gameMode.getGameModeForPlayer()!=net.minecraft.world.level.GameType.ADVENTURE) {
                close(room,"ผู้เล่นไม่พร้อมระหว่างโหลดสนาม ยกเลิกโดยไม่หัก Rating"); return;
            }
        }
        preparation(room,run,0);
        if(!barrier.ready(ticks)) {
            if(ticks%40==0) for(var id:room.members()) combatTitle(server.getPlayerList().getPlayer(id),
                    Component.literal("กำลังโหลดสนาม").withStyle(net.minecraft.ChatFormatting.GOLD),"รอผู้เล่นทั้งสองฝ่าย",45);
            return;
        }
        clearClientLoad(room); room.begin();
        run=new Run(run.slot(),run.origin(),run.layout(),ticks); runs.put(room.id(),run);
        var match=ranked.get(room.id());
        if(match!=null) { match.countdown=ticks+100; match.roundStarted=match.countdown; }
        preparation(room,run,ticks+100);
        notifyRoom(room); message(room,"ผู้เล่นทั้งสองโหลดสนามพร้อมแล้ว เริ่มต่อสู้ใน 5 วินาที");
    }
    public void disconnect(ServerPlayer player) {
        sounds.forget(player.getUUID()); countdownSounds.remove(player.getUUID());
        invitations.revokePlayer(player.getUUID());
        var pending=offer(player.getUUID()); if(pending!=null) failOffer(pending,player.getUUID());
        removeQueue(player.getUUID());
        var room = room(player.getUUID());
        if (room != null && ranked.containsKey(room.id())) {
            forfeit(room,player.getUUID());
            return;
        }
        if (room != null) close(room, "A player disconnected; room closed");
    }
    public void death(ServerPlayer player) {
        var room = room(player.getUUID());
        if (room != null && ranked.containsKey(room.id())) {
            var match = ranked.get(room.id());
            if (room.state() != PvpRoom.State.ACTIVE || match.result != null) return;
            endRankedRound(room,match,match.a.equals(player.getUUID()) ? match.b : match.a,player.getUUID());
            return;
        }
        if(room!=null && room.state()==PvpRoom.State.ACTIVE) {
            room.pause(); player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            intermissions.put(room.id(),RoundIntermission.begin(ticks,player.getUUID(),true,null));
            announceIntermission(room,null);
        }
    }
    private void close(PvpRoom room, String reason) {
        clearClientLoad(room);
        var placement=placements.remove(room.id()); if(placement!=null) placement.close();
        invitations.revokeRoom(room.id());
        var confirmation=offer(room.owner()); if(confirmation!=null && confirmation.privateRoom()==room.id()) removeOffer(confirmation);
        message(room, reason);
        for (UUID id : room.members()) {
            membership.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.isAlive()) recover(player);
        }
        rooms.remove(room.id());
        notifyRoom(room);
        intermissions.remove(room.id());
        ranked.remove(room.id());
        Run run = runs.remove(room.id());
        if (run != null) cleanup.add(new Cleanup(run.slot(), PvpRecoveryData.get(server).regions().get(run.slot())));
    }
    public void recover(ServerPlayer player) {
        var current = room(player.getUUID());
        if (current != null && ranked.containsKey(current.id())) return;
        var data = PvpRecoveryData.get(server); var point = data.point(player.getUUID());
        if (point == null || !player.isAlive()) return;
        ResourceLocation id = ResourceLocation.tryParse(point.getString("dimension"));
        ServerLevel destination = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        boolean fallback = destination == null || destination.dimension().equals(PvpDimensions.LEVEL);
        if (fallback) destination = server.overworld();
        BlockPos spawn = destination.getSharedSpawnPos();
        player.teleportTo(destination, fallback ? spawn.getX()+0.5 : point.getDouble("x"),
                fallback ? spawn.getY() : point.getDouble("y"), fallback ? spawn.getZ()+0.5 : point.getDouble("z"),
                point.getFloat("yaw"), point.getFloat("pitch"));
        restoreMode(player);
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
        player.setHealth(Math.max(1, Math.min(player.getMaxHealth(), point.getFloat("health"))));
        player.fallDistance = 0; player.clearFire();
        data.recovered(player.getUUID());
    }
    private void message(PvpRoom room, String text) {
        for (UUID id : room.members()) { var player = server.getPlayerList().getPlayer(id); if (player != null) player.sendSystemMessage(Component.literal("[PvP] " + text)); }
    }
    public void tick() {
        ticks++;
        sounds.tick(server,ticks);
        tickPlacements();
        invitations.expire(ticks);
        tickOffers();
        if (ticks % 20 == 0) {
            matchQueue();
            for(UUID id:queue.keySet()) { var player=server.getPlayerList().getPlayer(id); if(player!=null) sendQueueState(player); }
        }
        if (ticks % 100 == 0) retryResults();
        for (var room : List.copyOf(rooms.values())) {
            Run run = runs.get(room.id()); if (run == null) continue;
            if(room.state()==PvpRoom.State.LOADING) continue;
            if(room.state()==PvpRoom.State.CLIENT_LOADING) { tickClientLoad(room,run); continue; }
            if (ranked.containsKey(room.id())) { tickRanked(room, run, ranked.get(room.id())); continue; }
            if(intermissions.containsKey(room.id())) { tickIntermission(room,null); continue; }
            if(room.state()==PvpRoom.State.COUNTDOWN) preparation(room,run,run.started()+100);
            boolean invalid = false;
            for (UUID id : room.members()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null || !player.isAlive() || !player.serverLevel().dimension().equals(PvpDimensions.LEVEL)
                        || !run.layout().contains(run.origin(),player.getX(),player.getY(),player.getZ())) invalid = true;
            }
            if (invalid || ticks - run.started() >= 12000) { close(room, "Room ended: player left the arena or time expired"); continue; }
            if (room.state() == PvpRoom.State.COUNTDOWN && ticks - run.started() >= 100) { room.activate(); fightTitle(room); message(room, "Fight!"); }
        }
        clean();
    }
    public void queue(ServerPlayer player) {
        eligible(player);
        checkOffer(player);
        int penalty=PvpQueuePenaltyData.get(server).remaining(player.getUUID(),System.currentTimeMillis());
        if(penalty>0) throw new IllegalArgumentException("Ranked queue cooldown: " + penalty + " seconds");
        if (room(player.getUUID()) != null || queue.containsKey(player.getUUID())) throw new IllegalArgumentException("Already queued or in a room");
        var level = server.getLevel(PvpDimensions.LEVEL);
        if(level==null || PvpArenaData.get(server).all().entrySet().stream().noneMatch(entry -> rankedArenaReady(level,entry.getKey(),entry.getValue())))
            throw new IllegalArgumentException("ยังไม่มีสนามจัดอันดับพร้อมใช้งาน");
        try {
            var rating = PvpRatingRepository.player(player.getUUID(), player.getGameProfile().getName());
            queue.put(player.getUUID(), new QueueEntry(rating.rating(), ticks));
            sendQueueState(player);
            sounds.emit(player,PvpSoundEffects.Cue.QUEUED,ticks);
            player.sendSystemMessage(Component.literal("[PvP] Ranked queue joined. Rating " + rating.rating()));
        } catch (java.sql.SQLException exception) { throw new IllegalArgumentException("Rating database unavailable; queue not joined"); }
    }
    public void unqueue(ServerPlayer player) {
        if (!removeQueue(player.getUUID())) throw new IllegalArgumentException("Not in ranked queue");
        sounds.emit(player,PvpSoundEffects.Cue.CANCELLED,ticks);
        player.sendSystemMessage(Component.literal("[PvP] Left ranked queue"));
    }
    private static String pair(UUID a, UUID b) { return a.compareTo(b) < 0 ? a+":"+b : b+":"+a; }
    private void matchQueue() {
        recentPairs.entrySet().removeIf(entry -> ticks-entry.getValue() >= 12000);
        for (UUID id : List.copyOf(queue.keySet())) {
            var player = server.getPlayerList().getPlayer(id);
            try { if (player == null) throw new IllegalArgumentException(); eligible(player); }
            catch (IllegalArgumentException exception) { removeQueue(id); }
        }
        var ids = List.copyOf(queue.keySet());
        for (int i=0; i<ids.size(); i++) {
            UUID a=ids.get(i); if (!queue.containsKey(a)) continue;
            QueueEntry first=queue.get(a);
            UUID best=null; int distance=Integer.MAX_VALUE;
            for (int j=i+1; j<ids.size(); j++) {
                UUID b=ids.get(j); QueueEntry second=queue.get(b); if (second==null) continue;
                int allowed=(int)Math.min(600,100+(ticks-Math.min(first.entered(),second.entered()))/400*50);
                int gap=Math.abs(first.rating()-second.rating());
                if (gap<=allowed && gap<distance && !recentPairs.containsKey(pair(a,b))) { best=b; distance=gap; }
            }
            if (best==null) continue;
            var arena=randomRankedArena();
            if(arena.isEmpty()) {
                if(!warnedNoRankedArena) for(UUID id:queue.keySet()) {
                    var waiting=server.getPlayerList().getPlayer(id);
                    if(waiting!=null) waiting.sendSystemMessage(Component.literal("[PvP] รอสนามพร้อมใช้งาน คิวเดิมยังคงอยู่"));
                }
                warnedNoRankedArena=true; return;
            }
            warnedNoRankedArena=false;
            var owner=server.getPlayerList().getPlayer(a); var opponent=server.getPlayerList().getPlayer(best);
            QueueEntry second=queue.get(best);
            removeQueue(a); removeQueue(best);
            try {
                var confirmation=new MatchConfirmation(a,best,ticks);
                var offer=new Offer(confirmation,first,second,preview(owner,first.rating()),preview(opponent,second.rating()),0,arena.get());
                offers.put(confirmation.id,offer); sendOffer(offer); offerSound(offer,PvpSoundEffects.Cue.FOUND); loadOfferPositions(offer);
                owner.sendSystemMessage(Component.literal("[PvP] สุ่มสนาม: "+arena.get()));
                opponent.sendSystemMessage(Component.literal("[PvP] สุ่มสนาม: "+arena.get()));
            } catch (RuntimeException exception) {
                if (owner!=null) owner.sendSystemMessage(Component.literal("[PvP] Matchmaking could not start the arena; rejoin the queue"));
                if (opponent!=null) opponent.sendSystemMessage(Component.literal("[PvP] Matchmaking could not start the arena; rejoin the queue"));
                NeoWorld.LOGGER.warn("Ranked arena start failed",exception);
            }
        }
    }
    private java.util.Optional<String> randomRankedArena() {
        var level=server.getLevel(PvpDimensions.LEVEL);
        if(level==null) return java.util.Optional.empty();
        var arenas=PvpArenaData.get(server).all();
        return com.ambition.neoworld.pvp.ranked.RankedArenaSelector.pick(arenas.keySet().stream().sorted().toList(),id -> rankedArenaReady(level,id,arenas.get(id)),java.util.concurrent.ThreadLocalRandom.current());
    }
    private boolean rankedArenaReady(ServerLevel level,String id,PvpArenaData.Arena definition) {
        if(!definition.validated() || definition.pieces().isEmpty() || com.ambition.neoworld.pvp.arena.PvpCaptureService.isSaving(server,id)) return false;
        try { return PvpArenaTemplates.resourcesPresent(level,definition.pieces()); }
        catch(RuntimeException exception) { return false; }
    }
    public PvpMatchOfferPayload.Player preview(ServerPlayer player,int rating) {
        var equipment=java.util.Arrays.stream(new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.HEAD})
                .map(slot -> player.getItemBySlot(slot).copy()).toList();
        return new PvpMatchOfferPayload.Player(player.getUUID(),player.getGameProfile().getName(),rating,-1,equipment);
    }
    private void loadOfferPositions(Offer offer) {
        var cacheA=com.ambition.neoworld.pvp.ranked.PvpRankingCache.player(offer.a().id(),offer.a().name()).exceptionally(error -> null);
        var cacheB=com.ambition.neoworld.pvp.ranked.PvpRankingCache.player(offer.b().id(),offer.b().name()).exceptionally(error -> null);
        cacheA.thenCombine(cacheB,(a,b) -> new PvpRatingRepository.View[]{a,b}).thenAccept(positions -> server.execute(() -> {
            if(!offers.containsKey(offer.confirmation().id)) return;
            var a=offer.a(); var b=offer.b();
            var updated=new Offer(offer.confirmation(),offer.first(),offer.second(),
                    new PvpMatchOfferPayload.Player(a.id(),a.name(),positions[0]==null ? a.rating() : positions[0].player().rating(),positions[0]==null ? -1 : positions[0].position(),a.equipment()),
                    new PvpMatchOfferPayload.Player(b.id(),b.name(),positions[1]==null ? b.rating() : positions[1].player().rating(),positions[1]==null ? -1 : positions[1].position(),b.equipment()),offer.privateRoom(),offer.arena());
            offers.put(offer.confirmation().id,updated); sendOffer(updated);
        }));
    }
    private void offerSound(Offer offer,PvpSoundEffects.Cue cue) {
        for(var id:List.of(offer.confirmation().a,offer.confirmation().b)) {
            var player=server.getPlayerList().getPlayer(id); if(player!=null) sounds.emit(player,cue,ticks);
        }
    }
    private void sendOffer(Offer offer) {
        var c=offer.confirmation();
        for(UUID id:List.of(c.a,c.b)) {
            var player=server.getPlayerList().getPlayer(id); if(player==null) continue;
            boolean own=c.a.equals(id);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new PvpMatchOfferPayload(c.id,true,c.remaining(ticks),
                    own ? offer.a() : offer.b(),own ? offer.b() : offer.a(),c.ready(id),c.ready(own ? c.b : c.a)));
        }
    }
    private void removeOffer(Offer offer) {
        offers.remove(offer.confirmation().id);
        for(UUID id:List.of(offer.confirmation().a,offer.confirmation().b)) {
            var player=server.getPlayerList().getPlayer(id);
            if(player!=null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,PvpMatchOfferPayload.closed(offer.confirmation().id));
        }
    }
    public void confirm(ServerPlayer player,UUID offerId) {
        var offer=offers.get(offerId);
        if(offer==null || !offer.confirmation().contains(player.getUUID())) return;
        if(offer.confirmation().ready(player.getUUID())) return;
        if(offer.confirmation().accept(player.getUUID(),ticks)) {
            sounds.emit(player,PvpSoundEffects.Cue.ACCEPTED,ticks); sendOffer(offer);
        }
    }
    private void requeue(UUID id,QueueEntry entry) {
        var player=server.getPlayerList().getPlayer(id); if(player==null) return;
        try {
            eligible(player); if(room(id)!=null) return;
            if(PvpQueuePenaltyData.get(server).remaining(id,System.currentTimeMillis())>0) return;
            queue.put(id,entry); sendQueueState(player);
            player.sendSystemMessage(Component.literal("[PvP] Returning to ranked queue with original waiting time"));
        } catch(IllegalArgumentException ignored) { }
    }
    private void failOffer(Offer offer,UUID disconnected) {
        offerSound(offer,PvpSoundEffects.Cue.ERROR);
        removeOffer(offer); var c=offer.confirmation();
        if(offer.privateRoom()>0) {
            var room=rooms.get(offer.privateRoom());
            if(room!=null) {
                message(room,"ยืนยันไม่ครบ กลับห้องเดิมโดยไม่หักคะแนนหรือลงโทษ");
                reopenRoom(room);
            }
            return;
        }
        for(UUID id:List.of(c.a,c.b)) {
            boolean penalize=disconnected==null ? !c.ready(id) : id.equals(disconnected);
            if(penalize) {
                PvpQueuePenaltyData.get(server).penalize(id,System.currentTimeMillis());
                var player=server.getPlayerList().getPlayer(id);
                if(player!=null) player.sendSystemMessage(Component.literal("[PvP] Match not confirmed. Ranked queue suspended for 60 seconds; Rating unchanged."));
            } else requeue(id,c.a.equals(id) ? offer.first() : offer.second());
        }
    }
    private void tickOffers() {
        for(var offer:List.copyOf(offers.values())) {
            var c=offer.confirmation();
            var a=server.getPlayerList().getPlayer(c.a); var b=server.getPlayerList().getPlayer(c.b);
            if(a==null || b==null) { failOffer(offer,a==null ? c.a : c.b); continue; }
            if(c.status(ticks)==MatchConfirmation.Status.EXPIRED) { failOffer(offer,null); continue; }
            if(c.status(ticks)==MatchConfirmation.Status.ACCEPTED) { startConfirmed(offer,a,b); continue; }
            long remaining=c.deadline-ticks;
            if(remaining>0 && remaining<=100 && remaining%20==0) {
                if(!c.ready(c.a)) sounds.emit(a,PvpSoundEffects.Cue.TICK,ticks);
                if(!c.ready(c.b)) sounds.emit(b,PvpSoundEffects.Cue.TICK,ticks);
            }
            if(ticks%20==0) sendOffer(offer);
        }
    }
    private void startConfirmed(Offer offer,ServerPlayer owner,ServerPlayer opponent) {
        offerSound(offer,PvpSoundEffects.Cue.BOTH_READY);
        if(offer.privateRoom()>0) {
            removeOffer(offer);
            var room=rooms.get(offer.privateRoom());
            if(room==null || !room.owner().equals(owner.getUUID()) || room.state()!=PvpRoom.State.WAITING
                    || !room.members().equals(List.of(owner.getUUID(),opponent.getUUID()))) return;
            try {
                startArena(owner); notifyRoom(room);
            } catch(RuntimeException exception) {
                message(room,"ไม่สามารถเริ่มสนามได้: "+exception.getMessage());
                if(rooms.containsKey(room.id())) reopenRoom(room);
                NeoWorld.LOGGER.warn("Private PvP arena start failed",exception);
            }
            return;
        }
        removeOffer(offer); PvpRoom room=null;
        try {
            room=rooms.get(create(owner,offer.arena())); join(opponent,room.id());
            var match=new RankedMatch(owner.getUUID(),opponent.getUUID(),owner.getGameProfile().getName(),opponent.getGameProfile().getName(),ticks);
            ranked.put(room.id(),match); startArena(owner);
            message(room,"Ranked 1vs1: first to 2 round wins. Leaving forfeits the match.");
        } catch(RuntimeException exception) {
            if(room!=null && rooms.containsKey(room.id())) close(room,"Ranked start cancelled without penalty");
            owner.sendSystemMessage(Component.literal("[PvP] Could not start arena; no Rating or queue penalty. Rejoin when arena is available."));
            opponent.sendSystemMessage(Component.literal("[PvP] Could not start arena; no Rating or queue penalty. Rejoin when arena is available."));
            NeoWorld.LOGGER.warn("Confirmed PvP arena start failed",exception);
        }
    }
    private void resetRound(PvpRoom room, RankedMatch match) {
        var run=runs.get(room.id()); var level=server.getLevel(PvpDimensions.LEVEL);
        if(match.began) { room.pause(); match.countdown=ticks+60; match.roundStarted=match.countdown; }
        for (int i=0;i<room.members().size();i++) {
            var player=server.getPlayerList().getPlayer(room.members().get(i)); if (player==null) continue;
            player.setCamera(player);
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
            var spawn=run.layout().point(i==0 ? "pvp_spawn_a" : "pvp_spawn_b",run.origin());
            player.teleportTo(level,spawn.getX()+0.5,spawn.getY(),spawn.getZ()+0.5,i==0 ? -90 : 90,0);
            player.setHealth(player.getMaxHealth()); player.removeAllEffects(); player.setAbsorptionAmount(0);
            player.clearFire(); player.fallDistance=0;
        }
        var box=new AABB(run.origin().getX(),run.origin().getY(),run.origin().getZ(),
                run.origin().getX()+run.layout().size().getX(),run.origin().getY()+run.layout().size().getY(),run.origin().getZ()+run.layout().size().getZ());
        level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,box).forEach(net.minecraft.world.entity.Entity::discard);
        if(match.began) preparation(room,run,match.countdown);
        else waitForClients(room);
    }
    private void forfeit(PvpRoom room, UUID loser) {
        var match=ranked.get(room.id());
        if(!match.exit(loser)) {
            if(match.result==null) close(room,"Cancelled before first round; no Rating change");
            return;
        }
        room.pause(); finishRanked(room,match);
    }
    private void finishRanked(PvpRoom room, RankedMatch match) {
        if(match.resultSaved) return;
        room.pause();
        PvpResultOutbox.get(server).add(match);
        try {
            var report=PvpRatingRepository.result(match.id,match.a,match.b,match.nameA,match.nameB,match.result,match.reason);
            PvpResultOutbox.get(server).saved(match.id);
            recentPairs.put(pair(match.a,match.b),ticks);
            message(room,"Ranked result saved. " + (match.result==0.5 ? "Draw" : "Winner: " + (match.result==1 ? match.nameA : match.nameB)));
            match.resultSaved=true;
            UUID defeated=match.result==0.5 ? null : match.result==1 ? match.b : match.a;
            var loser=defeated==null ? null : server.getPlayerList().getPlayer(defeated);
            if(loser!=null) { loser.setHealth(Math.max(1,loser.getHealth())); loser.setGameMode(net.minecraft.world.level.GameType.SPECTATOR); }
            intermissions.put(room.id(),RoundIntermission.begin(ticks,defeated,true,report));
            announceIntermission(room,match);
        } catch (java.sql.SQLException exception) {
            if (ticks%100==0) { message(room,"Rating save pending; retrying safely"); NeoWorld.LOGGER.error("Ranked result save failed for {}",match.id,exception); }
        }
    }
    private void tickRanked(PvpRoom room, Run run, RankedMatch match) {
        if (match.result!=null) {
            if(match.resultSaved) tickIntermission(room,match);
            else if(ticks%100==0) finishRanked(room,match);
            return;
        }
        if (ticks-run.started()>=12000) { match.finish(match.winsA==match.winsB ? 0.5 : match.winsA>match.winsB ? 1 : 0,"match_timeout"); finishRanked(room,match); return; }
        var a=server.getPlayerList().getPlayer(match.a); var b=server.getPlayerList().getPlayer(match.b);
        if (a==null || b==null) {
            if(a==null && b==null) close(room,"Both unavailable without a recorded exit; match void");
            else forfeit(room,a==null ? match.a : match.b);
            return;
        }
        if(intermissions.containsKey(room.id())) { tickIntermission(room,match); return; }
        if(room.state()==PvpRoom.State.COUNTDOWN) preparation(room,run,match.countdown);
        for (var player : List.of(a,b)) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator() || !player.serverLevel().dimension().equals(PvpDimensions.LEVEL)
                    || !run.layout().contains(run.origin(),player.getX(),player.getY(),player.getZ())) {
                forfeit(room,player.getUUID()); return;
            }
        }
        if (room.state()==PvpRoom.State.COUNTDOWN && ticks>=match.countdown) {
            room.activate(); match.began=true; fightTitle(room); message(room,"Ranked round: Fight!");
        }
        if (room.state()==PvpRoom.State.ACTIVE && ticks-match.roundStarted>=3600) {
            float ha=a.getHealth()/a.getMaxHealth(), hb=b.getHealth()/b.getMaxHealth();
            if(Float.compare(ha,hb)!=0) endRankedRound(room,match,ha>hb ? match.a : match.b,ha>hb ? match.b : match.a);
            else {
                room.pause(); intermissions.put(room.id(),RoundIntermission.begin(ticks,null,false,null));
                announceIntermission(room,match);
            }
        }
    }
    private void endRankedRound(PvpRoom room,RankedMatch match,UUID winner,UUID defeated) {
        match.round(winner); room.pause();
        var loser=server.getPlayerList().getPlayer(defeated);
        if(loser!=null) { loser.setHealth(Math.max(1,loser.getHealth())); loser.setGameMode(net.minecraft.world.level.GameType.SPECTATOR); }
        message(room,"Round score " + match.winsA + " : " + match.winsB);
        if(match.result!=null) { finishRanked(room,match); }
        else { intermissions.put(room.id(),RoundIntermission.begin(ticks,defeated,false,null)); announceIntermission(room,match); }
    }
    private void preparation(PvpRoom room,Run run,long deadline) {
        long remaining=Math.max(0,deadline-ticks);
        var level=server.getLevel(PvpDimensions.LEVEL); if(level==null) return;
        for(int i=0;i<room.members().size();i++) {
            var player=server.getPlayerList().getPlayer(room.members().get(i));
            if(player==null || player.serverLevel()!=level) continue;
            var spawn=run.layout().point(i==0 ? "pvp_spawn_a" : "pvp_spawn_b",run.origin());
            double x=spawn.getX()+0.5,y=spawn.getY(),z=spawn.getZ()+0.5;
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.fallDistance=0;
            if(player.distanceToSqr(x,y,z)>0.000001)
                player.teleportTo(level,x,y,z,player.getYRot(),player.getXRot());
            if(remaining>0 && remaining%20==0) {
                combatTitle(player,PvpTitles.countdown((int)((remaining+19)/20)),"เตรียมพร้อมต่อสู้",25);
                if(!java.util.Objects.equals(countdownSounds.put(player.getUUID(),ticks),ticks))
                    sounds.emit(player,PvpSoundEffects.Cue.TICK,ticks);
            }
        }
    }
    private void fightTitle(PvpRoom room) {
        for(UUID id:room.members()) {
            var player=server.getPlayerList().getPlayer(id);
            if(player!=null) { combatTitle(player,PvpTitles.fight(),"",30); sounds.emit(player,PvpSoundEffects.Cue.FIGHT,ticks); }
        }
    }
    private void combatTitle(ServerPlayer player,Component title,String subtitle,int duration) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0,duration,5));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(title));
    }
    private void restoreMode(ServerPlayer player) {
        var point=PvpRecoveryData.get(server).point(player.getUUID()); if(point==null) return;
        player.setCamera(player);
        if(point.contains("gameMode")) player.setGameMode(net.minecraft.world.level.GameType.byId(point.getInt("gameMode")));
        else if(player.isSpectator()) player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    }
    private String resultSubtitle(PvpRoom room,RankedMatch match,UUID player,RoundIntermission pause) {
        int seconds=pause.seconds(ticks);
        if(!pause.terminal()) return "กลับจุดเกิดใน " + seconds + " วิ • ผล " + match.winsA + "–" + match.winsB;
        if(match==null) return "ผล " + (player.equals(pause.defeated()) ? "0–1" : "1–0") + " • ไม่คิด Rating • กลับใน " + seconds + " วิ";
        boolean own=player.equals(match.a);
        String score=(own ? match.winsA : match.winsB)+"–"+(own ? match.winsB : match.winsA);
        String rating="บันทึกผลแล้ว";
        if(pause.report()!=null && pause.report().available()) {
            int before=own ? pause.report().beforeA() : pause.report().beforeB();
            int after=own ? pause.report().afterA() : pause.report().afterB();
            rating=String.format(java.util.Locale.ROOT,"Rating %+,d (%,d)",after-before,after);
        }
        return score+" • "+rating+" • กลับใน "+seconds+" วิ";
    }
    private Component resultSubtitleComponent(PvpRoom room,RankedMatch match,UUID player,RoundIntermission pause) {
        if(!pause.terminal() || match==null || pause.report()==null || !pause.report().available())
            return Component.literal(resultSubtitle(room,match,player,pause));
        boolean own=player.equals(match.a);
        int before=own ? pause.report().beforeA() : pause.report().beforeB();
        int after=own ? pause.report().afterA() : pause.report().afterB();
        String score=(own ? match.winsA : match.winsB)+"–"+(own ? match.winsB : match.winsA);
        return Component.literal(score+" • ").append(PvpTitles.ratingChange(before,after))
                .append(" • กลับใน "+pause.seconds(ticks)+" วิ");
    }
    private void announceIntermission(PvpRoom room,RankedMatch match) {
        var pause=intermissions.get(room.id());
        for(UUID id:room.members()) {
            var player=server.getPlayerList().getPlayer(id); if(player==null) continue;
            String title;
            if(pause.defeated()!=null) {
                boolean lost=id.equals(pause.defeated());
                sounds.emit(player,pause.terminal() ? (lost ? PvpSoundEffects.Cue.DEFEAT : PvpSoundEffects.Cue.VICTORY)
                        : (lost ? PvpSoundEffects.Cue.ROUND_LOSS : PvpSoundEffects.Cue.ROUND_WIN),ticks);
            }
            net.minecraft.ChatFormatting color;
            if(pause.defeated()==null) { title=pause.terminal() ? "เสมอ" : "รอบเสมอ"; color=net.minecraft.ChatFormatting.GOLD; }
            else if(id.equals(pause.defeated())) { title=pause.terminal() ? "คุณแพ้การแข่งขัน" : "คุณแพ้รอบนี้"; color=net.minecraft.ChatFormatting.RED; }
            else { title=pause.terminal() ? "ยินดีด้วย! คุณชนะ" : "ชนะรอบนี้"; color=net.minecraft.ChatFormatting.AQUA; }
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0,110,10));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(resultSubtitleComponent(room,match,id,pause)));
            Component decorated=pause.defeated()==null ? Component.literal(title).withStyle(color) : PvpTitles.result(!id.equals(pause.defeated()));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(decorated));
            if(pause.terminal()) player.sendSystemMessage(Component.literal("[PvP] " + title + " • " + resultSubtitle(room,match,id,pause)));
        }
    }
    private void tickIntermission(PvpRoom room,RankedMatch match) {
        var pause=intermissions.get(room.id()); if(pause==null) return;
        if(pause.finished(ticks)) {
            intermissions.remove(room.id());
            if(pause.terminal()) close(room,"Match complete; returning to original location");
            else resetRound(room,match);
            return;
        }
        if(ticks%20==0) for(UUID id:room.members()) {
            var player=server.getPlayerList().getPlayer(id);
            if(player!=null) player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(resultSubtitleComponent(room,match,id,pause)));
        }
    }
    private void retryResults() {
        for (var result:PvpResultOutbox.get(server).all()) {
            if (ranked.values().stream().anyMatch(match -> match.id.equals(result.match()))) continue;
            try {
                PvpRatingRepository.result(result.match(),result.a(),result.b(),result.nameA(),result.nameB(),result.score(),result.reason());
                PvpResultOutbox.get(server).saved(result.match());
            } catch (java.sql.SQLException exception) { NeoWorld.LOGGER.warn("Pending PvP result {} will retry",result.match()); break; }
        }
    }
    private void clean() {
        ServerLevel level = server.getLevel(PvpDimensions.LEVEL);
        if (level == null || cleanup.isEmpty()) return;
        Cleanup job = cleanup.peek(); var region = job.region;
        AABB box = new AABB(region.origin().getX(), region.origin().getY(), region.origin().getZ(),
                region.origin().getX()+region.x(), region.origin().getY()+region.y(), region.origin().getZ()+region.z());
        // Never clear a region while a player still occupies it (including pending recovery).
        if (!level.getEntitiesOfClass(ServerPlayer.class, box).isEmpty()) {
            cleanup.remove(); cleanup.add(job); return;
        }
        int total = region.x()*region.y()*region.z();
        int loadedChunks=0;
        for (int budget = 0; budget < 2048 && job.index < total; budget++, job.index++) {
            int x = job.index % region.x(), z = (job.index / region.x()) % region.z(), y = job.index / (region.x()*region.z());
            var position=region.origin().offset(x,y,z);
            if(!level.hasChunk(position.getX()>>4,position.getZ()>>4)) {
                if(loadedChunks>=4) break;
                level.getChunk(position.getX()>>4,position.getZ()>>4); loadedChunks++;
            }
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
        }
        if (job.index == total) {
            level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
                    entity -> !(entity instanceof ServerPlayer)).forEach(net.minecraft.world.entity.Entity::discard);
            PvpRecoveryData.get(server).release(job.slot); cleanup.remove();
        }
    }
}
