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
    private final Map<Integer, PvpRoom> rooms = new LinkedHashMap<>();
    private final Map<UUID, Integer> membership = new HashMap<>();
    private final Map<Integer, Run> runs = new HashMap<>();
    private final Map<Integer,RoundIntermission> intermissions=new HashMap<>();
    private final ArrayDeque<Cleanup> cleanup = new ArrayDeque<>();
    private final Map<UUID, QueueEntry> queue = new LinkedHashMap<>();
    private final Map<Integer, RankedMatch> ranked = new HashMap<>();
    private final Map<String, Long> recentPairs = new HashMap<>();
    private record QueueEntry(String arena, int rating, long entered) { }
    private record Offer(MatchConfirmation confirmation,QueueEntry first,QueueEntry second,
            PvpMatchOfferPayload.Player a,PvpMatchOfferPayload.Player b) { }
    private final Map<UUID,Offer> offers=new LinkedHashMap<>();
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
    public void shutdown() { for(var offer:List.copyOf(offers.values())) removeOffer(offer); for(UUID id:List.copyOf(queue.keySet())) removeQueue(id); for (var room : List.copyOf(rooms.values())) close(room, "Server stopping; room closed without penalty"); }
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
    public PvpRoom room(UUID player) { return rooms.get(membership.get(player)); }
    public boolean inArena(UUID player) { var room = room(player); return room != null && room.state() != PvpRoom.State.WAITING; }
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
        if (PvpArenaData.get(server).template(arena) == null) throw new IllegalArgumentException("Unknown arena: " + arena);
        var room = new PvpRoom(nextId++, arena, player.getUUID());
        rooms.put(room.id(), room); membership.put(player.getUUID(), room.id());
        message(room, "Room " + room.id() + " created. Invite your opponent with /neow pvp room join " + room.id());
        return room.id();
    }
    public void join(ServerPlayer player, int id) {
        eligible(player);
        checkOffer(player);
        if (queue.containsKey(player.getUUID()) || ranked.containsKey(id)) throw new IllegalArgumentException("Ranked opponents are selected by matchmaking");
        if (room(player.getUUID()) != null) throw new IllegalArgumentException("You already have a room");
        PvpRoom room = rooms.get(id);
        if (room == null) throw new IllegalArgumentException("Unknown room: " + id);
        room.join(player.getUUID()); membership.put(player.getUUID(), id);
        message(room, player.getGameProfile().getName() + " joined. Both players must use /neow pvp room ready");
    }
    private PvpRoom require(ServerPlayer player) {
        var room = room(player.getUUID());
        if (room == null) throw new IllegalArgumentException("You are not in a PvP room");
        return room;
    }
    public void ready(ServerPlayer player, boolean ready) {
        var room = require(player); room.setReady(player.getUUID(), ready);
        message(room, player.getGameProfile().getName() + (ready ? " is ready" : " is not ready"));
    }
    public void leave(ServerPlayer player) {
        var room = require(player);
        if (ranked.containsKey(room.id())) { forfeit(room, player.getUUID()); return; }
        if (room.owner().equals(player.getUUID()) || room.state() != PvpRoom.State.WAITING) close(room, "Room closed");
        else { room.leave(player.getUUID()); membership.remove(player.getUUID()); message(room, "Opponent left; readiness reset"); }
    }
    public void cancel(ServerPlayer player) {
        var room = require(player);
        if (ranked.containsKey(room.id())) { forfeit(room, player.getUUID()); return; }
        if (!room.owner().equals(player.getUUID())) throw new IllegalArgumentException("Only the owner can cancel this room");
        close(room, "Owner cancelled the room");
    }
    public void start(ServerPlayer owner) {
        var room = require(owner);
        if (!room.owner().equals(owner.getUUID())) throw new IllegalArgumentException("Only the owner can start");
        if (!room.canStart()) throw new IllegalArgumentException("Both players must be ready");
        ServerLevel level = server.getLevel(PvpDimensions.LEVEL);
        if (level == null) throw new IllegalArgumentException("PvP dimension is unavailable");
        var template = PvpArenaData.get(server).template(room.arena());
        var layout = PvpArenaTemplates.inspect(level, template);
        var size = layout.template().getSize();
        if (size.getX() > 512 || size.getZ() > 512 || size.getY() > 224 || (long)size.getX()*size.getY()*size.getZ() > 262144) {
            throw new IllegalArgumentException("Arena exceeds slot limits: 512 x 224 x 512, at most 262144 blocks");
        }
        var players = room.members().stream().map(server.getPlayerList()::getPlayer).toList();
        for (ServerPlayer player : players) {
            if (player == null) throw new IllegalArgumentException("Both players must be online");
            eligible(player);
        }
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
        try {
            PvpArenaTemplates.place(level, template, origin);
            for (String name : List.of("pvp_spawn_a", "pvp_spawn_b")) {
                BlockPos spawn = layout.point(name, origin);
                if (!level.getBlockState(spawn).getCollisionShape(level, spawn).isEmpty()
                        || !level.getBlockState(spawn.above()).getCollisionShape(level, spawn.above()).isEmpty()
                        || !level.getBlockState(spawn.below()).isCollisionShapeFullBlock(level, spawn.below())) {
                    throw new IllegalArgumentException("Spawn requires empty feet/head cells and a solid floor: " + name);
                }
            }
            for (ServerPlayer player : players) recovery.capture(player);
            room.begin();
            for (int i = 0; i < players.size(); i++) {
                BlockPos spawn = layout.point(i == 0 ? "pvp_spawn_a" : "pvp_spawn_b", origin);
                ServerPlayer player = players.get(i);
                player.teleportTo(level, spawn.getX()+0.5, spawn.getY(), spawn.getZ()+0.5, i == 0 ? -90 : 90, 0);
                player.fallDistance = 0;
            }
            message(room, "Fight starts in 3 seconds. No Rating in private rooms.");
        } catch (RuntimeException exception) {
            close(room, "Could not start: " + exception.getMessage());
            NeoWorld.LOGGER.warn("Could not start PvP room {}", room.id(), exception);
            throw new IllegalArgumentException("Could not start arena; room closed safely");
        }
    }
    public void disconnect(ServerPlayer player) {
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
        message(room, reason);
        for (UUID id : room.members()) {
            membership.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.isAlive()) recover(player);
        }
        rooms.remove(room.id());
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
        tickOffers();
        if (ticks % 20 == 0) {
            matchQueue();
            for(UUID id:queue.keySet()) { var player=server.getPlayerList().getPlayer(id); if(player!=null) sendQueueState(player); }
        }
        if (ticks % 100 == 0) retryResults();
        for (var room : List.copyOf(rooms.values())) {
            Run run = runs.get(room.id()); if (run == null) continue;
            if (ranked.containsKey(room.id())) { tickRanked(room, run, ranked.get(room.id())); continue; }
            if(intermissions.containsKey(room.id())) { tickIntermission(room,null); continue; }
            boolean invalid = false;
            for (UUID id : room.members()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null || !player.isAlive() || !player.serverLevel().dimension().equals(PvpDimensions.LEVEL)
                        || !run.layout().contains(run.origin(),player.getX(),player.getY(),player.getZ())) invalid = true;
            }
            if (invalid || ticks - run.started() >= 12000) { close(room, "Room ended: player left the arena or time expired"); continue; }
            if (room.state() == PvpRoom.State.COUNTDOWN && ticks - run.started() >= 60) { room.activate(); message(room, "Fight!"); }
        }
        clean();
    }
    public void queue(ServerPlayer player, String arena) {
        eligible(player);
        checkOffer(player);
        int penalty=PvpQueuePenaltyData.get(server).remaining(player.getUUID(),System.currentTimeMillis());
        if(penalty>0) throw new IllegalArgumentException("Ranked queue cooldown: " + penalty + " seconds");
        if (player.isCreative() || room(player.getUUID()) != null || queue.containsKey(player.getUUID())) throw new IllegalArgumentException("Already queued, in a room, or in Creative mode");
        var level = server.getLevel(PvpDimensions.LEVEL);
        var template = PvpArenaData.get(server).template(arena);
        if (level == null || template == null) throw new IllegalArgumentException("Unknown or unavailable arena");
        PvpArenaTemplates.inspect(level, template);
        try {
            var rating = PvpRatingRepository.player(player.getUUID(), player.getGameProfile().getName());
            queue.put(player.getUUID(), new QueueEntry(arena, rating.rating(), ticks));
            sendQueueState(player);
            player.sendSystemMessage(Component.literal("[PvP] Ranked queue joined. Rating " + rating.rating()));
        } catch (java.sql.SQLException exception) { throw new IllegalArgumentException("Rating database unavailable; queue not joined"); }
    }
    public void unqueue(ServerPlayer player) {
        if (!removeQueue(player.getUUID())) throw new IllegalArgumentException("Not in ranked queue");
        player.sendSystemMessage(Component.literal("[PvP] Left ranked queue"));
    }
    private static String pair(UUID a, UUID b) { return a.compareTo(b) < 0 ? a+":"+b : b+":"+a; }
    private void matchQueue() {
        recentPairs.entrySet().removeIf(entry -> ticks-entry.getValue() >= 12000);
        for (UUID id : List.copyOf(queue.keySet())) {
            var player = server.getPlayerList().getPlayer(id);
            try { if (player == null || player.isCreative()) throw new IllegalArgumentException(); eligible(player); }
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
                if (first.arena().equals(second.arena()) && gap<=allowed && gap<distance && !recentPairs.containsKey(pair(a,b))) { best=b; distance=gap; }
            }
            if (best==null) continue;
            var owner=server.getPlayerList().getPlayer(a); var opponent=server.getPlayerList().getPlayer(best);
            QueueEntry second=queue.get(best);
            removeQueue(a); removeQueue(best);
            try {
                var confirmation=new MatchConfirmation(a,best,ticks);
                var offer=new Offer(confirmation,first,second,preview(owner,first.rating()),preview(opponent,second.rating()));
                offers.put(confirmation.id,offer); sendOffer(offer); loadOfferPositions(offer);
            } catch (RuntimeException exception) {
                if (owner!=null) owner.sendSystemMessage(Component.literal("[PvP] Matchmaking could not start the arena; rejoin the queue"));
                if (opponent!=null) opponent.sendSystemMessage(Component.literal("[PvP] Matchmaking could not start the arena; rejoin the queue"));
                NeoWorld.LOGGER.warn("Ranked arena start failed",exception);
            }
        }
    }
    private PvpMatchOfferPayload.Player preview(ServerPlayer player,int rating) {
        var equipment=java.util.Arrays.stream(new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.HEAD})
                .map(slot -> player.getItemBySlot(slot).copy()).toList();
        return new PvpMatchOfferPayload.Player(player.getUUID(),player.getGameProfile().getName(),rating,-1,equipment);
    }
    private void loadOfferPositions(Offer offer) {
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return new int[]{PvpRatingRepository.position(offer.a().id()),PvpRatingRepository.position(offer.b().id())}; }
            catch(java.sql.SQLException | IllegalStateException exception) { return new int[]{-1,-1}; }
        }).thenAccept(positions -> server.execute(() -> {
            if(!offers.containsKey(offer.confirmation().id)) return;
            var a=offer.a(); var b=offer.b();
            var updated=new Offer(offer.confirmation(),offer.first(),offer.second(),
                    new PvpMatchOfferPayload.Player(a.id(),a.name(),a.rating(),positions[0],a.equipment()),
                    new PvpMatchOfferPayload.Player(b.id(),b.name(),b.rating(),positions[1],b.equipment()));
            offers.put(offer.confirmation().id,updated); sendOffer(updated);
        }));
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
        offer.confirmation().accept(player.getUUID(),ticks); sendOffer(offer);
    }
    private void requeue(UUID id,QueueEntry entry) {
        var player=server.getPlayerList().getPlayer(id); if(player==null) return;
        try {
            eligible(player); if(player.isCreative() || room(id)!=null) return;
            if(PvpQueuePenaltyData.get(server).remaining(id,System.currentTimeMillis())>0) return;
            queue.put(id,entry); sendQueueState(player);
            player.sendSystemMessage(Component.literal("[PvP] Returning to ranked queue with original waiting time"));
        } catch(IllegalArgumentException ignored) { }
    }
    private void failOffer(Offer offer,UUID disconnected) {
        removeOffer(offer); var c=offer.confirmation();
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
            if(ticks%20==0) sendOffer(offer);
        }
    }
    private void startConfirmed(Offer offer,ServerPlayer owner,ServerPlayer opponent) {
        removeOffer(offer); PvpRoom room=null;
        try {
            room=rooms.get(create(owner,offer.first().arena())); join(opponent,room.id());
            room.setReady(owner.getUUID(),true); room.setReady(opponent.getUUID(),true);
            var match=new RankedMatch(owner.getUUID(),opponent.getUUID(),owner.getGameProfile().getName(),opponent.getGameProfile().getName(),ticks);
            ranked.put(room.id(),match); start(owner); resetRound(room,match);
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
        room.pause(); match.countdown=ticks+60; match.roundStarted=ticks+60;
        for (int i=0;i<room.members().size();i++) {
            var player=server.getPlayerList().getPlayer(room.members().get(i)); if (player==null) continue;
            restoreMode(player);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
            var spawn=run.layout().point(i==0 ? "pvp_spawn_a" : "pvp_spawn_b",run.origin());
            player.teleportTo(level,spawn.getX()+0.5,spawn.getY(),spawn.getZ()+0.5,i==0 ? -90 : 90,0);
            player.setHealth(player.getMaxHealth()); player.removeAllEffects(); player.setAbsorptionAmount(0);
            player.clearFire(); player.fallDistance=0;
        }
        var box=new AABB(run.origin().getX(),run.origin().getY(),run.origin().getZ(),
                run.origin().getX()+run.layout().template().getSize().getX(),run.origin().getY()+run.layout().template().getSize().getY(),run.origin().getZ()+run.layout().template().getSize().getZ());
        level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,box).forEach(net.minecraft.world.entity.Entity::discard);
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
        for (var player : List.of(a,b)) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator() || !player.serverLevel().dimension().equals(PvpDimensions.LEVEL)
                    || !run.layout().contains(run.origin(),player.getX(),player.getY(),player.getZ())) {
                forfeit(room,player.getUUID()); return;
            }
        }
        if (room.state()==PvpRoom.State.COUNTDOWN && ticks>=match.countdown) {
            room.activate(); match.began=true; message(room,"Ranked round: Fight!");
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
    private void announceIntermission(PvpRoom room,RankedMatch match) {
        var pause=intermissions.get(room.id());
        for(UUID id:room.members()) {
            var player=server.getPlayerList().getPlayer(id); if(player==null) continue;
            String title;
            net.minecraft.ChatFormatting color;
            if(pause.defeated()==null) { title=pause.terminal() ? "เสมอ" : "รอบเสมอ"; color=net.minecraft.ChatFormatting.GOLD; }
            else if(id.equals(pause.defeated())) { title=pause.terminal() ? "คุณแพ้การแข่งขัน" : "คุณแพ้รอบนี้"; color=net.minecraft.ChatFormatting.RED; }
            else { title=pause.terminal() ? "ยินดีด้วย! คุณชนะ" : "ชนะรอบนี้"; color=net.minecraft.ChatFormatting.AQUA; }
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0,110,10));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(resultSubtitle(room,match,id,pause))));
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(title).withStyle(color)));
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
            if(player!=null) player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(resultSubtitle(room,match,id,pause))));
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
        for (int budget = 0; budget < 2048 && job.index < total; budget++, job.index++) {
            int x = job.index % region.x(), z = (job.index / region.x()) % region.z(), y = job.index / (region.x()*region.z());
            level.setBlock(region.origin().offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        if (job.index == total) {
            level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
                    entity -> !(entity instanceof ServerPlayer)).forEach(net.minecraft.world.entity.Entity::discard);
            PvpRecoveryData.get(server).release(job.slot); cleanup.remove();
        }
    }
}
