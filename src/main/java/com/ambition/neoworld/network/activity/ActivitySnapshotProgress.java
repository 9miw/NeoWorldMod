package com.ambition.neoworld.network.activity;

import java.util.BitSet;
import java.util.UUID;

/** Correlates the bounded pages of one snapshot without polling or duplicate-page counting. */
public final class ActivitySnapshotProgress {
    private final UUID request;
    private final BitSet[] received = {new BitSet(), new BitSet(), new BitSet(), new BitSet()};
    private final int[] expected = {-1, -1, -1, -1};
    public ActivitySnapshotProgress(UUID request) { this.request = request; }
    public boolean accept(UUID id, int tab, int page, int total) {
        if (!request.equals(id) || tab < 0 || tab > 3 || total < 0 || page < 0) return false;
        int pages = tab == 3 ? Math.max(1, (int) (((long) total + 5) / 6)) : 1;
        if (pages > 100_001 || page >= pages) return false;
        if (expected[tab] != pages) { expected[tab] = pages; received[tab].clear(); }
        received[tab].set(page); return true;
    }
    public boolean complete() {
        for (int tab = 0; tab < 4; tab++) if (expected[tab] < 0 || received[tab].cardinality() != expected[tab]) return false;
        return true;
    }
}
