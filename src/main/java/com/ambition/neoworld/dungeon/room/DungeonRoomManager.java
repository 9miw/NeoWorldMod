package com.ambition.neoworld.dungeon.room;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Owns temporary dungeon lobbies for one running server. */
public final class DungeonRoomManager {
    private static final Map<MinecraftServer, DungeonRoomManager> MANAGERS = new WeakHashMap<>();

    private final Map<Integer, DungeonRoom> rooms = new LinkedHashMap<>();
    private final Map<UUID, Integer> roomByPlayer = new LinkedHashMap<>();
    private int nextRoomId = 1;

    private DungeonRoomManager() {
    }

    public static synchronized DungeonRoomManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, ignored -> new DungeonRoomManager());
    }

    public RoomActionResult create(ServerPlayer owner, String dungeonId, DungeonRoomType type) {
        if (roomByPlayer.containsKey(owner.getUUID())) {
            return RoomActionResult.failure("คุณอยู่ในห้องดันเจี้ยนแล้ว");
        }
        int roomId = allocateRoomId();
        DungeonRoom room = new DungeonRoom(roomId, dungeonId, type, memberOf(owner));
        rooms.put(roomId, room);
        roomByPlayer.put(owner.getUUID(), roomId);
        return RoomActionResult.success(room, "สร้างห้องสำเร็จ");
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

    public RoomActionResult start(UUID playerId) {
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

        room.setState(DungeonRoomState.STARTING);
        return RoomActionResult.success(room, "ห้องพร้อมเริ่มดันเจี้ยน");
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
        DungeonRoom room = findForPlayer(playerId).orElse(null);
        if (room == null) return;

        room.removeMember(playerId);
        roomByPlayer.remove(playerId);
        if (room.isEmpty()) {
            room.setState(DungeonRoomState.CLOSED);
            rooms.remove(room.id());
        }
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

    public record RoomActionResult(boolean successful, DungeonRoom room, String message) {
        private static RoomActionResult success(DungeonRoom room, String message) {
            return new RoomActionResult(true, room, message);
        }

        private static RoomActionResult failure(String message) {
            return new RoomActionResult(false, null, message);
        }
    }
}
