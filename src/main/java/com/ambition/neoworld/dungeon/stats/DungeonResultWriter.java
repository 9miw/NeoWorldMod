package com.ambition.neoworld.dungeon.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/** Server-owned single-flight worker. Only immutable snapshots cross the I/O boundary. */
public final class DungeonResultWriter {
    @FunctionalInterface
    public interface Store { void record(DungeonClear clear) throws Exception; }
    private record Attempt(UUID runId, Exception failure) {}
    private final DungeonResultOutbox outbox;
    private final Executor executor;
    private final Store store;
    private final BiConsumer<UUID, Exception> errors;
    private CompletableFuture<List<Attempt>> inFlight;
    private long nextAttemptTick;
    private boolean stopped;

    public DungeonResultWriter(DungeonResultOutbox outbox, Executor executor, Store store,
            BiConsumer<UUID, Exception> errors) {
        this.outbox = outbox; this.executor = executor; this.store = store; this.errors = errors;
    }

    public void tick(long tick) {
        if (stopped) return;
        if (inFlight != null) {
            if (!inFlight.isDone()) return;
            try {
                boolean failed = acknowledge(inFlight.join());
                nextAttemptTick = tick + (failed ? 600 : 1);
            } catch (RuntimeException error) {
                errors.accept(null, error);
                nextAttemptTick = tick + 600;
            }
            inFlight = null;
        }
        if (tick < nextAttemptTick) return;
        List<DungeonClear> batch = outbox.pending(32);
        if (batch.isEmpty()) return;
        try {
            inFlight = CompletableFuture.supplyAsync(() -> {
                var results = new ArrayList<Attempt>();
                for (var clear : batch) {
                    try {
                        store.record(clear);
                        results.add(new Attempt(clear.runId(), null));
                    } catch (Exception error) {
                        results.add(new Attempt(clear.runId(), error));
                    }
                }
                return List.copyOf(results);
            }, executor);
        } catch (RuntimeException error) {
            errors.accept(null, error);
            nextAttemptTick = tick + 600;
        }
    }

    private boolean acknowledge(List<Attempt> results) {
        boolean failed = false;
        for (var result : results) {
            if (result.failure() == null) outbox.acknowledge(result.runId());
            else { failed = true; errors.accept(result.runId(), result.failure()); }
        }
        return failed;
    }

    /** Never discard unacknowledged rows on timeout; a late commit is safe to replay next start. */
    public void stop() {
        stopped = true;
        if (inFlight == null) return;
        try {
            acknowledge(inFlight.get(5, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            errors.accept(null, error);
        } catch (TimeoutException | java.util.concurrent.ExecutionException error) {
            errors.accept(null, error);
        }
    }
}
