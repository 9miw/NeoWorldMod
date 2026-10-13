package com.ambition.neoworld.trade;

/** Revision binds both confirmations to one exact offer snapshot. */
public final class TradeAgreement {
    private int revision;
    private final boolean[] confirmed = new boolean[2];
    private long deadline;
    public int revision() { return revision; }
    public boolean confirmed(int side) { return confirmed[side]; }
    public long deadline() { return deadline; }
    public void changed() {
        revision++; confirmed[0] = false; confirmed[1] = false; deadline = 0;
    }
    public boolean confirm(int side, int expectedRevision, long tick) {
        if (expectedRevision != revision || confirmed[side]) return false;
        confirmed[side] = true;
        if (confirmed[0] && confirmed[1]) deadline = tick + 60;
        return true;
    }
    public boolean ready(long tick) { return confirmed[0] && confirmed[1] && tick >= deadline; }
}
