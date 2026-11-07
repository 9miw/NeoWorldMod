package com.ambition.neoworld.dungeon.room;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

import com.ambition.neoworld.dungeon.instance.DungeonInstance;
import com.ambition.neoworld.dungeon.instance.DungeonInstanceState;
import com.ambition.neoworld.dungeon.recovery.DungeonReturnPoint;
import com.ambition.neoworld.dungeon.recovery.DungeonRecoveryData;
import com.ambition.neoworld.dungeon.instance.DungeonScoreboardManager;
import com.ambition.neoworld.network.dungeon.DungeonHudPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import com.ambition.neoworld.dungeon.instance.DungeonRuntime;
import com.ambition.neoworld.dungeon.definition.DungeonDefinition;
import com.ambition.neoworld.dungeon.definition.DungeonDefinitionData;
import com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus;
import com.ambition.neoworld.dungeon.definition.DungeonMonsterWave;
import com.ambition.neoworld.dungeon.structure.DungeonPlacementResult;
import com.ambition.neoworld.dungeon.structure.DungeonStructurePlacer;
import com.ambition.neoworld.dungeon.structure.DungeonTemplateLayouts;
import com.ambition.neoworld.dungeon.structure.DungeonRemovalManager;
import com.ambition.neoworld.party.Party;
import com.ambition.neoworld.party.PartyMember;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.registry.ModDimensionRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.nbt.Tag;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;

/** Owns temporary dungeon lobbies for one running server. */
public final class DungeonRoomManager {
    static final String SPECTATOR_RETURN_MODE = "neoworld_dungeon_spectator_return_mode";
    static final String DUNGEON_DEATH = "neoworld_dungeon_death";
    static final String DEATH_EXIT = "neoworld_dungeon_death_exit";
    private static final Map<MinecraftServer, DungeonRoomManager> MANAGERS = new WeakHashMap<>();

    private final Map<Integer, DungeonRoom> rooms = new LinkedHashMap<>();
    private final Map<UUID, Integer> roomByPlayer = new LinkedHashMap<>();
    private final Map<UUID, ActiveRun> activeRuns = new LinkedHashMap<>();
    private record SoundKey(int roomId, DungeonSoundEffects.Cue cue) {}
    private record PendingSound(DungeonRoom room, UUID instanceId, UUID playerId, ResourceLocation dimension, long tick, DungeonSoundEffects.Step step) {}
    private final Map<SoundKey, Long> soundCooldowns = new java.util.HashMap<>();
    private final java.util.List<PendingSound> pendingSounds = new java.util.ArrayList<>();
    private final Map<Integer, PendingStart> pendingStarts = new LinkedHashMap<>();
    private final MinecraftServer server;
    private int nextRoomId = 1;

    private DungeonRoomManager(MinecraftServer server) {
        this.server = server;
    }

