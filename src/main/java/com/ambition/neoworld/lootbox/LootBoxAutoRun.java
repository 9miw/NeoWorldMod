package com.ambition.neoworld.lootbox;

/** Server tick based progress; only successful inventory exchanges advance it. */
public final class LootBoxAutoRun {
    public static final int MAX_BOXES = 64;
    public static final int INTERVAL = 80;
    public static final int FAST_INTERVAL = 4;
    private final int interval;
    private int remaining;
    private long nextTick;
    public LootBoxAutoRun(int count, long tick) {
        this(count, tick, false);
    }
    public LootBoxAutoRun(int count, long tick, boolean fast) {
        if (count < 1 || count > MAX_BOXES) throw new IllegalArgumentException("Invalid auto-open count");
        interval = fast ? FAST_INTERVAL : INTERVAL;
        remaining = count; nextTick = tick + (fast ? FAST_INTERVAL : 0);
    }
    public int interval() { return interval; }
    public int remaining() { return remaining; }
    public boolean ready(long tick) { return remaining > 0 && tick >= nextTick; }
    public void opened(long tick) {
        if (!ready(tick)) throw new IllegalArgumentException("Auto opening is not ready");
        remaining--; nextTick = tick + interval;
    }
    public void stop() { remaining = 0; }
}
