package com.ambition.neoworld.pvp.ranked;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
public final class PvpRankingCache {
    private static final ExpiringQueryCache<UUID,PvpRatingRepository.View> PLAYERS=new ExpiringQueryCache<>(
            () -> System.nanoTime()/1_000_000,ForkJoinPool.commonPool(),30_000,5_000,512);
    private static final ExpiringQueryCache<String,List<PvpRatingRepository.Rating>> TOP=new ExpiringQueryCache<>(
            () -> System.nanoTime()/1_000_000,ForkJoinPool.commonPool(),30_000,5_000,1);
    private PvpRankingCache() { }
    public static CompletableFuture<PvpRatingRepository.View> player(UUID id,String name) { return PLAYERS.get(id,() -> PvpRatingRepository.view(id,name)); }
    public static CompletableFuture<List<PvpRatingRepository.Rating>> top() { return TOP.get("top",PvpRatingRepository::top); }
    public static void clear() { PLAYERS.clear(); TOP.clear(); }
}
