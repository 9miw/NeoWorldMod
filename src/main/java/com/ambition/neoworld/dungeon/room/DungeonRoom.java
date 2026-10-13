package com.ambition.neoworld.dungeon.room;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DungeonRoom {
    private final int id;
    private final String dungeonId;
    private final DungeonRoomType type;
    private final UUID partyId;
    private final Map<UUID, DungeonRoomMember> members = new LinkedHashMap<>();
    private final java.util.Set<UUID> readyMembers = new java.util.HashSet<>();
    private UUID ownerId;
    private DungeonRoomState state = DungeonRoomState.WAITING;

    DungeonRoom(int id, String dungeonId, DungeonRoomType type, DungeonRoomMember owner) {
        this(id, dungeonId, type, null, java.util.List.of(owner), owner.playerId());
    }

    DungeonRoom(
            int id,
            String dungeonId,
            DungeonRoomType type,
            UUID partyId,
            Collection<DungeonRoomMember> members,
            UUID ownerId
    ) {
        this.id = id;
        this.dungeonId = dungeonId;
        this.type = type;
        this.partyId = partyId;
        this.ownerId = ownerId;
        members.forEach(member -> this.members.put(member.playerId(), member));
        if (!this.members.containsKey(ownerId)) {
            throw new IllegalArgumentException("Dungeon room owner must be a room member");
        }
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

    public Optional<UUID> partyId() {
        return Optional.ofNullable(partyId);
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

    public boolean isReady(UUID playerId) {
        return members.containsKey(playerId) && (playerId.equals(ownerId) || readyMembers.contains(playerId));
    }

    public boolean allReady() {
        return !members.isEmpty() && members.keySet().stream().allMatch(this::isReady);
    }

    public void clearReady() {
        readyMembers.clear();
    }

    void setReady(UUID playerId, boolean ready) {
        if (!members.containsKey(playerId) || playerId.equals(ownerId)) return;
        if (ready) readyMembers.add(playerId);
        else readyMembers.remove(playerId);
    }

    void addMember(DungeonRoomMember member) {
        members.put(member.playerId(), member);
        clearReady();
    }

    void removeMember(UUID playerId) {
        members.remove(playerId);
        clearReady();
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
