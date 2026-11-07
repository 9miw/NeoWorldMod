package com.ambition.neoworld.navigation;

import java.util.UUID;

public final class QuestAutoTrackingRegressionTest {
    public static void run() {
        var pending=new QuestAutoTrackingQueue();
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        pending.accepted(first,101); pending.accepted(first,7); pending.accepted(second,42);
        var next=pending.drain();
        check(next.get(first)==7,"Most recently accepted quest wins, regardless of numeric quest ID");
        check(next.get(second)==42,"Latest quest is isolated per player");
        check(pending.drain().isEmpty(),"Consumed acceptance cannot restart manually stopped navigation every tick");
        pending.accepted(first,8); pending.cancel(first);
        check(pending.drain().isEmpty(),"Manual stop/track or logout cancels an unprocessed auto-track");
        pending.accepted(first,9);
        check(pending.drain().get(first)==9,"A new acceptance after stop starts automatic tracking again");
        System.out.println("Latest accepted quest auto-tracking regression checks passed");
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
