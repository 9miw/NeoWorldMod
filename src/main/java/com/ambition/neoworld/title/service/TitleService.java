package com.ambition.neoworld.title.service;

import com.ambition.neoworld.title.JdbcTitleRepository;
import com.ambition.neoworld.title.sync.TitleSync;
import com.ambition.neoworld.title.TitlePlayerState;
import com.ambition.neoworld.title.TitleRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.ambition.neoworld.Config;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.database.NeoWorldDatabase;
import com.ambition.neoworld.registry.ModAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

/** Database writes commit before publishing to the server-thread cache. Never flush Attachments back to SQL. */
public final class TitleService {
    private static TitleService current;
    private final MinecraftServer server;
    private final JdbcTitleRepository repository;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "NeoWorld-Titles"));
    private final Map<UUID, TitlePlayerState> loaded = new HashMap<>();
    private final Map<UUID, ServerPlayer> loading = new HashMap<>();
    private final Set<UUID> reportedFailures = new HashSet<>();
    private boolean closing;

    private TitleService(MinecraftServer server) {
        this.server = server;
        repository = new JdbcTitleRepository(Config.DATABASE_TABLE_PREFIX.get());
    }
    public static Path cataloguePath() { return FMLPaths.CONFIGDIR.get().resolve("neoworld/titles.json"); }
    public static void start(MinecraftServer server) {
        try { TitleRegistry.load(cataloguePath()); }
        catch (IOException exception) { throw new IllegalStateException("Cannot load title catalogue", exception); }
        current = new TitleService(server);
    }
    public static TitleService get() {
        if (current == null) throw new IllegalStateException("Title service is not running");
        return current;
    }
    public static boolean isRunning() { return current != null && !current.closing; }
    public static void stop() {
        if (current == null) return;
        var service = current;
        service.closing = true;
        service.worker.shutdown();
        try {
            // Drain accepted SQL operations before the shared connection pool closes.
            while (!service.worker.awaitTermination(1, TimeUnit.SECONDS)) { }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while draining title writes", exception);
        }
        service.loaded.clear(); service.loading.clear();
        current = null;
    }
    public TitlePlayerState cached(UUID id) { return loaded.get(id); }
    public void load(ServerPlayer player) {
        var id = player.getUUID();
        if (closing || loaded.containsKey(id) || loading.containsKey(id)) return;
        var legacy = player.getData(ModAttachments.TITLE_DATA);
        var unlocked = legacy.unlockedTitles();
        var equipped = legacy.equippedId();
        loading.put(id, player);
        CompletableFuture.supplyAsync(() -> {
            try { return NeoWorldDatabase.inTransaction(connection -> repository.loadOrImport(
                    connection, id, unlocked, equipped, System.currentTimeMillis())); }
            catch (Exception exception) { throw new java.util.concurrent.CompletionException(exception); }
        }, worker).whenComplete((state, error) -> server.execute(() -> {
            if (closing || loading.get(id) != player) return;
            loading.remove(id);
            if (server.getPlayerList().getPlayer(id) != player) return;
            if (error != null) {
                if (reportedFailures.add(id)) NeoWorld.LOGGER.error("Could not load titles for {} (will retry)", id, error);
                return;
            }
            reportedFailures.remove(id);
            loaded.put(id, state);
            TitleSync.send(player);
        }));
    }
    public void forget(UUID id) { loaded.remove(id); loading.remove(id); reportedFailures.remove(id); }

    /** UUID APIs support offline players who have already completed their first-login import. */
    public CompletableFuture<TitlePlayerState> inspect(UUID id) {
        return submit(id, connection -> { });
    }
    public CompletableFuture<TitlePlayerState> grant(UUID id, String title) {
        if (TitleRegistry.find(title).isEmpty()) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown title"));
        return submit(id, connection -> repository.grant(connection, id, title, System.currentTimeMillis()));
    }
    public CompletableFuture<TitlePlayerState> grantAll(UUID id) {
        var titles = TitleRegistry.all().stream().map(title -> title.id()).toList();
        return submit(id, connection -> repository.grantAll(connection, id, titles, System.currentTimeMillis()));
    }
    public CompletableFuture<TitlePlayerState> revoke(UUID id, String title) {
        return submit(id, connection -> repository.revoke(connection, id, title));
    }
    public CompletableFuture<TitlePlayerState> equip(UUID id, String title) {
        if (!title.isEmpty() && TitleRegistry.find(title).isEmpty())
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown title"));
        return submit(id, connection -> repository.equip(connection, id, title));
    }
    public CompletableFuture<TitlePlayerState> settings(UUID id, boolean title, boolean effects) {
        return submit(id, connection -> repository.settings(connection, id, title, effects));
    }
    public CompletableFuture<TitlePlayerState> progress(UUID id, String metric, long amount) {
        return submit(id, connection -> repository.progress(connection, id, metric, amount));
    }
    public CompletableFuture<TitlePlayerState> discover(UUID id, String metric, String discovery) {
        return submit(id, connection -> repository.discover(connection, id, metric, discovery));
    }
    private CompletableFuture<TitlePlayerState> submit(UUID id, SqlAction action) {
        if (closing) return CompletableFuture.failedFuture(new IllegalStateException("Title service is stopping"));
        // This API is called on the server thread; capture the session, never update a later login.
        var session = server.getPlayerList().getPlayer(id);
        var response = new CompletableFuture<TitlePlayerState>();
        worker.execute(() -> {
            try {
                var state = NeoWorldDatabase.inTransaction(connection -> {
                    action.run(connection);
                    var snapshot = repository.load(connection, id);
                    if (snapshot == null) throw new java.sql.SQLException("Player has not completed the title import yet");
                    return snapshot;
                });
                server.execute(() -> {
                    if (!closing && session != null && server.getPlayerList().getPlayer(id) == session) {
                        loaded.put(id, state);
                        TitleSync.send(session);
                    }
                    response.complete(state);
                });
            } catch (Exception exception) { server.execute(() -> response.completeExceptionally(exception)); }
        });
        return response;
    }
    @FunctionalInterface private interface SqlAction { void run(java.sql.Connection connection) throws Exception; }
}
