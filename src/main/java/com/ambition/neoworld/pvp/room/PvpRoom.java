package com.ambition.neoworld.pvp.room;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-thread room state, deliberately independent of dungeon rooms. */
public final class PvpRoom {
    public enum State { WAITING, COUNTDOWN, ACTIVE }
    private final int id;
    private final String arena;
    private final UUID owner;
    private final Map<UUID, Boolean> members = new LinkedHashMap<>();
    private State state = State.WAITING;

    public PvpRoom(int id, String arena, UUID owner) {
        this.id = id;
        this.arena = arena;
        this.owner = owner;
        members.put(owner, false);
    }
    public int id() { return id; }
    public String arena() { return arena; }
    public UUID owner() { return owner; }
    public State state() { return state; }
    public List<UUID> members() { return List.copyOf(members.keySet()); }
    public boolean ready(UUID player) { return Boolean.TRUE.equals(members.get(player)); }
    public void join(UUID player) {
        if (state != State.WAITING || members.size() >= 2 || members.containsKey(player)) {
            throw new IllegalArgumentException("Room is full or has already started");
        }
        members.put(player, false);
        members.replaceAll((id, ready) -> false);
    }
    public void setReady(UUID player, boolean ready) {
        if (state != State.WAITING || !members.containsKey(player)) {
            throw new IllegalArgumentException("You cannot change readiness now");
        }
        members.put(player, ready);
    }
    public boolean canStart() { return state == State.WAITING && members.size() == 2 && members.values().stream().allMatch(Boolean::booleanValue); }
    public void begin() {
        if (!canStart()) throw new IllegalArgumentException("Both players must be ready");
        state = State.COUNTDOWN;
    }
    public void activate() {
        if (state != State.COUNTDOWN) throw new IllegalStateException("Room is not counting down");
        state = State.ACTIVE;
    }
    public void pause() {
        if (state == State.WAITING) throw new IllegalStateException("Room has not started");
        state = State.COUNTDOWN;
    }
    public void leave(UUID player) {
        if (state != State.WAITING || owner.equals(player) || !members.containsKey(player)) throw new IllegalArgumentException("Cannot leave this room");
        members.remove(player);
        members.replaceAll((id, ready) -> false);
    }
}
