package com.ambition.neoworld.dungeon.room;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DungeonRoom {
    private final int id;
    private final String dungeonId;
    private final DungeonRoomType type;
    private final Map<UUID, DungeonRoomMember> members = new LinkedHashMap<>();
    private UUID ownerId;
    private DungeonRoomState state = DungeonRoomState.WAITING;

    DungeonRoom(int id, String dungeonId, DungeonRoomType type, DungeonRoomMember owner) {
        this.id = id;
        this.dungeonId = dungeonId;
        this.type = type;
        this.ownerId = owner.playerId();
        this.members.put(owner.playerId(), owner);
    }

    public int id() {
        return id;
    }

    public String dungeonId() {
        return dungeonId;
    }

    public DungeonRoomType type() {
        return type;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public DungeonRoomState state() {
        return state;
    }

    public Collection<DungeonRoomMember> members() {
        return java.util.List.copyOf(members.values());
    }

    public int memberCount() {
        return members.size();
    }

    public boolean isFull() {
        return memberCount() >= type.capacity();
    }

    void addMember(DungeonRoomMember member) {
        members.put(member.playerId(), member);
    }

    void removeMember(UUID playerId) {
        members.remove(playerId);
        if (playerId.equals(ownerId) && !members.isEmpty()) {
            ownerId = members.keySet().iterator().next();
        }
    }

    boolean contains(UUID playerId) {
        return members.containsKey(playerId);
    }

    boolean isEmpty() {
        return members.isEmpty();
    }

    void setState(DungeonRoomState state) {
        this.state = state;
    }
}
