package com.ambition.neoworld.pvp.room;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-thread room state, deliberately independent of dungeon rooms. */
public final class PvpRoom {
    public enum State { WAITING, LOADING, CLIENT_LOADING, COUNTDOWN, ACTIVE }
    private final int id;
    private final String arena;
    private final UUID owner;
    private final Set<UUID> members = new LinkedHashSet<>();
    private State state = State.WAITING;

    public PvpRoom(int id, String arena, UUID owner) {
        this.id = id;
        this.arena = arena;
        this.owner = owner;
        members.add(owner);
    }
    public int id() { return id; }
    public String arena() { return arena; }
    public UUID owner() { return owner; }
    public State state() { return state; }
    public List<UUID> members() { return List.copyOf(members); }
    public void join(UUID player) {
        if (state != State.WAITING || members.size() >= 2 || members.contains(player)) {
            throw new IllegalArgumentException("Room is full or has already started");
        }
        members.add(player);
    }
    /** Lobby eligibility only; the manager separately requires a two-party confirmation. */
    public boolean canStart() { return state == State.WAITING && members.size() == 2; }
    public void beginLoading() {
        if (!canStart()) throw new IllegalArgumentException("Two players are required");
        state = State.LOADING;
    }
    public void awaitClients() {
        if(state!=State.LOADING) throw new IllegalArgumentException("Room cannot await clients");
        state=State.CLIENT_LOADING;
    }
    public void begin() {
        if(state!=State.CLIENT_LOADING) throw new IllegalArgumentException("Room is not awaiting clients");
        state = State.COUNTDOWN;
    }
    public void activate() {
        if (state != State.COUNTDOWN) throw new IllegalStateException("Room is not counting down");
        state = State.ACTIVE;
    }
    public void pause() {
        if (state != State.ACTIVE && state!=State.COUNTDOWN) throw new IllegalStateException("Room has not started");
        state = State.COUNTDOWN;
    }
    public void leave(UUID player) {
        if (state != State.WAITING || owner.equals(player) || !members.contains(player)) throw new IllegalArgumentException("Cannot leave this room");
        members.remove(player);
    }
}
