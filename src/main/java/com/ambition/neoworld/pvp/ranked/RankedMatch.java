package com.ambition.neoworld.pvp.ranked;

import java.util.UUID;

/** Best-of-three state shared by combat and disconnect handling. */
public final class RankedMatch {
    public final UUID id = UUID.randomUUID();
    public final UUID a, b;
    public final String nameA, nameB;
    public int winsA, winsB;
    public long countdown, roundStarted;
    public boolean began;
    public boolean resultSaved;
    public Double result;
    public String reason;
    public RankedMatch(UUID a, UUID b, String nameA, String nameB, long now) {
        this.a=a; this.b=b; this.nameA=nameA; this.nameB=nameB; countdown=now+60;
    }
    public void round(UUID winner) {
        if (result != null) return;
        if (a.equals(winner)) winsA++; else if (b.equals(winner)) winsB++;
        else throw new IllegalArgumentException("Winner is outside this match");
        if (winsA == 2 || winsB == 2) finish(winsA == 2 ? 1 : 0, "best_of_three");
    }
    public void forfeit(UUID loser) {
        if (!a.equals(loser) && !b.equals(loser)) throw new IllegalArgumentException("Loser is outside this match");
        finish(a.equals(loser) ? 0 : 1, "forfeit");
    }
    /** Leaving or disconnecting after combat starts decides the whole match immediately. */
    public boolean exit(UUID loser) {
        if(!a.equals(loser) && !b.equals(loser)) throw new IllegalArgumentException("Player is outside this match");
        if(!began || result!=null) return false;
        forfeit(loser); return true;
    }
    public void finish(double score, String reason) { if (result == null) { result=score; this.reason=reason; } }
}
