package com.ambition.neoworld.pvp.room;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One round's nonce-bound, two-player readiness gate. Server ticks own its deadline. */
public final class PvpClientLoadBarrier {
    public static final int TIMEOUT_TICKS=1200;
    private final UUID token=UUID.randomUUID();
    private final Set<UUID> players;
    private final Set<UUID> ready=new HashSet<>();
    private final long deadline;
    public PvpClientLoadBarrier(List<UUID> players,long now) {
        this.players=Set.copyOf(players);
        if(players.size()!=2 || this.players.size()!=2) throw new IllegalArgumentException("Two distinct players required");
        deadline=now+TIMEOUT_TICKS;
    }
    public UUID token() { return token; }
    public boolean expired(long now) { return now>=deadline; }
    public boolean accept(UUID token,UUID player,long now) {
        return this.token.equals(token) && !expired(now) && players.contains(player) && ready.add(player);
    }
    public boolean ready(long now) { return !expired(now) && ready.size()==players.size(); }
}
