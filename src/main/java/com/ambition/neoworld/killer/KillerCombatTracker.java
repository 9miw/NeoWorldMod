package com.ambition.neoworld.killer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Short-lived, server-owned rights; a retaliation never reverses the aggressor. */
public final class KillerCombatTracker {
    private record Hit(UUID attacker, long tick) {}
    private final Map<UUID, Map<UUID, Long>> defense = new HashMap<>();
    private final Map<UUID, Hit> credit = new HashMap<>();

    public void recordDamage(UUID attacker, UUID victim, long tick) {
        recordDamage(attacker, victim, tick, false);
    }

    public void recordDamage(UUID attacker, UUID victim, long tick, boolean arenaCombat) {
        if (arenaCombat) {
            clear(attacker);
            clear(victim);
            return;
        }
        if (attacker.equals(victim)) return;
        if (!canDefend(attacker, victim, tick))
            defense.computeIfAbsent(victim, ignored -> new HashMap<>()).put(attacker, tick);
        credit.put(victim, new Hit(attacker, tick));
    }

    public boolean canDefend(UUID defender, UUID attacker, long tick) {
        var rights = defense.get(defender);
        Long hit = rights == null ? null : rights.get(attacker);
        return hit != null && recent(tick, hit, KillerRules.DEFENSE_TICKS);
    }

    public Optional<UUID> lastAttacker(UUID victim, long tick) {
        Hit hit = credit.get(victim);
        return hit != null && recent(tick, hit.tick(), KillerRules.CREDIT_TICKS)
                ? Optional.of(hit.attacker()) : Optional.empty();
    }

    public void clear(UUID player) {
        defense.remove(player);
        defense.values().forEach(rights -> rights.remove(player));
        defense.values().removeIf(Map::isEmpty);
        credit.remove(player);
        credit.values().removeIf(hit -> hit.attacker().equals(player));
    }

    public void expire(long tick) {
        defense.values().forEach(rights -> rights.values().removeIf(hit -> !recent(tick, hit, KillerRules.DEFENSE_TICKS)));
        defense.values().removeIf(Map::isEmpty);
        credit.values().removeIf(hit -> !recent(tick, hit.tick(), KillerRules.CREDIT_TICKS));
    }

    public void clearAll() {
        defense.clear();
        credit.clear();
    }

    private static boolean recent(long tick, long hit, int duration) {
        return tick >= hit && tick - hit < duration;
    }
}
