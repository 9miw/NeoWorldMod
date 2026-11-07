package com.ambition.neoworld.economy.analytics;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.database.NeoWorldDatabase;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.h2.jdbcx.JdbcDataSource;

/** Production spool/delivery classes with independent file-backed H2; no live player or server required. */
public final class AnalyticsOutboxRegressionTest {
    private static int checks;
    private AnalyticsOutboxRegressionTest() {}
    public static int run() throws Exception {
        checks=0;
        Path root=Files.createDirectories(Path.of("build","analytics-outbox-regression")).toAbsolutePath();
        restartAndReceipts(root);
        rollback(root);
        uncertainCommit(root);
        fileRecovery(root);
        concurrentAppend(root);
        shutdownOffline(root);
        startupRetry(root);
        System.out.println("Passed "+checks+" durable analytics outbox checks.");
        return checks;
    }

    private static void restartAndReceipts(Path root) throws Exception {
        Path dir=Files.createTempDirectory(root,"restart-"); var db=database(dir);
        var event=event("restart");
        var observation=new EconomyAnalyticsRepository.Balance("one",100,2,false,500,20,3);
        try(var outbox=new AnalyticsOutbox(dir.resolve("spool"))) {
            outbox.append(AnalyticsWork.event(event));
            outbox.append(AnalyticsWork.balance(observation,"2026-10-29"));
            outbox.append(AnalyticsWork.carryDaily("2026-10-30"));
            var batch=outbox.batch(256);
            check(batch.size()==3 && batch.get(0).work().event().equals(event),"typed jobs survive checksum/JSON round trip in order");
            AnalyticsDelivery.commit(batch,db::getConnection,"test_"); // Process stopped before any file acknowledgement.
            check(outbox.pending()==3 && count(db,"economy_outbox_receipts")==3,"SQL commit leaves files until acknowledgement");
        }
        try(var outbox=new AnalyticsOutbox(dir.resolve("spool"))) {
            check(outbox.pending()==3,"restart loads every unacknowledged job");
            // Another later observation must not be copied retroactively by a replayed carry job.
            try(var c=db.getConnection()) {
                EconomyAnalyticsRepository.balance(c,"test_",new EconomyAnalyticsRepository.Balance("later",300,3,false,700,0,0),"2026-10-31");
                EconomyAnalyticsRepository.balance(c,"test_",new EconomyAnalyticsRepository.Balance("one",400,3,false,900,20,3),"2026-10-29");
            }
            check(AnalyticsDelivery.flush(outbox,db::getConnection,"test_",256)==3,"restart acknowledges committed jobs without repeating effects");
            check(count(db,"economy_events")==1 && count(db,"economy_outbox_receipts")==3 && outbox.pending()==0,"receipt replay never duplicates events");
            check(scalar(db,"SELECT silver FROM test_economy_balances WHERE player_id='one'")==900,"replayed balance cannot revert a later observation");
            check(scalar(db,"SELECT COUNT(*) FROM test_economy_daily_balances WHERE snapshot_day='2026-10-30' AND player_id='later'")==0,
                    "replayed daily carry does not copy players that arrived after the original commit");
            outbox.append(AnalyticsWork.balance(observation,"2026-10-29"));
            AnalyticsDelivery.flush(outbox,db::getConnection,"test_",256);
            check(scalar(db,"SELECT silver FROM test_economy_daily_balances WHERE player_id='one' AND snapshot_day='2026-10-29'")==900,
                    "newly delivered stale observation cannot overwrite newer daily data");
        }
    }

    private static void rollback(Path root) throws Exception {
        Path dir=Files.createTempDirectory(root,"rollback-"); var db=database(dir);
        try(var c=db.getConnection()) {
            try { AnalyticsWork.event(event("autocommit")).apply(c,"test_"); throw new AssertionError("non-atomic receipt write accepted"); }
            catch(SQLException expected) { checks++; }
        }
        try(var outbox=new AnalyticsOutbox(dir.resolve("spool"))) {
            outbox.append(AnalyticsWork.event(event("valid")));
            outbox.append(AnalyticsWork.event(event("x".repeat(192)))); // SQL source VARCHAR(191) rejects the second row.
            try { AnalyticsDelivery.flush(outbox,db::getConnection,"test_",256); throw new AssertionError("invalid SQL payload accepted"); }
            catch(SQLException expected) { checks++; }
            check(count(db,"economy_events")==0 && count(db,"economy_outbox_receipts")==0,"batch rollback removes effects and receipts together");
            check(outbox.pending()==2,"SQL failure retains both jobs on disk");
        }
        try(var outbox=new AnalyticsOutbox(dir.resolve("spool"))) { check(outbox.pending()==2,"failed batch survives process restart"); }
    }

