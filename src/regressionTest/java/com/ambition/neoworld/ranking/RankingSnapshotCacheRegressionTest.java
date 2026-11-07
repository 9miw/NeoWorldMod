package com.ambition.neoworld.ranking;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;

public final class RankingSnapshotCacheRegressionTest {
    public static void run() {
        var clock=new AtomicLong();
        var work=new ArrayDeque<Runnable>();
        var cache=new RankingSnapshotCache<Integer>(30_000,clock::get);
        var first=cache.get(() -> 42,work::add);
        clock.set(60_000);
        if(first!=cache.get(() -> 99,work::add) || work.size()!=1) throw new AssertionError("pending scan must be shared");
        work.remove().run();
        clock.set(89_999);
        if(cache.get(() -> 99,work::add).join()!=42 || !work.isEmpty()) throw new AssertionError("TTL must start at completion");
        clock.set(90_000);
        var next=cache.get(() -> 99,work::add);
        if(next.isDone() || work.size()!=1) throw new AssertionError("expired snapshot must reload");
        work.remove().run();
        if(next.join()!=99) throw new AssertionError("reload result");
        clock.set(120_000);
        var failed=cache.get(() -> { throw new IllegalStateException("scan failed"); },work::add);
        work.remove().run();
        if(!failed.isCompletedExceptionally()) throw new AssertionError("errors must propagate");
        var retry=cache.get(() -> 7,work::add); work.remove().run();
        if(retry.join()!=7) throw new AssertionError("failed scan must permit retry");
        System.out.println("Ranking cache regression passed");
    }
}
