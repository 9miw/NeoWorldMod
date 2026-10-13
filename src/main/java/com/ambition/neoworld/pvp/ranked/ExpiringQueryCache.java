package com.ambition.neoworld.pvp.ranked;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/** Coalesces concurrent reads, bounds retained keys, and backs off failed queries. */
public final class ExpiringQueryCache<K,V> {
    @FunctionalInterface public interface Loader<V> { V load() throws Exception; }
    private static final class Entry<V> { final CompletableFuture<V> future=new CompletableFuture<>(); long expires=Long.MAX_VALUE; }
    private final Map<K,Entry<V>> entries=new LinkedHashMap<>(16,0.75F,true);
    private final LongSupplier clock;
    private final Executor executor;
    private final long ttl,failureTtl;
    private final int capacity;
    public ExpiringQueryCache(LongSupplier clock,Executor executor,long ttl,long failureTtl,int capacity) {
        this.clock=clock; this.executor=executor; this.ttl=ttl; this.failureTtl=failureTtl; this.capacity=capacity;
    }
    public synchronized CompletableFuture<V> get(K key,Loader<V> loader) {
        long now=clock.getAsLong(); Entry<V> existing=entries.get(key);
        if(existing!=null && (existing.expires>now || !existing.future.isDone())) return existing.future;
        entries.entrySet().removeIf(item -> item.getValue().future.isDone() && item.getValue().expires<=now);
        if(entries.size()>=capacity) {
            var iterator=entries.entrySet().iterator(); boolean removed=false;
            while(iterator.hasNext()) if(iterator.next().getValue().future.isDone()) { iterator.remove(); removed=true; break; }
            if(!removed) return CompletableFuture.failedFuture(new IllegalStateException("PvP read queue busy"));
        }
        Entry<V> entry=new Entry<>(); entries.put(key,entry);
        try {
            executor.execute(() -> {
                try {
                    V value=loader.load(); synchronized(this) { entry.expires=clock.getAsLong()+ttl; }
                    entry.future.complete(value);
                } catch(Exception error) {
                    synchronized(this) { entry.expires=clock.getAsLong()+failureTtl; }
                    entry.future.completeExceptionally(error);
                }
            });
        } catch(RuntimeException error) { entry.expires=now+failureTtl; entry.future.completeExceptionally(error); }
        return entry.future;
    }
    public synchronized void clear() { entries.clear(); }
}
