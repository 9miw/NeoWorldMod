package com.ambition.neoworld.ranking;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Shares in-flight scans and retains successful values for a full TTL after completion. */
final class RankingSnapshotCache<T> {
    private final long ttlMillis;
    private final LongSupplier clock;
    private CompletableFuture<T> value;
    private volatile long completedAt;

    RankingSnapshotCache(long ttlMillis,LongSupplier clock) {
        this.ttlMillis=ttlMillis; this.clock=clock;
    }
    synchronized CompletableFuture<T> get(Supplier<T> loader,Executor executor) {
        if(value==null || (value.isDone() && (value.isCompletedExceptionally() || clock.getAsLong()-completedAt>=ttlMillis))) {
            value=CompletableFuture.supplyAsync(() -> {
                T loaded=loader.get();
                completedAt=clock.getAsLong();
                return loaded;
            },executor);
        }
        return value;
    }
}
