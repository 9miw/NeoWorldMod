package com.ambition.neoworld.economy.analytics;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.database.NeoWorldDatabase;
import net.neoforged.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Durably accepts typed jobs; the worker only removes files after SQL receipts commit. */
public final class AnalyticsWriter {
    private static final AtomicLong DROPPED = new AtomicLong();
    private static volatile Runtime current;
    private static ScheduledExecutorService initializer;
    private AnalyticsWriter() {}

    public static synchronized void start() {
        if (current != null) return;
        DROPPED.set(0);
        startWhenAvailable(FMLPaths.CONFIGDIR.get().resolve("neoworld/analytics-outbox"), NeoWorldDatabase::getConnection,
                Config.DATABASE_TABLE_PREFIX.get());
    }

    static synchronized void startWhenAvailable(Path directory, AnalyticsDelivery.Connections connections, String prefix) {
        if (current != null || initializer != null) return;
        try { start(directory,connections,prefix); return; }
        catch (IOException failure) { NeoWorld.LOGGER.error("Cannot open economy outbox yet; retrying initialization",failure); }
        var retry=Executors.newSingleThreadScheduledExecutor(r -> {
            var t=new Thread(r,"NeoWorld-economy-outbox-open"); t.setDaemon(true); return t;
        });
        initializer=retry;
        retry.scheduleWithFixedDelay(() -> {
            synchronized(AnalyticsWriter.class) {
                if(initializer!=retry)return;
                try {
                    start(directory,connections,prefix);
                    initializer=null; retry.shutdown();
                } catch(IOException stillUnavailable) {
                    // The initial failure is logged; acceptance failures remain counted until storage opens.
                }
            }
        },1,1,TimeUnit.SECONDS);
    }

    static synchronized void start(Path directory, AnalyticsDelivery.Connections connections, String prefix) throws IOException {
        if (current != null) return;
        var runtime = new Runtime(new AnalyticsOutbox(directory), connections, prefix);
        current=runtime;
        runtime.worker.scheduleWithFixedDelay(runtime::flush,1,1,TimeUnit.SECONDS);
    }

    public static boolean offer(AnalyticsWork work) {
        Runtime runtime=current;
        if (runtime != null && runtime.offer(work)) return true;
        long count=DROPPED.incrementAndGet();
        if (count==1 || count%100==0) NeoWorld.LOGGER.error("Economy outbox could not accept {} records; reports may be incomplete",count);
        return false;
    }
    public static long dropped() { return DROPPED.get(); }
    public static int pending() { Runtime runtime=current; return runtime==null ? -1 : runtime.outbox.pending(); }

    public static synchronized void stop() {
        if(initializer!=null) { initializer.shutdown(); initializer=null; }
        Runtime runtime=current;
        if (runtime==null) return;
        current=null;
        runtime.finish();
        try {
            if (!runtime.worker.awaitTermination(10,TimeUnit.SECONDS))
                NeoWorld.LOGGER.error("Economy outbox worker is still finishing; durable jobs remain on disk");
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }

    private static final class Runtime {
        final AnalyticsOutbox outbox;
        final AnalyticsDelivery.Connections connections;
        final String prefix;
        final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r -> {
            var t=new Thread(r,"NeoWorld-economy-analysis"); t.setDaemon(true); return t;
        });
        boolean accepting=true;
        long lastFailure;
        Runtime(AnalyticsOutbox outbox, AnalyticsDelivery.Connections connections, String prefix) {
            this.outbox=outbox; this.connections=connections; this.prefix=prefix;
        }
        synchronized boolean offer(AnalyticsWork work) {
            if (!accepting) return false;
            try { outbox.append(work); return true; }
            catch (IOException | RuntimeException failure) {
                NeoWorld.LOGGER.error("Could not persist economy telemetry to the local outbox",failure);
                return false;
            }
        }
        int flush() {
            try { return AnalyticsDelivery.flush(outbox,connections,prefix,256); }
            catch (Exception failure) {
                long now=System.currentTimeMillis();
                if(lastFailure==0 || now-lastFailure>=60000) {
                    lastFailure=now;
                    NeoWorld.LOGGER.error("Economy delivery failed; {} durable jobs retained for retry",outbox.pending(),failure);
                }
                return 0;
            }
        }
        synchronized void finish() {
            accepting=false;
            worker.execute(() -> {
                try {
                    while(outbox.pending()>0) if(flush()==0) break;
                } finally {
                    try { outbox.close(); }
                    catch(IOException failure) { NeoWorld.LOGGER.error("Could not close economy outbox",failure); }
                }
            });
            // Never interrupt an in-progress SQL commit or close its spool lock from another thread.
            worker.shutdown();
        }
    }
}
