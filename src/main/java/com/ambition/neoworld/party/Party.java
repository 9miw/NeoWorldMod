package com.ambition.neoworld.party;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Party {
    private final UUID id;
    private final int capacity;
    private final Map<UUID, PartyMember> members = new LinkedHashMap<>();
    private final Set<UUID> readyMembers = new java.util.HashSet<>();
    private UUID leaderId;

    Party(UUID id, int capacity, PartyMember leader) {
        if (capacity != 3 && capacity != 6) {
            throw new IllegalArgumentException("Party capacity must be 3 or 6");
        }
        this.id = id;
        this.capacity = capacity;
        this.leaderId = leader.playerId();
        this.members.put(leader.playerId(), leader);
    }

    public UUID id() {
        return id;
    }

    public int capacity() {
        return capacity;
    }

    public UUID leaderId() {
        return leaderId;
    }

    public Collection<PartyMember> members() {
        return java.util.List.copyOf(members.values());
    }

    public int memberCount() {
        return members.size();
    }

    public boolean isFull() {
        return memberCount() >= capacity;
    }

    public boolean contains(UUID playerId) {
        return members.containsKey(playerId);
    }

    public boolean isReady(UUID playerId) {
        return readyMembers.contains(playerId);
    }

    public boolean allReady() {
        return !members.isEmpty() && readyMembers.containsAll(members.keySet());
    }

    void addMember(PartyMember member) {
        members.put(member.playerId(), member);
        readyMembers.remove(member.playerId());
    }

    void removeMember(UUID playerId) {
        members.remove(playerId);
        readyMembers.remove(playerId);
        if (playerId.equals(leaderId) && !members.isEmpty()) {
            leaderId = members.keySet().iterator().next();
        }
    }

    void setReady(UUID playerId, boolean ready) {
        if (ready) {
            readyMembers.add(playerId);
        } else {
            readyMembers.remove(playerId);
        }
    }

    public void clearReady() {
        readyMembers.clear();
    }

    boolean isEmpty() {
        return members.isEmpty();
    }
}
