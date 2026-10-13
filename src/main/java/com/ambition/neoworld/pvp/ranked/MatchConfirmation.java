package com.ambition.neoworld.pvp.ranked;

import java.util.UUID;

/** A timed offer has no room or arena allocation until both players accept. */
public final class MatchConfirmation {
    public enum Status { WAITING, ACCEPTED, EXPIRED }
    public final UUID id=UUID.randomUUID(),a,b;
    public final long deadline;
    private boolean readyA,readyB;
    public MatchConfirmation(UUID a,UUID b,long now) {
        if(a==null || b==null || a.equals(b)) throw new IllegalArgumentException("Invalid opponents");
        this.a=a; this.b=b; deadline=now+600;
    }
    public boolean contains(UUID player) { return a.equals(player) || b.equals(player); }
    public boolean ready(UUID player) {
        if(!contains(player)) throw new IllegalArgumentException("Player is outside this offer");
        return a.equals(player) ? readyA : readyB;
    }
    public boolean accept(UUID player,long now) {
        if(!contains(player)) throw new IllegalArgumentException("Player is outside this offer");
        if(now>=deadline) return false;
        if(a.equals(player)) readyA=true; else readyB=true;
        return true;
    }
    public Status status(long now) { return readyA && readyB ? Status.ACCEPTED : now>=deadline ? Status.EXPIRED : Status.WAITING; }
    public int remaining(long now) { return (int)Math.clamp((deadline-now+19)/20,0,30); }
}