    private static void uncertainCommit(Path root) throws Exception {
        Path dir=Files.createTempDirectory(root,"ambiguous-"); var db=database(dir);
        boolean[] loseReply={true};
        AnalyticsDelivery.Connections connections=()-> {
            var real=db.getConnection();
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                try {
                    Object result=method.invoke(real,args);
                    if(method.getName().equals("commit") && loseReply[0]) { loseReply[0]=false; throw new SQLException("commit reply lost"); }
                    return result;
                } catch(InvocationTargetException e) { throw e.getCause(); }
            });
        };
        try(var outbox=new AnalyticsOutbox(dir.resolve("spool"))) {
            outbox.append(AnalyticsWork.event(event("ambiguous")));
            try { AnalyticsDelivery.flush(outbox,connections,"test_",256); throw new AssertionError("lost commit reply ignored"); }
            catch(SQLException expected) { checks++; }
            check(outbox.pending()==1 && count(db,"economy_events")==1,"uncertain commit keeps file despite committed SQL");
            AnalyticsDelivery.flush(outbox,db::getConnection,"test_",256);
            check(outbox.pending()==0 && count(db,"economy_events")==1,"uncertain commit retry is exactly-once in SQL");
        }
    }

    private static void fileRecovery(Path root) throws Exception {
        Path dir=Files.createTempDirectory(root,"files-"); Path spool=dir.resolve("spool");
        Path saved;
        try(var outbox=new AnalyticsOutbox(spool)) {
            outbox.append(AnalyticsWork.event(event("temp"))); saved=outbox.batch(1).getFirst().path();
            try(var second=new AnalyticsOutbox(spool)) { throw new AssertionError("two consumers acquired one spool"); }
            catch(IOException expected) { checks++; }
        }
        Files.move(saved,saved.resolveSibling(saved.getFileName().toString().replace(".json",".tmp")));
        try(var outbox=new AnalyticsOutbox(spool)) {
            check(outbox.pending()==1 && outbox.batch(1).getFirst().work().event().source().equals("temp"),"fully written temporary file recovers after interrupted rename");
            saved=outbox.batch(1).getFirst().path();
            String original=Files.readString(saved);
            Files.writeString(saved,original.replace("\"source\":\"temp\"","\"source\":\"tampered\""));
            try { outbox.batch(1); throw new AssertionError("changed work passed checksum"); }
            catch(IOException expected) { checks++; }
            check(Files.exists(saved),"checksum failure preserves the original evidence file");
            Files.writeString(saved,"{\"version\":1}");
            try { outbox.batch(1); throw new AssertionError("corrupt work accepted"); }
            catch(IOException expected) { checks++; }
            check(Files.exists(saved) && outbox.pending()==1,"corrupt file is retained for review, never silently dropped");
        }
    }

    private static void concurrentAppend(Path root) throws Exception {
        Path dir=Files.createTempDirectory(root,"concurrent-"); var db=database(dir);
        try(var outbox=new AnalyticsOutbox(dir.resolve("spool"))) {
            var writers=Executors.newFixedThreadPool(4);
            try {
                var futures=new ArrayList<Future<?>>();
                for(int t=0;t<4;t++) futures.add(writers.submit(()->{
                    for(int i=0;i<80;i++) try { outbox.append(AnalyticsWork.event(event("concurrent"))); }
                    catch(IOException e) { throw new RuntimeException(e); }
                }));
                for(var f:futures) f.get();
            } finally { writers.shutdownNow(); }
            check(outbox.pending()==320,"concurrent producers persist every job beyond a worker batch");
            check(AnalyticsDelivery.flush(outbox,db::getConnection,"test_",256)==256 && outbox.pending()==64,"bounded batch retains the remaining files");
            AnalyticsDelivery.flush(outbox,db::getConnection,"test_",256);
            check(outbox.pending()==0 && count(db,"economy_events")==320,"concurrent acceptance and multiple batches lose no jobs");
        }
    }

    private static void shutdownOffline(Path root) throws Exception {
        Path spool=Files.createTempDirectory(root,"offline-").resolve("spool");
        try {
            AnalyticsWriter.start(spool,()->{ throw new SQLException("offline"); },"test_");
            check(AnalyticsWriter.offer(AnalyticsWork.event(event("offline"))),"writer accepts durable telemetry while database is offline");
        } finally { AnalyticsWriter.stop(); }
        try(var outbox=new AnalyticsOutbox(spool)) { check(outbox.pending()==1,"shutdown during database outage retains the job and releases spool lock"); }
    }

    private static void startupRetry(Path root) throws Exception {
        Path spool=Files.createTempDirectory(root,"retiring-").resolve("spool");
        try {
            try(var retiring=new AnalyticsOutbox(spool)) {
                AnalyticsWriter.startWhenAvailable(spool,()->{ throw new SQLException("offline"); },"test_");
                check(AnalyticsWriter.pending()==-1,"locked spool reports unavailable instead of an empty queue");
            }
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(AnalyticsWriter.pending()<0 && System.nanoTime()<deadline) Thread.sleep(20);
            check(AnalyticsWriter.pending()==0,"new run automatically opens spool after retiring writer releases its lock");
            check(AnalyticsWriter.offer(AnalyticsWork.event(event("new-run"))),"reinitialized writer accepts new durable jobs");
        } finally { AnalyticsWriter.stop(); }
        try(var reopened=new AnalyticsOutbox(spool)) { check(reopened.pending()==1,"retry initialization retains the new run's queued work"); }
    }

    private static JdbcDataSource database(Path dir) throws Exception {
        var db=new JdbcDataSource();db.setURL("jdbc:h2:file:"+dir.resolve("database").toString().replace('\\','/')+";WRITE_DELAY=0");
        NeoWorldDatabase.runMigrations(db,Config.DatabaseType.H2,"test_");return db;
    }
    private static EconomyAnalyticsRepository.Event event(String source) {
        return new EconomyAnalyticsRepository.Event(UUID.randomUUID().toString(),1,"WALLET","player",1,false,source,"","SOURCE","SILVER",0,10,10,"{}");
    }
    private static long count(JdbcDataSource db,String suffix)throws Exception { return scalar(db,"SELECT COUNT(*) FROM test_"+suffix); }
    private static long scalar(JdbcDataSource db,String sql)throws Exception {
        try(var c=db.getConnection();var s=c.createStatement();var r=s.executeQuery(sql)) { r.next();return r.getLong(1); }
    }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message);checks++; }
}
