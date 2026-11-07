package com.ambition.neoworld.killer;

import java.util.UUID;

/** Executable rules tests: no game, database, or fake player required. */
public final class KillerRegressionTest {
    public static void main(String[] args) {
        penalties();
        defenseAndCredit();
        persistenceAndDecay();
        deathClassification();
        clientSyncAndCache();
        KillerLifecycleRegressionTest.run();
        CombatLogoutRegressionTest.run();
        CombatLogoutHooksRegressionTest.run();
        KillerSafeZoneRegressionTest.run();
        System.out.println("Killer rules regression checks passed");
    }

    private static void penalties() {
        check(KillerRules.deathPenalty(0) == 0, "Empty wallet stays empty");
        check(KillerRules.deathPenalty(19) == 0, "Fractional Silver rounds down");
        check(KillerRules.deathPenalty(20) == 1, "Five percent starts at one Silver");
        check(KillerRules.deathPenalty(1_000) == 50, "Ordinary wallet loses five percent");
        check(KillerRules.deathPenalty(20_000) == 1_000, "Penalty reaches cap");
        check(KillerRules.deathPenalty(Long.MAX_VALUE) == 1_000, "Huge balance cannot overflow");
        check(KillerRules.deathPenalty(-1) == 0, "Invalid negative balance cannot credit money");
        check(!KillerRules.isRed(0) && KillerRules.isRed(1), "Any positive sin is red");
        check(KillerRules.addKillSin(0) == 100 && KillerRules.addKillSin(100) == 200, "Repeated murders accumulate");
        check(KillerRules.addKillSin(Integer.MAX_VALUE - 50) == Integer.MAX_VALUE, "Sin saturates without becoming innocent");
    }

    private static void defenseAndCredit() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var combat = new KillerCombatTracker();
        combat.recordDamage(a, b, 100);
        check(combat.canDefend(b, a, 101), "Victim can defend against aggressor");
        check(!combat.canDefend(a, b, 101), "Aggressor has no defense right");
        check(!combat.canDefend(c, a, 101), "Third party cannot borrow defense right");
        combat.recordDamage(b, a, 110);
        check(!combat.canDefend(a, b, 111), "Retaliation cannot grant defense to aggressor");
        check(combat.canDefend(b, a, 1_299), "Right lasts until just before sixty seconds");
        check(!combat.canDefend(b, a, 1_300), "Right expires at sixty seconds");
        check(combat.lastAttacker(b, 699).orElseThrow().equals(a), "Environmental death credits last damaging owner");
        check(combat.lastAttacker(b, 700).isEmpty(), "Environmental credit expires at thirty seconds");
        combat.recordDamage(a, b, 1_400);
        combat.recordDamage(a, b, 1_800);
        check(combat.canDefend(b, a, 2_999), "New hit from aggressor refreshes right");
        combat.recordDamage(c, b, 1_900);
        check(combat.lastAttacker(b, 1_901).orElseThrow().equals(c), "Latest attacker replaces environmental credit");
        combat.clear(a);
        check(!combat.canDefend(b, a, 1_901), "Logout removes rights involving departed player");
        check(combat.canDefend(b, c, 1_901), "Clearing one player preserves another fight");
        combat.clear(b);
        check(combat.lastAttacker(b, 1_901).isEmpty() && !combat.canDefend(b, c, 1_901), "Respawn removes victim combat state");
        combat.recordDamage(a, a, 2_000);
        check(combat.lastAttacker(a, 2_001).isEmpty(), "Self damage never grants kill credit");
        combat.recordDamage(a, b, 2_000);
        combat.expire(4_000);
        check(!combat.canDefend(b, a, 4_000) && combat.lastAttacker(b, 4_000).isEmpty(), "Expired fights are discarded");
    }

    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static void persistenceAndDecay() {
        var data = new KillerData();
        for (int i = 0; i < 20_000; i++) data.tickOnline();
        data.addKill();
        check(data.getSin() == 100 && data.getOnlineTicks() == 0, "Innocent time cannot prepay future sin");
        for (int i = 0; i < 11_999; i++) check(!data.tickOnline(), "Sin does not decay before ten minutes");
        var saved = KillerData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data).getOrThrow();
        var restored = KillerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow();
        check(restored.getSin() == 100 && restored.getOnlineTicks() == 11_999, "Save preserves sin and partial online time");
        check(restored.tickOnline() && restored.getSin() == 90 && restored.getOnlineTicks() == 0, "Resumed online time reduces sin at exact boundary");
        var almostClean = new KillerData(5, 11_999);
        check(almostClean.tickOnline() && almostClean.getSin() == 0, "Last decay clamps at zero");
        check(!almostClean.tickOnline() && almostClean.getOnlineTicks() == 0, "Clean player does not accrue decay time");
        var missing = KillerData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow();
        check(missing.getSin() == 0 && missing.getOnlineTicks() == 0, "Existing saves default to innocent");
        var invalid = new KillerData(-1, Integer.MAX_VALUE);
        check(invalid.getSin() == 0 && invalid.getOnlineTicks() == 0, "Invalid stored state is normalized");
    }

    private static void deathClassification() {
        check(KillerRules.sinForKill(false, false, false) == 100, "Unprovoked kill adds sin");
        check(KillerRules.sinForKill(true, false, false) == 0, "Red victim adds no sin");
        check(KillerRules.sinForKill(false, true, false) == 0, "Self-defense adds no sin");
        check(KillerRules.sinForKill(false, false, true) == 0, "Arena or canceled death adds no sin");
    }

    private static void clientSyncAndCache() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        com.ambition.neoworld.client.state.ClientKillerData.reset();
        check(!com.ambition.neoworld.client.state.ClientKillerData.isRed(player1)
                && com.ambition.neoworld.client.state.ClientKillerData.getSin(player1) == 0, "Default player is not red");
        com.ambition.neoworld.client.state.ClientKillerData.update(player1, 100);
        check(com.ambition.neoworld.client.state.ClientKillerData.isRed(player1)
                && com.ambition.neoworld.client.state.ClientKillerData.getSin(player1) == 100, "Updated player has sin and is red");
        check(!com.ambition.neoworld.client.state.ClientKillerData.isRed(player2), "Other players remain unaffected");
        com.ambition.neoworld.client.state.ClientKillerData.update(player1, 0);
        check(!com.ambition.neoworld.client.state.ClientKillerData.isRed(player1)
                && com.ambition.neoworld.client.state.ClientKillerData.getSin(player1) == 0, "Zero sin removes red state");
        com.ambition.neoworld.client.state.ClientKillerData.update(player2, 50);
        com.ambition.neoworld.client.state.ClientKillerData.remove(player2);
        check(!com.ambition.neoworld.client.state.ClientKillerData.isRed(player2), "Removed player is not red");
        com.ambition.neoworld.client.state.ClientKillerData.update(player1, 200);
        com.ambition.neoworld.client.state.ClientKillerData.reset();
        check(!com.ambition.neoworld.client.state.ClientKillerData.isRed(player1), "Reset clears all player state");
    }
}
