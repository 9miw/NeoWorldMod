package com.ambition.neoworld.killer;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.ambition.neoworld.data.PlayerData;

/** Exercises arena isolation, final death decisions, and the real wallet mutation. */
final class KillerLifecycleRegressionTest {
    static void run() {
        arenaCombatCannotGrantWorldRights();
        lateCancellationCannotChargeOrAddSin();
        canceledDeathCannotHideRealDeathInSameTick();
        confirmedDeathCommitsOnce();
        respawnChargesReplacementWallet();
        penaltyObservationEnclosesWalletMutation();
        System.out.println("Killer lifecycle and penalty regression checks passed");
    }

    private static void arenaCombatCannotGrantWorldRights() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var combat = new KillerCombatTracker();
        combat.recordDamage(a, b, 100);
        combat.recordDamage(a, b, 110, true);
        KillerRegressionTest.check(!combat.canDefend(b, a, 111), "Entering arena combat clears preexisting world rights");
        KillerRegressionTest.check(combat.lastAttacker(b, 111).isEmpty(), "Arena damage cannot credit environmental world kills");
        combat.recordDamage(b, a, 120, true);
        KillerRegressionTest.check(!combat.canDefend(a, b, 121), "Arena retaliation cannot grant world defense rights");
        combat.recordDamage(c, b, 130, false);
        KillerRegressionTest.check(combat.canDefend(b, c, 131), "Ordinary world combat still grants defense");
        KillerRegressionTest.check(!combat.canDefend(b, a, 131), "Leaving arena does not restore arena rights");
    }

    private static void lateCancellationCannotChargeOrAddSin() {
        var queue = new KillerDeathQueue<Object>();
        var victim = new Object();
        var canceled = new AtomicBoolean(false);
        var wallet = new PlayerData(1, 0, 1_000, 25, 10);
        var killer = new KillerData();
        queue.submit(victim, canceled::get, () -> {
            wallet.removeSilver(50);
            killer.addKill();
        });
        KillerRegressionTest.check(wallet.getSilver() == 1_000 && killer.getSin() == 0,
                "Death event dispatch must not mutate money or sin");
        canceled.set(true); // A later listener vetoes this death.
        queue.flush();
        KillerRegressionTest.check(wallet.getSilver() == 1_000 && killer.getSin() == 0,
                "Final cancellation must preserve money and sin");
        // The same living entity can really die later; cancellation does not consume its life.
        canceled.set(false);
        queue.submit(victim, canceled::get, killer::addKill);
        queue.flush();
        KillerRegressionTest.check(killer.getSin() == 100, "Canceled death must not suppress a later real death");
    }

    private static void confirmedDeathCommitsOnce() {
        var queue = new KillerDeathQueue<Object>();
        var life = new Object();
        var count = new AtomicInteger();
        queue.submit(life, () -> false, count::incrementAndGet);
        queue.submit(life, () -> false, count::incrementAndGet);
        KillerRegressionTest.check(count.get() == 0, "Confirmed death remains pending until dispatch has ended");
        queue.flush();
        queue.submit(life, () -> false, count::incrementAndGet);
        queue.flush();
        KillerRegressionTest.check(count.get() == 1, "Repeated death notification must not charge twice");
        queue.submit(new Object(), () -> false, count::incrementAndGet);
        queue.flush();
        KillerRegressionTest.check(count.get() == 2, "A respawned entity can incur its own later death penalty");
        queue.submit(new Object(), () -> false, count::incrementAndGet);
        queue.clear();
        queue.flush();
        KillerRegressionTest.check(count.get() == 2, "Server shutdown discards unfinished deaths");
    }

    private static void canceledDeathCannotHideRealDeathInSameTick() {
        var queue = new KillerDeathQueue<Object>();
        var life = new Object();
        var firstCanceled = new AtomicBoolean(false);
        var accepted = new AtomicInteger();
        queue.submit(life, firstCanceled::get, () -> { throw new AssertionError("Vetoed death must never commit"); });
        firstCanceled.set(true);
        queue.submit(life, () -> false, accepted::incrementAndGet);
        queue.flush();
        KillerRegressionTest.check(accepted.get() == 1, "A real second death in the same tick must replace a canceled first death");
    }

    private static void respawnChargesReplacementWallet() {
        var queue = new KillerDeathQueue<PlayerData>();
        var original = new PlayerData(1, 0, 1_000, 0, 0);
        var replacement = new PlayerData(1, 0, 1_000, 0, 0);
        queue.submit(original, () -> false, wallet -> KillerManager.chargeDeathPenalty(wallet, () -> () -> {}));
        queue.flush(original, replacement);
        queue.flush();
        KillerRegressionTest.check(replacement.getSilver() == 950 && original.getSilver() == 1_000,
                "Immediate respawn must charge copied live wallet once, not discarded original");
    }

    private static void penaltyObservationEnclosesWalletMutation() {
        var wallet = new PlayerData(1, 0, 1_000, 25, 10);
        long[] observation = {-1, -1};
        long charged = KillerManager.chargeDeathPenalty(wallet, () -> {
            observation[0] = wallet.getSilver();
            return () -> observation[1] = wallet.getSilver();
        });
        KillerRegressionTest.check(charged == 50 && wallet.getSilver() == 950, "Death penalty debits actual wallet once");
        KillerRegressionTest.check(observation[0] == 1_000 && observation[1] == 950,
                "Analytics must see before and after balances so the sink is attributed");
        KillerRegressionTest.check(wallet.getLegacyRetiredBalance() == 25 && wallet.getDiamond() == 10, "Penalty preserves other currencies");
        var tiny = new PlayerData(1, 0, 19, 0, 0);
        KillerRegressionTest.check(KillerManager.chargeDeathPenalty(tiny, () -> {
            throw new AssertionError("Zero charge should not open analytics observation");
        }) == 0 && tiny.getSilver() == 19, "Rounded-down zero penalty cannot change money");
    }
}
