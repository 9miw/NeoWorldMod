package com.ambition.neoworld.pvp.arena;

import java.util.List;

/** Bounded placement slices; progress advances only after a successful world write. */
public final class PvpPlacementCursor {
    public static final int BLOCKS_PER_TICK=8192;
    public record Slice(int part,int from,int to) { public int size() { return to-from; } }
    private final List<Integer> counts;
    private int part,index;
    private long completed,total;
    public PvpPlacementCursor(List<Integer> counts) {
        this.counts=List.copyOf(counts);
        for(int count:counts) { if(count<0) throw new IllegalArgumentException("Negative piece size"); total+=count; }
        skipEmpty();
    }
    private void skipEmpty() { while(part<counts.size() && index==counts.get(part)) { part++; index=0; } }
    public boolean finished() { return part==counts.size(); }
    public long completed() { return completed; }
    public long total() { return total; }
    public Slice next() {
        if(finished()) throw new IllegalStateException("Placement is complete");
        return new Slice(part,index,Math.min(counts.get(part),index+BLOCKS_PER_TICK));
    }
    public void advance(Slice slice) {
        var expected=next();
        if(slice.part()!=expected.part() || slice.from()!=expected.from() || slice.to()<=slice.from() || slice.to()>expected.to())
            throw new IllegalArgumentException("Stale or oversized placement slice");
        completed+=slice.size(); index=slice.to(); skipEmpty();
    }
}
