package com.ambition.neoworld.pvp;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.ambition.neoworld.pvp.ranked.ExpiringQueryCache;

public final class PvpCacheRegressionTest {
    public static void run() {
        var clock=new AtomicLong(); var tasks=new ArrayDeque<Runnable>(); var reads=new AtomicInteger();
        var top=new ExpiringQueryCache<String,Integer>(clock::get,tasks::add,30_000,5_000,1);
        var first=top.get("top",reads::incrementAndGet);
        for(int i=0;i<20;i++) check(top.get("top",reads::incrementAndGet)==first,"Twenty concurrent viewers share one in-flight query");
        check(tasks.size()==1 && reads.get()==0,"Only one database job scheduled");
        tasks.remove().run(); check(first.join()==1,"First load completes");
        clock.set(2_000); check(top.get("top",reads::incrementAndGet).join()==1 && tasks.isEmpty(),"Two-second refresh does not reload rankings");
        clock.set(30_000); var expired=top.get("top",reads::incrementAndGet); tasks.remove().run();
        check(expired.join()==2,"TTL permits next database load");
        top.clear(); var stale=top.get("top",() -> 9); top.clear(); var fresh=top.get("top",() -> 10);
        tasks.remove().run(); tasks.remove().run();
        check(stale.join()==9 && fresh.join()==10 && top.get("top",()->11).join()==10,"Result invalidation prevents stale work repopulating the cache");
        var failure=new ExpiringQueryCache<String,Integer>(clock::get,tasks::add,30_000,5_000,1);
        var error=failure.get("player",()->{throw new java.sql.SQLException("offline");}); tasks.remove().run();
        check(error.isCompletedExceptionally() && failure.get("player",reads::incrementAndGet)==error,"Failed database reads back off instead of flooding");
        clock.addAndGet(5_000); var retry=failure.get("player",()->20); tasks.remove().run(); check(retry.join()==20,"Database recovery retries after backoff");
        var bounded=new ExpiringQueryCache<Integer,Integer>(clock::get,tasks::add,30_000,5_000,1);
        bounded.get(1,()->1); check(bounded.get(2,()->2).isCompletedExceptionally(),"Distinct outstanding queries remain bounded"); tasks.remove().run();
        System.out.println("PvP ranking cache/coalescing regression checks passed");
    }
    private static void check(boolean valid,String message) { if(!valid) throw new AssertionError(message); }
}