    public static synchronized DungeonRoomManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, DungeonRoomManager::new);
    }

    public RoomActionResult create(ServerPlayer owner, String dungeonId, DungeonRoomType type) {
        if (roomByPlayer.containsKey(owner.getUUID())) {
            return RoomActionResult.failure("คุณอยู่ในห้องดันเจี้ยนแล้ว");
        }
        if (DungeonRuntime.get(server).getForPlayer(owner.getUUID()).isPresent()) {
            return RoomActionResult.failure("คุณอยู่ใน Dungeon Instance แล้ว");
        }
        int roomId = allocateRoomId();
        DungeonRoom room = new DungeonRoom(roomId, dungeonId, type, memberOf(owner));
        rooms.put(roomId, room);
        roomByPlayer.put(owner.getUUID(), roomId);
        sound(room, DungeonSoundEffects.Cue.CREATED);
        return RoomActionResult.success(room, "สร้างห้องสำเร็จ");
    }

    public RoomActionResult createFromParty(ServerPlayer leader, String dungeonId, DungeonRoomType type, Party party) {
        if (party == null || !party.leaderId().equals(leader.getUUID())) {
            return RoomActionResult.failure("เฉพาะหัวหน้าปาร์ตี้เท่านั้นที่สร้างห้องจากปาร์ตี้ได้");
        }
        if (type == DungeonRoomType.SOLO) {
            return RoomActionResult.failure("โหมด solo ไม่ต้องใช้ปาร์ตี้");
        }
        if (party.capacity() != type.capacity()) {
            return RoomActionResult.failure("ขนาดปาร์ตี้ไม่ตรงกับโหมดดันเจี้ยน");
        }
        if (party.memberCount() != type.capacity()) {
            return RoomActionResult.failure(String.format(
                    "ต้องมีสมาชิกครบ %d คนก่อนสร้างห้อง (ปัจจุบัน %d คน)",
                    type.capacity(), party.memberCount()));
        }
        for (PartyMember member : party.members()) {
            if (roomByPlayer.containsKey(member.playerId())) {
                return RoomActionResult.failure("สมาชิก " + member.playerName() + " อยู่ในห้องดันเจี้ยนแล้ว");
            }
            if (DungeonRuntime.get(server).getForPlayer(member.playerId()).isPresent()) {
                return RoomActionResult.failure("สมาชิก " + member.playerName() + " อยู่ใน Dungeon Instance แล้ว");
            }
            if (server.getPlayerList().getPlayer(member.playerId()) == null) {
                return RoomActionResult.failure("สมาชิก " + member.playerName() + " ไม่ได้ออนไลน์");
            }
        }
        int roomId = allocateRoomId();
        java.util.List<DungeonRoomMember> members = party.members().stream()
                .map(member -> new DungeonRoomMember(member.playerId(), member.playerName()))
                .toList();
        DungeonRoom room = new DungeonRoom(roomId, dungeonId, type, party.id(), members, party.leaderId());
        rooms.put(roomId, room);
        members.forEach(member -> roomByPlayer.put(member.playerId(), roomId));
        sound(room, DungeonSoundEffects.Cue.CREATED);
        return RoomActionResult.success(room, "สร้างห้องจากปาร์ตี้สำเร็จ");
    }

    public RoomActionResult join(ServerPlayer player, int roomId) {
        if (roomByPlayer.containsKey(player.getUUID())) {
            return RoomActionResult.failure("คุณอยู่ในห้องดันเจี้ยนแล้ว");
        }
        DungeonRoom room = rooms.get(roomId);
        if (room == null) {
            return RoomActionResult.failure("ไม่พบห้องหมายเลข " + roomId);
        }
        if (room.state() != DungeonRoomState.WAITING) {
            return RoomActionResult.failure("ห้องนี้เริ่มดันเจี้ยนแล้ว");
        }
        if (room.isFull()) {
            return RoomActionResult.failure("ห้องนี้เต็มแล้ว");
        }

        room.addMember(memberOf(player));
        roomByPlayer.put(player.getUUID(), roomId);
        sound(room, DungeonSoundEffects.Cue.MEMBER_JOIN);
        return RoomActionResult.success(room, "เข้าร่วมห้องสำเร็จ");
    }

    public RoomActionResult leave(UUID playerId) {
        DungeonRoom room = findForPlayer(playerId).orElse(null);
        if (room == null) {
            return RoomActionResult.failure("คุณไม่ได้อยู่ในห้องดันเจี้ยน");
        }
        if (room.state() != DungeonRoomState.WAITING) {
            return RoomActionResult.failure("ไม่สามารถออกจากห้องที่กำลังเริ่มดันเจี้ยน");
        }

        room.removeMember(playerId);
        roomByPlayer.remove(playerId);
        if (room.isEmpty()) {
            room.setState(DungeonRoomState.CLOSED);
            rooms.remove(room.id());
        }
        return RoomActionResult.success(room, "ออกจากห้องสำเร็จ");
    }

    public RoomActionResult toggleReady(UUID playerId) {
        DungeonRoom room = findForPlayer(playerId).orElse(null);
        if (room == null || room.state() != DungeonRoomState.WAITING) {
            return RoomActionResult.failure("เปลี่ยนสถานะพร้อมได้ในห้องรอดันเจี้ยนเท่านั้น");
        }
        if (room.ownerId().equals(playerId)) {
            return RoomActionResult.success(room, "เจ้าของห้องพร้อมอยู่เสมอ");
        }
        room.setReady(playerId, !room.isReady(playerId));
        boolean allReady = room.isFull() && room.allReady()
                && room.members().stream().allMatch(member -> server.getPlayerList().getPlayer(member.playerId()) != null);
        sound(room, allReady ? DungeonSoundEffects.Cue.ALL_READY
                : room.isReady(playerId) ? DungeonSoundEffects.Cue.READY : DungeonSoundEffects.Cue.UNREADY);
        return RoomActionResult.success(room, room.isReady(playerId) ? "พร้อมแล้ว" : "ยกเลิกพร้อมแล้ว");
    }

    public void clearPlayerReady(UUID playerId) {
        findForPlayer(playerId).ifPresent(room -> room.setReady(playerId, false));
    }

    public RoomActionResult start(ServerPlayer owner) {
        DungeonRoom room = findForPlayer(owner.getUUID()).orElse(null);
        if (room == null) return RoomActionResult.failure("คุณไม่ได้อยู่ในห้องดันเจี้ยน");
        if (!room.ownerId().equals(owner.getUUID())) return RoomActionResult.failure("เฉพาะเจ้าของห้องเท่านั้นที่เริ่มได้");
        if (room.state() != DungeonRoomState.WAITING) return RoomActionResult.failure("ห้องนี้ไม่ได้อยู่ในสถานะรอ");
        var members = room.members().stream().map(DungeonRoomMember::playerId).toList();
        var definition = DungeonDefinitionData.get(server).get(room.dungeonId()).orElse(null);
        String title = definition == null ? room.dungeonId() : definition.displayName();
        room.setState(DungeonRoomState.STARTING);
        pendingStarts.put(room.id(), new PendingStart(room, members, server.getTickCount() + 5));
        for (UUID member : members) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) PacketDistributor.sendToPlayer(player,
                    new com.ambition.neoworld.network.dungeon.DungeonLoadingPayload(true, room.id(), title));
        }
        return RoomActionResult.success(room, "กำลังเตรียมดันเจี้ยน");
    }

    private record PendingStart(DungeonRoom room, java.util.List<UUID> members, int tick) {}

    private void tickPendingStarts() {
        var iterator = pendingStarts.values().iterator();
        while (iterator.hasNext()) {
            PendingStart pending = iterator.next();
            if (server.getTickCount() < pending.tick()) continue;
            iterator.remove();
            DungeonRoom room = pending.room();
            RoomActionResult result = RoomActionResult.failure("การเริ่มดันเจี้ยนถูกยกเลิก");
            try {
                ServerPlayer owner = server.getPlayerList().getPlayer(room.ownerId());
                if (rooms.get(room.id()) == room && room.state() == DungeonRoomState.STARTING && owner != null) {
                    // Allow clients to render the loader before synchronous structure placement.
                    room.setState(DungeonRoomState.WAITING);
                    result = startNow(owner);
                }
            } catch (RuntimeException exception) {
                com.ambition.neoworld.NeoWorld.LOGGER.error("Failed to start dungeon room {}", room.id(), exception);
                result = RoomActionResult.failure("เกิดข้อผิดพลาดระหว่างเตรียมดันเจี้ยน กรุณาตรวจสอบ server log");
            } finally {
                if (!result.successful() && room.state() == DungeonRoomState.STARTING) {
                    room.setState(DungeonRoomState.WAITING);
                }
                for (UUID member : pending.members()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(member);
                    if (player == null) continue;
                    PacketDistributor.sendToPlayer(player,
                            new com.ambition.neoworld.network.dungeon.DungeonLoadingPayload(false, room.id(), ""));
                    if (!result.successful()) {
                        player.sendSystemMessage(Component.literal(result.message()));
                        com.ambition.neoworld.network.dungeon.DungeonRoomNetworkHandler.send(player, true);
                    }
                }
            }
        }
    }

    private RoomActionResult startNow(ServerPlayer owner) {
        UUID playerId = owner.getUUID();
        DungeonRoom room = findForPlayer(playerId).orElse(null);
        if (room == null) {
            return RoomActionResult.failure("คุณไม่ได้อยู่ในห้องดันเจี้ยน");
        }
        if (!room.ownerId().equals(playerId)) {
            return RoomActionResult.failure("เฉพาะเจ้าของห้องเท่านั้นที่เริ่มได้");
        }
        if (room.state() != DungeonRoomState.WAITING) {
            return RoomActionResult.failure("ห้องนี้ไม่ได้อยู่ในสถานะรอ");
        }
        if (room.memberCount() != room.type().capacity()) {
            return RoomActionResult.failure(String.format(
                    "ต้องมีสมาชิกครบ %d คนก่อนเริ่ม (ปัจจุบัน %d คน)",
                    room.type().capacity(),
                    room.memberCount()
            ));
        }
        Party party = room.partyId()
                .flatMap(id -> com.ambition.neoworld.party.PartyManager.get(server).find(id))
                .orElse(null);
        if (room.type() != DungeonRoomType.SOLO) {
            if (party == null) {
                room.setState(DungeonRoomState.WAITING);
                return RoomActionResult.failure("ไม่พบปาร์ตี้ของห้องนี้แล้ว");
            }
            if (!room.allReady()) {
                return RoomActionResult.failure("สมาชิกห้องดันเจี้ยนต้องกดพร้อมทุกคนก่อนเริ่ม");
            }
        }

        room.setState(DungeonRoomState.STARTING);
        DungeonDefinition definition = DungeonDefinitionData.get(server).get(room.dungeonId()).orElse(null);
        if (definition == null || definition.status() != DungeonDefinitionStatus.ENABLED) {
            room.setState(DungeonRoomState.WAITING);
            return RoomActionResult.failure("ดันเจี้ยนนี้ไม่ได้เปิดใช้งานแล้ว");
        }
        ServerLevel dungeonLevel = server.getLevel(ModDimensionRegistries.DUNGEON_LEVEL);
        if (dungeonLevel == null) {
            room.setState(DungeonRoomState.WAITING);
            return RoomActionResult.failure("ไม่พบมิติ neoworld:dungeon");
        }

        java.util.List<ServerPlayer> players = new java.util.ArrayList<>();
        Map<UUID, DungeonReturnPoint> returnPoints = new LinkedHashMap<>();
        for (DungeonRoomMember member : room.members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player == null) {
                room.setState(DungeonRoomState.WAITING);
                return RoomActionResult.failure("สมาชิก " + member.playerName() + " ไม่ได้ออนไลน์");
            }
            if (player.getData(ModAttachments.PLAYER_DATA).getLevel() < definition.minimumLevel()) {
                room.setState(DungeonRoomState.WAITING);
                return RoomActionResult.failure(String.format(
                        "สมาชิก %s ต้องมีเลเวลอย่างน้อย %d",
                        member.playerName(), definition.minimumLevel()));
            }
            players.add(player);
            returnPoints.put(player.getUUID(), new DungeonReturnPoint(
                    player.serverLevel().dimension().location().toString(),
                    player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
            ));
        }
        DungeonInstance instance;
        try {
            instance = DungeonRuntime.get(server).create(room.dungeonId(), returnPoints, server.getTickCount());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            room.setState(DungeonRoomState.WAITING);
            return RoomActionResult.failure(exception.getMessage());
        }

        BlockPos origin = new BlockPos(
                DungeonRuntime.get(server).slotOriginX(instance.slotIndex()), 64, 0);
        DungeonPlacementResult placement = DungeonStructurePlacer.place(
                dungeonLevel, DungeonTemplateLayouts.from(definition), origin);
        if (!placement.successful()) {
            closeFailedInstance(instance);
            room.setState(DungeonRoomState.WAITING);
            return RoomActionResult.failure("วาง Structure ไม่สำเร็จ: " + placement.error());
        }

        java.util.List<BlockPos> spawnPoints = placement.markers().getOrDefault("player_spawn", java.util.List.of());
        if (spawnPoints.size() < players.size()) {
            closeFailedInstance(instance);
            room.setState(DungeonRoomState.WAITING);
            return RoomActionResult.failure("จำนวน player_spawn ไม่เพียงพอ");
        }

        returnPoints.forEach((memberId, point) -> DungeonRecoveryData.get(server).put(memberId, point));
        for (int i = 0; i < players.size(); i++) {
            BlockPos spawn = spawnPoints.get(i);
            ServerPlayer player = players.get(i);
            // Reuse the death-cloned recovery key so spectating preserves the pre-entry mode.
            if (!player.getPersistentData().contains(SPECTATOR_RETURN_MODE, Tag.TAG_INT)) {
                player.getPersistentData().putInt(SPECTATOR_RETURN_MODE,
                        player.gameMode.getGameModeForPlayer().getId());
            }
            player.setGameMode(GameType.ADVENTURE);
            players.get(i).teleportTo(
                    dungeonLevel,
                    spawn.getX() + 0.5D,
                    spawn.getY() + 0.1D,
                    spawn.getZ() + 0.5D,
                    players.get(i).getYRot(),
                    players.get(i).getXRot()
            );
        }
        DungeonRuntime.get(server).transition(instance.id(), DungeonInstanceState.ACTIVE, server.getTickCount());
        ActiveRun run = new ActiveRun(instance, room, definition, dungeonLevel, placement, server.getTickCount());
        run.phase = definition.monsterWaves().isEmpty() ? RunPhase.WAITING_BOSS : RunPhase.WAITING_WAVE;
        run.nextActionTick = server.getTickCount() + 60L;
        activeRuns.put(instance.id(), run);
        syncRunHud(run);
        notifyTitle(run, DungeonTitles.start(definition.displayName()));
        room.setState(DungeonRoomState.IN_DUNGEON);
        sound(run, DungeonSoundEffects.Cue.START);
        room.clearReady();
        return RoomActionResult.success(room,
                "เริ่มดันเจี้ยนสำเร็จ | slot=" + instance.slotIndex());
    }

    private static String spawnMonsterWave(
            DungeonDefinition definition,
            int waveIndex,
            ServerLevel dungeonLevel,
            DungeonPlacementResult placement,
            java.util.List<IEntity<?>> spawned
    ) {
        java.util.List<BlockPos> spawnPoints = placement.markers()
                .getOrDefault("mob_spawn_" + (waveIndex + 1), java.util.List.of());
        if (spawnPoints.isEmpty()) {
            return "ไม่พบ DATA marker mob_spawn_" + (waveIndex + 1);
        }
        if (!NpcAPI.IsAvailable()) {
            return "CustomNPCs API ยังไม่พร้อม";
        }

        DungeonMonsterWave wave = definition.monsterWaves().get(waveIndex);
        IWorld world = NpcAPI.Instance().getIWorld(dungeonLevel);
        try {
            int total = wave.totalCount(spawnPoints.size());
            int[] used = new int[spawnPoints.size()];
            for (int i = 0; i < total; i++) {
                int markerIndex = wave.markerIndex(i, spawnPoints.size(), dungeonLevel.random);
                BlockPos marker = spawnPoints.get(markerIndex);
                int repeat = used[markerIndex]++;
                double offsetX = repeat == 0 ? 0.0D : ((repeat - 1) % 3 - 1) * 1.25D;
                double offsetZ = repeat == 0 ? 0.0D : (((repeat - 1) / 3) % 3 - 1) * 1.25D;
                IEntity<?> entity = world.spawnClone(
                        marker.getX() + 0.5D + offsetX,
                        marker.getY(),
                        marker.getZ() + 0.5D + offsetZ,
                        wave.cloneTab(),
                        wave.cloneName()
                );
                if (entity == null) {
                    throw new IllegalStateException("CustomNPCs returned no entity");
                }
                spawned.add(entity);
            }
        } catch (RuntimeException exception) {
            spawned.forEach(entity -> {
                if (entity != null && entity.isAlive()) entity.despawn();
            });
            return String.format(
                    "สร้าง Monster Wave %d ไม่สำเร็จ: tab%d/%s (%s)",
                    waveIndex + 1, wave.cloneTab(), wave.cloneName(), exception.getMessage());
        }
        return null;
    }

    public void tick() {
        tickSounds();
        tickPendingStarts();
        if (activeRuns.isEmpty()) return;
        long gameTick = server.getTickCount();
        java.util.Iterator<ActiveRun> iterator = activeRuns.values().iterator();
        while (iterator.hasNext()) {
            ActiveRun run = iterator.next();
            if (run.soloDeathPending) {
                ServerPlayer player = server.getPlayerList().getPlayer(run.room.ownerId());
                if (player == null || run.returned.contains(player.getUUID())) {
                    iterator.remove();
                    finishRun(run, false, "§c[Dungeon] จบดันเจี้ยน");
                } else if (!player.isAlive()) {
                    // Run after the death event and inventory preservation have completed.
                    player.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                            net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                } else {
                    if (run.soloDeathExitTick < 0) {
                        spectateRun(run, player);
                        run.soloDeathExitTick = gameTick + 200L;
                    }
                    int seconds = soloDeathSecondsRemaining(run.soloDeathExitTick, gameTick);
                    if (seconds == 0) {
                        iterator.remove();
                        finishRun(run, false, "§c[Dungeon] คุณเสียชีวิตแล้ว");
                    } else if (seconds != run.soloDeathLastSecond) {
                        run.soloDeathLastSecond = seconds;
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, 30, 0));
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                                Component.literal("ออกจากดันใน " + seconds + " วินาที")));
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                                Component.literal("คุณเสียชีวิตแล้ว").withStyle(net.minecraft.ChatFormatting.RED)));
                    }
                }
                continue;
            }
            if (run.definition.timeLimitSeconds() > 0
                    && gameTick - run.instance.createdAtTick() >= run.definition.timeLimitSeconds() * 20L) {
                iterator.remove();
                finishRun(run, false, null, DungeonTitles.timeout());
                continue;
            }
            if (onlinePlayers(run).isEmpty()) {
                iterator.remove();
                finishRun(run, false, "§c[Dungeon] ไม่มีสมาชิกออนไลน์เหลืออยู่");
                continue;
            }
            if (run.phase != RunPhase.REWARD
                    && run.eliminated.containsAll(run.instance.members().keySet())) {
                iterator.remove();
                finishRun(run, false, null, DungeonTitles.defeat());
                continue;
            }

            switch (run.phase) {
                case FIGHTING_WAVE -> {
                    if (run.enemies.stream().noneMatch(IEntity::isAlive)) {
                        run.enemies.clear();
                        run.waveIndex++;
                        if (run.waveIndex < run.definition.monsterWaves().size()) {
                            run.phase = RunPhase.WAITING_WAVE;
                            run.nextActionTick = gameTick + 60L;
                        } else {
                            run.phase = RunPhase.WAITING_BOSS;
                            run.nextActionTick = gameTick + 60L;
                        }
                        notifyTitle(run, DungeonTitles.cleared(run.phase == RunPhase.WAITING_WAVE));
                    }
                }
                case WAITING_WAVE -> {
                    if (gameTick >= run.nextActionTick) {
                        String error = spawnMonsterWave(
                                run.definition, run.waveIndex, run.level, run.placement, run.enemies);
                        if (error != null) {
                            iterator.remove();
                            finishRun(run, false, "§c[Dungeon] " + error);
                        } else {
                            run.phase = RunPhase.FIGHTING_WAVE;
                            sound(run, DungeonSoundEffects.Cue.WAVE);
                            notifyTitle(run, DungeonTitles.wave(run.waveIndex + 1));
                        }
                    }
                }
                case WAITING_BOSS -> {
                    if (gameTick >= run.nextActionTick) {
                        String error = spawnBoss(run);
                        if (error != null) {
                            iterator.remove();
                            finishRun(run, false, "§c[Dungeon] " + error);
                        } else {
                            DungeonRuntime.get(server).transition(
                                    run.instance.id(), DungeonInstanceState.BOSS, gameTick);
                            run.phase = RunPhase.FIGHTING_BOSS;
                            sound(run, DungeonSoundEffects.Cue.BOSS);
                            notifyTitle(run, DungeonTitles.boss(run.definition.boss().cloneName()));
                        }
                    }
                }
                case FIGHTING_BOSS -> {
                    if (run.enemies.stream().noneMatch(IEntity::isAlive)) {
                        if (!run.completionRecorded) {
                            var clear = com.ambition.neoworld.dungeon.stats.DungeonClear.completed(
                                    run.instance.id(), run.instance.dungeonId(), run.room.type(),
                                    run.startedTick, gameTick, System.currentTimeMillis(), run.memberIds(), run.returned);
                            com.ambition.neoworld.dungeon.stats.DungeonResults.completed(server, clear);
                            run.completionRecorded = true;
                        }
                        DungeonRuntime.get(server).transition(
                                run.instance.id(), DungeonInstanceState.REWARD, gameTick);
                        grantRewards(run);
                        run.phase = RunPhase.REWARD;
                        if (!run.resultSoundPlayed) {
                            run.resultSoundPlayed = true;
                            sound(run, DungeonSoundEffects.Cue.VICTORY);
                        }
                        notifyTitle(run, DungeonTitles.victory());
                        returnPlayersAtExit(run);
                    }
                }
                case REWARD -> {
                    returnPlayersAtExit(run);
                    if (run.returned.size() >= run.instance.members().size()) {
                        iterator.remove();
                        cleanupRun(run);
                    }
                }
            }
            if (gameTick % 20L == 0L && activeRuns.containsKey(run.instance.id())) {
                syncRunHud(run);
            }
        }
    }

    private static String spawnBoss(ActiveRun run) {
        if (!NpcAPI.IsAvailable()) return "CustomNPCs API ยังไม่พร้อม";
        try {
            BlockPos position = run.placement.markers().get("boss_spawn").getFirst();
            IEntity<?> boss = NpcAPI.Instance().getIWorld(run.level).spawnClone(
                    position.getX() + 0.5D,
                    position.getY(),
                    position.getZ() + 0.5D,
                    run.definition.boss().cloneTab(),
                    run.definition.boss().cloneName()
            );
            if (boss == null) return "สร้าง Boss ไม่สำเร็จ: CustomNPCs returned no entity";
            run.enemies.clear();
            run.enemies.add(boss);
            return null;
        } catch (RuntimeException exception) {
            return "สร้าง Boss ไม่สำเร็จ: " + exception.getMessage();
        }
    }

    private void grantRewards(ActiveRun run) {
        BlockPos chestPos = run.placement.markers().get("reward_chest").getFirst();
        run.level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        if (run.level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            // The physical block is only an access point; hoppers must never see rewards.
            chest.setLootTable(null);
            chest.clearContent();
        }
    }

    public boolean isRewardChest(ServerLevel level, BlockPos position) {
        return rewardRunAt(level, position) != null;
    }

    private ActiveRun rewardRunAt(ServerLevel level, BlockPos position) {
        for (ActiveRun run : activeRuns.values()) {
            if (run.level == level && run.placement.markers()
                    .getOrDefault("reward_chest", java.util.List.of()).contains(position)) return run;
        }
        return null;
    }

    /** Returns true when this block belongs to a run, including denied access. */
    public boolean openRewardChest(ServerPlayer player, BlockPos position) {
        ActiveRun run = rewardRunAt(player.serverLevel(), position);
        if (run == null) return false;
        if (!canClaimReward(run, player, position)) {
            player.sendSystemMessage(Component.literal("§c[Dungeon] คุณยังไม่มีสิทธิ์รับรางวัลจากกล่องนี้"));
            return true;
        }
        java.util.List<net.minecraft.world.item.ItemStack> preset;
        try {
            preset = run.definition.reward().presetId() == null ? java.util.List.of()
                    : DungeonDefinitionData.get(server).lootPresetItems(run.definition.reward().presetId(), server.registryAccess());
        } catch (IllegalArgumentException exception) {
            player.sendSystemMessage(Component.literal("§c[Dungeon] ชุดรางวัลใช้ไม่ได้ กรุณาแจ้งแอดมิน"));
            return true;
        }
        SimpleContainer inventory = run.personalRewards.computeIfAbsent(player.getUUID(), ignored -> {
            UUID ownerId = player.getUUID();
            SimpleContainer contents = new SimpleContainer(preset.size() > 27 ? 54 : 27) {
                @Override
                public boolean stillValid(Player viewer) {
                    return viewer instanceof ServerPlayer current && ownerId.equals(current.getUUID())
                            && canClaimReward(run, current, position);
                }
            };
            if (run.definition.reward().presetId() != null) {
                for (int slot = 0; slot < preset.size(); slot++) {
                    var stack = preset.get(slot);
                    if (stack.isEmpty()) continue;
                    int count = DungeonDefinitionData.get(server).lootRule(run.definition.reward().presetId(), slot)
                            .roll(run.level.getRandom(), stack.getCount());
                    if (count > 0) contents.setItem(slot, stack.copyWithCount(count));
                }
            } else {
                LootTable table = server.reloadableRegistries().getLootTable(ResourceKey.create(
                        Registries.LOOT_TABLE, run.definition.reward().lootTableId()));
                LootParams params = new LootParams.Builder(run.level)
                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(position))
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                        .withLuck(player.getLuck()).create(LootContextParamSets.CHEST);
                table.fill(contents, params, run.level.getRandom().nextLong());
            }
            for (int slot = 0; slot < contents.getContainerSize(); slot++)
                com.ambition.neoworld.economy.analytics.EconomyAnalytics.coinIssued(player, contents.getItem(slot), "dungeon_reward_generated", run.instance.id().toString());
            return contents;
        });
        player.openMenu(new SimpleMenuProvider(
                (id, playerInventory, viewer) -> inventory.getContainerSize() > 27
                        ? ChestMenu.sixRows(id, playerInventory, inventory) : ChestMenu.threeRows(id, playerInventory, inventory),
                Component.literal("รางวัลส่วนตัว - " + run.definition.displayName())));
        return true;
    }

    private boolean canClaimReward(ActiveRun run, ServerPlayer player, BlockPos position) {
        return activeRuns.get(run.instance.id()) == run && run.phase == RunPhase.REWARD
                && run.instance.members().containsKey(player.getUUID())
                && !run.eliminated.contains(player.getUUID()) && !run.returned.contains(player.getUUID())
                && player.isAlive() && !player.isSpectator() && player.serverLevel() == run.level
                && run.level.getBlockEntity(position) instanceof ChestBlockEntity
                && player.distanceToSqr(Vec3.atCenterOf(position)) <= 64.0D;
    }

    private void returnPlayersAtExit(ActiveRun run) {
        // Release spectators after victory, including deferred respawn/login recovery.
        for (UUID memberId : run.memberIds()) {
            if (!run.eliminated.contains(memberId) || run.returned.contains(memberId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null && player.isAlive()) {
                returnPlayer(run, player);
            } else {
                DungeonRecoveryData.get(server).put(memberId, run.instance.members().get(memberId));
                run.returned.add(memberId);
            }
        }
        java.util.List<BlockPos> exits = run.placement.markers().get("exit");
        for (ServerPlayer player : onlinePlayers(run)) {
            if (run.returned.contains(player.getUUID())) continue;
            boolean reachedExit = exits.stream().anyMatch(exit -> player.distanceToSqr(
                    exit.getX() + 0.5D,
                    exit.getY() + 0.5D,
                    exit.getZ() + 0.5D
            ) <= 4.0D);
            if (reachedExit) {
                returnPlayer(run, player);
            }
        }
    }

    private void finishRun(ActiveRun run, boolean success, String message) {
        finishRun(run, success, message, null);
    }

    private void finishRun(ActiveRun run, boolean success, String message, DungeonTitles.Notice title) {
        if (!success && !run.resultSoundPlayed) {
            run.resultSoundPlayed = true;
            sound(run, DungeonSoundEffects.Cue.DEFEAT);
        }
        if (message != null) notifyPlayers(run.memberIds(), message);
        var recipients = onlinePlayers(run).stream()
                .filter(player -> !run.returned.contains(player.getUUID())).toList();
        run.enemies.forEach(entity -> {
            if (entity != null && entity.isAlive()) entity.despawn();
        });
        for (UUID memberId : run.memberIds()) {
            if (run.returned.contains(memberId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null && player.isAlive()) {
                returnPlayer(run, player);
            } else {
                DungeonRecoveryData.get(server).put(memberId, run.instance.members().get(memberId));
            }
        }
        // Returning can clear the death countdown, so result titles follow teleportation.
        if (title != null) recipients.forEach(title::send);
        cleanupRun(run);
    }

    private void returnPlayer(ActiveRun run, ServerPlayer player) {
        DungeonReturnPoint point = run.instance.members().get(player.getUUID());
        if (point == null) return;
        player.closeContainer();
        if (run.soloDeathPending) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));
        }
        teleportToReturnPoint(player, point);
        DungeonRecoveryData.get(server).remove(player.getUUID());
        run.returned.add(player.getUUID());
    }

    private void teleportToReturnPoint(ServerPlayer player, DungeonReturnPoint point) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(point.dimensionId());
        ServerLevel destination = dimensionId == null ? null : server.getLevel(
                ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (destination == null) destination = server.overworld();
        player.teleportTo(destination, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
        restoreSpectatorMode(player);
        hideRunHud(player);
    }

    private static void restoreSpectatorMode(ServerPlayer player) {
        var data = player.getPersistentData();
        if (!data.contains(SPECTATOR_RETURN_MODE, Tag.TAG_INT)) return;
        player.setCamera(player);
        player.setGameMode(GameType.byId(data.getInt(SPECTATOR_RETURN_MODE)));
        data.remove(SPECTATOR_RETURN_MODE);
    }

    private void spectateRun(ActiveRun run, ServerPlayer player) {
        var data = player.getPersistentData();
        if (!data.contains(SPECTATOR_RETURN_MODE, Tag.TAG_INT)) {
            data.putInt(SPECTATOR_RETURN_MODE, player.gameMode.getGameModeForPlayer().getId());
        }
        BlockPos spawn = run.placement.markers().get("player_spawn").getFirst();
        player.setGameMode(GameType.SPECTATOR);
        player.setCamera(player);
        player.teleportTo(run.level, spawn.getX() + 0.5D, spawn.getY() + 1.0D,
                spawn.getZ() + 0.5D, player.getYRot(), player.getXRot());
        DungeonTitles.death(run.soloDeathPending).send(player);
        syncRunHud(run);
    }

    private void syncRunHud(ActiveRun run) {
        int enemiesRemaining = (int) run.enemies.stream().filter(IEntity::isAlive).count();
        int bossHealth = 0;
        if (run.phase == RunPhase.FIGHTING_BOSS && !run.enemies.isEmpty()
                && run.enemies.getFirst().getMCEntity() instanceof LivingEntity boss) {
            bossHealth = boss.getMaxHealth() <= 0.0F
                    ? 0
                    : Math.max(0, Math.min(100, Math.round(boss.getHealth() * 100.0F / boss.getMaxHealth())));
        }
        int elapsedSeconds = (int) Math.max(0L, (server.getTickCount() - run.instance.createdAtTick()) / 20L);
        int secondsRemaining = Math.max(0, run.definition.timeLimitSeconds() - elapsedSeconds);
        DungeonHudPayload.Phase phase = switch (run.phase) {
            case WAITING_WAVE -> DungeonHudPayload.Phase.WAITING_WAVE;
            case FIGHTING_WAVE -> DungeonHudPayload.Phase.WAVE;
            case WAITING_BOSS -> DungeonHudPayload.Phase.WAITING_BOSS;
            case FIGHTING_BOSS -> DungeonHudPayload.Phase.BOSS;
            case REWARD -> DungeonHudPayload.Phase.REWARD;
        };
        int currentWave = Math.min(run.waveIndex + 1, run.definition.monsterWaves().size());
        int partyAlive = Math.max(0, run.instance.members().size() - run.eliminated.size());
        String title = run.definition.displayName();
        String bossName = run.definition.boss() == null ? "" : run.definition.boss().cloneName();
        DungeonHudPayload payload = new DungeonHudPayload(true,
                title.substring(0, Math.min(128, title.length())), phase, currentWave,
                run.definition.monsterWaves().size(), enemiesRemaining,
                bossName.substring(0, Math.min(128, bossName.length())), bossHealth, secondsRemaining,
                run.definition.timeLimitSeconds() <= 0, partyAlive, run.instance.members().size());
        for (ServerPlayer player : onlinePlayers(run)) {
            if (!run.returned.contains(player.getUUID())) {
                DungeonScoreboardManager.hide(player);
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static void hideRunHud(ServerPlayer player) {
        DungeonScoreboardManager.hide(player);
        PacketDistributor.sendToPlayer(player, DungeonHudPayload.HIDDEN);
    }

    private java.util.List<ServerPlayer> onlinePlayers(ActiveRun run) {
        return run.memberIds().stream()
                .map(id -> server.getPlayerList().getPlayer(id))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private void cleanupRun(ActiveRun run) {
        if (run.instance.state() != DungeonInstanceState.CLOSING) {
            DungeonRuntime.get(server).transition(
                    run.instance.id(), DungeonInstanceState.CLOSING, server.getTickCount());
        }
        run.room.members().forEach(member -> roomByPlayer.remove(member.playerId()));
        run.room.setState(DungeonRoomState.CLOSED);
        rooms.remove(run.room.id());
        int originX = DungeonRuntime.get(server).slotOriginX(run.instance.slotIndex());
        DungeonRemovalManager.StartResult removal = DungeonRemovalManager.get(server).start(
                run.level,
                DungeonTemplateLayouts.from(run.definition),
                run.instance.slotIndex(),
                new BlockPos(originX, 64, 0),
                ignored -> DungeonRuntime.get(server).transition(
                        run.instance.id(), DungeonInstanceState.CLOSED, server.getTickCount())
        );
        if (!removal.started()) {
            DungeonRuntime.get(server).transition(
                    run.instance.id(), DungeonInstanceState.CLOSED, server.getTickCount());
        }
    }

    private void notifyTitle(ActiveRun run, DungeonTitles.Notice title) {
        for (ServerPlayer player : onlinePlayers(run)) {
            if (!run.returned.contains(player.getUUID())) title.send(player);
        }
    }

    private void notifyPlayers(java.util.List<UUID> memberIds, String message) {
        for (UUID memberId : memberIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.literal(message));
        }
    }

    private void closeFailedInstance(DungeonInstance instance) {
        DungeonRuntime.get(server).transition(instance.id(), DungeonInstanceState.CLOSING, server.getTickCount());
        DungeonRuntime.get(server).transition(instance.id(), DungeonInstanceState.CLOSED, server.getTickCount());
    }

    public RoomActionResult disband(UUID playerId) {
        DungeonRoom room = findForPlayer(playerId).orElse(null);
        if (room == null) {
            return RoomActionResult.failure("คุณไม่ได้อยู่ในห้องดันเจี้ยน");
        }
        if (!room.ownerId().equals(playerId)) {
            return RoomActionResult.failure("เฉพาะเจ้าของห้องเท่านั้นที่ยุบห้องได้");
        }
        if (room.state() != DungeonRoomState.WAITING) {
            return RoomActionResult.failure("ไม่สามารถยุบห้องที่กำลังเริ่มดันเจี้ยน");
        }

        room.members().forEach(member -> roomByPlayer.remove(member.playerId()));
        room.setState(DungeonRoomState.CLOSED);
        rooms.remove(room.id());
        return RoomActionResult.success(room, "ยุบห้องสำเร็จ");
    }

    public void handleDisconnect(UUID playerId) {
        DungeonScoreboardManager.forget(playerId);
        if (DungeonRuntime.get(server).getForPlayer(playerId).isPresent()) return;
        DungeonRoom room = findForPlayer(playerId).orElse(null);
        if (room == null) return;

        room.removeMember(playerId);
        roomByPlayer.remove(playerId);
        if (room.isEmpty()) {
            room.setState(DungeonRoomState.CLOSED);
            rooms.remove(room.id());
        }
    }

    public void handleDeath(UUID playerId) {
        ServerPlayer deadPlayer = server.getPlayerList().getPlayer(playerId);
        if (deadPlayer != null) {
            deadPlayer.getPersistentData().remove(DUNGEON_DEATH);
            deadPlayer.getPersistentData().remove(DEATH_EXIT);
        }
        DungeonInstance instance = DungeonRuntime.get(server).getForPlayer(playerId).orElse(null);
        if (instance == null) return;
        ActiveRun run = activeRuns.get(instance.id());
        if (run != null && !run.returned.contains(playerId)) {
            if (deadPlayer != null) deadPlayer.getPersistentData().putBoolean(DUNGEON_DEATH, true);
            run.eliminated.add(playerId);
            if (run.room.type() == DungeonRoomType.SOLO) {
                run.soloDeathPending = true;
            }
            DungeonRecoveryData.get(server).put(playerId, instance.members().get(playerId));
        }
    }

    public void handlePlayerReady(ServerPlayer player) {
        if (!player.isAlive()) return;
        boolean exitAtSpawn = player.getPersistentData().getBoolean(DUNGEON_DEATH)
                && player.getPersistentData().getBoolean(DEATH_EXIT);
        player.getPersistentData().remove(DUNGEON_DEATH);
        player.getPersistentData().remove(DEATH_EXIT);
        if (exitAtSpawn) {
            exitDungeon(player);
            restoreSpectatorMode(player);
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    overworld.getSharedSpawnAngle(), 0.0F);
            return;
        }
        DungeonInstance activeInstance = DungeonRuntime.get(server).getForPlayer(player.getUUID()).orElse(null);
        if (activeInstance != null) {
            ActiveRun activeRun = activeRuns.get(activeInstance.id());
            if (activeRun != null && !activeRun.returned.contains(player.getUUID())) {
                if (!activeRun.eliminated.contains(player.getUUID())) return;
                if (activeRun.soloDeathPending) return;
                if (!player.isAlive()) return;
                if (activeRun.phase != RunPhase.REWARD
                        && !activeRun.eliminated.containsAll(activeInstance.members().keySet())) {
                    spectateRun(activeRun, player);
                    return;
                }
                // The team has wiped, or the surviving members finished the dungeon.
                returnPlayer(activeRun, player);
                return;
            }
        }
        if (!player.isAlive()) return;
        DungeonReturnPoint point = DungeonRecoveryData.get(server).remove(player.getUUID()).orElse(null);
        if (point == null) {
            restoreSpectatorMode(player);
            return;
        }
        teleportToReturnPoint(player, point);
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§e[Dungeon] ส่งกลับจาก Dungeon Instance แล้ว"));
    }

    public ExitResult exitDungeon(ServerPlayer player) {
        DungeonInstance instance = DungeonRuntime.get(server).getForPlayer(player.getUUID()).orElse(null);
        if (instance != null) {
            ActiveRun run = activeRuns.get(instance.id());
            if (run == null) {
                return ExitResult.failure("ไม่พบ Dungeon run ที่กำลังทำงาน");
            }
            run.eliminated.add(player.getUUID());
            returnPlayer(run, player);
            notifyPlayers(run.memberIds(), "§e[Dungeon] " + player.getGameProfile().getName() + " ออกจากดันเจี้ยน");
            if (run.returned.containsAll(instance.members().keySet())) {
                activeRuns.remove(instance.id());
                run.enemies.forEach(entity -> {
                    if (entity != null && entity.isAlive()) entity.despawn();
                });
                cleanupRun(run);
            }
            return ExitResult.success("ออกจากดันเจี้ยนแล้ว");
        }

        DungeonReturnPoint recovery = DungeonRecoveryData.get(server).remove(player.getUUID()).orElse(null);
        if (recovery == null) {
            return ExitResult.failure("คุณไม่ได้อยู่ใน Dungeon Instance");
        }
        teleportToReturnPoint(player, recovery);
        return ExitResult.success("กู้คืนและส่งกลับจากดันเจี้ยนแล้ว");
    }

    public Optional<DungeonRoom> findForPlayer(UUID playerId) {
        Integer roomId = roomByPlayer.get(playerId);
        return roomId == null ? Optional.empty() : Optional.ofNullable(rooms.get(roomId));
    }

    public Optional<DungeonRoom> find(int roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    public Collection<DungeonRoom> rooms() {
        return java.util.List.copyOf(rooms.values());
    }

    private int allocateRoomId() {
        while (rooms.containsKey(nextRoomId)) {
            nextRoomId = nextRoomId == Integer.MAX_VALUE ? 1 : nextRoomId + 1;
        }
        return nextRoomId++;
    }

    private static DungeonRoomMember memberOf(ServerPlayer player) {
        return new DungeonRoomMember(player.getUUID(), player.getGameProfile().getName());
    }

    public record ExitResult(boolean successful, String message) {
        private static ExitResult success(String message) {
            return new ExitResult(true, message);
        }

        private static ExitResult failure(String message) {
            return new ExitResult(false, message);
        }
    }

    private enum RunPhase {
        FIGHTING_WAVE,
        WAITING_WAVE,
        WAITING_BOSS,
        FIGHTING_BOSS,
        REWARD
    }

    private static int soloDeathSecondsRemaining(long deadline, long tick) {
        return (int) Math.clamp((deadline - tick + 19L) / 20L, 0L, 10L);
    }

    private void sound(DungeonRoom room, DungeonSoundEffects.Cue cue) {
        sound(room, cue, null);
    }

    private void sound(ActiveRun run, DungeonSoundEffects.Cue cue) {
        sound(run.room, cue, run);
    }

    private void sound(DungeonRoom room, DungeonSoundEffects.Cue cue, ActiveRun run) {
        long tick = server.getTickCount();
        SoundKey key = new SoundKey(room.id(), cue);
        if (tick - soundCooldowns.getOrDefault(key, tick - 5) < 5) return;
        soundCooldowns.put(key, tick);
        for (var member : room.members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.playerId());
            if (player == null) continue;
            if (run != null && (run.returned.contains(player.getUUID()) || player.serverLevel() != run.level)) continue;
            for (var step : cue.steps()) {
                if (step.delay() == 0) DungeonSoundEffects.play(player, step);
                else pendingSounds.add(new PendingSound(room, run == null ? null : run.instance.id(), player.getUUID(),
                        player.serverLevel().dimension().location(), tick + step.delay(), step));
            }
        }
    }

    private void tickSounds() {
        soundCooldowns.keySet().removeIf(key -> !rooms.containsKey(key.roomId()));
        var iterator = pendingSounds.iterator();
        while (iterator.hasNext()) {
            PendingSound sound = iterator.next();
            if (server.getTickCount() < sound.tick()) continue;
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(sound.playerId());
            ActiveRun run = sound.instanceId() == null ? null : activeRuns.get(sound.instanceId());
            if (sound.instanceId() != null && (run == null || run.returned.contains(sound.playerId()))) continue;
            if (player != null && rooms.get(sound.room().id()) == sound.room() && sound.room().contains(sound.playerId())
                    && sound.dimension().equals(player.serverLevel().dimension().location())) DungeonSoundEffects.play(player, sound.step());
        }
    }

    private static final class ActiveRun {
        private final DungeonInstance instance;
        private final DungeonRoom room;
        private final DungeonDefinition definition;
        private final ServerLevel level;
        private final DungeonPlacementResult placement;
        private final long startedTick;
        private boolean completionRecorded;
        private final java.util.List<IEntity<?>> enemies = new java.util.ArrayList<>();
        private final java.util.Set<UUID> returned = new java.util.HashSet<>();
        private final java.util.Set<UUID> eliminated = new java.util.HashSet<>();
        private final Map<UUID, SimpleContainer> personalRewards = new java.util.HashMap<>();
        private int waveIndex;
        private RunPhase phase;
        private long nextActionTick;
        private boolean soloDeathPending;
        private boolean resultSoundPlayed;
        private long soloDeathExitTick = -1L;
        private int soloDeathLastSecond = -1;

        private ActiveRun(
                DungeonInstance instance,
                DungeonRoom room,
                DungeonDefinition definition,
                ServerLevel level,
                DungeonPlacementResult placement,
                long startedTick
        ) {
            this.instance = instance;
            this.room = room;
            this.definition = definition;
            this.level = level;
            this.placement = placement;
            this.startedTick = startedTick;
        }

        private java.util.List<UUID> memberIds() {
            return java.util.List.copyOf(instance.members().keySet());
        }
    }

    public record RoomActionResult(boolean successful, DungeonRoom room, String message) {
        private static RoomActionResult success(DungeonRoom room, String message) {
            return new RoomActionResult(true, room, message);
        }

        private static RoomActionResult failure(String message) {
            return new RoomActionResult(false, null, message);
        }
    }
}
