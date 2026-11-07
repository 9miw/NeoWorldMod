package com.ambition.neoworld.market.service;

import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.ambition.neoworld.NeoWorld;

/** Dedicated, bounded executor for asynchronous market database mutations and queries. */
public final class MarketDatabaseExecutor {
    private static final int CORE_THREADS = 2;
    private static final int MAX_THREADS = 4;
    private static final int QUEUE_CAPACITY = 256;
    private static final long KEEP_ALIVE_SECONDS = 60L;

    private static final ConcurrentHashMap<UUID, String> IN_FLIGHT_MUTATIONS = new ConcurrentHashMap<>();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            CORE_THREADS,
            MAX_THREADS,
            KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            new MarketThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy()
    );

    private MarketDatabaseExecutor() {}

    /**
     * Attempts to acquire an exclusive in-flight lock for mutations by the given player.
     * @return true if acquired, false if a mutation is already in progress for this player.
     */
    public static boolean tryAcquire(UUID playerId, String operationType) {
        if (playerId == null || operationType == null) return false;
        return IN_FLIGHT_MUTATIONS.putIfAbsent(playerId, operationType) == null;
    }

    /** Releases the in-flight lock for the given player. */
    public static void release(UUID playerId) {
        if (playerId != null) {
            IN_FLIGHT_MUTATIONS.remove(playerId);
        }
    }

    /** Checks whether the given player currently has a mutation in-flight. */
    public static boolean isInFlight(UUID playerId) {
        return playerId != null && IN_FLIGHT_MUTATIONS.containsKey(playerId);
    }

    /** Submits a database task to the bounded market executor. */
    public static void execute(Runnable task) {
        EXECUTOR.execute(task);
    }

    /** Shuts down the executor cleanly when the server stops. */
    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException exception) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
        IN_FLIGHT_MUTATIONS.clear();
        NeoWorld.LOGGER.info("MarketDatabaseExecutor shut down cleanly");
    }

    private static final class MarketThreadFactory implements ThreadFactory {
        private final AtomicInteger threadCounter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "NeoWorld-MarketDB-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
