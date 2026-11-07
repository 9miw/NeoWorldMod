package com.ambition.neoworld.killer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One authoritative body per account; read-only login checks may run outside the tick loop. */
public final class CombatLogoutState<T> {
    public static final int COMBAT_TICKS = 1_200;
    public static final int BODY_TICKS = 1_200;
    public record Body<T>(UUID playerId, T player, long expiresAt) {}
    private final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Body<T>> bodies = new ConcurrentHashMap<>();

    public void tag(UUID attacker, UUID victim, long tick, boolean arenaCombat) {
        if (arenaCombat) {
            clearCombat(attacker);
            clearCombat(victim);
        } else if (!attacker.equals(victim)) {
            tagUntil(attacker, tick + COMBAT_TICKS);
            tagUntil(victim, tick + COMBAT_TICKS);
        }
    }

    public int remaining(UUID player, long tick) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, combatUntil.getOrDefault(player, tick) - tick));
    }

    public void tagUntil(UUID player, long deadline) {
        combatUntil.merge(player, deadline, Math::max);
    }

    public boolean retain(UUID player, T entity, long tick) {
        var existing = bodies.get(player);
        if (existing != null) return existing.player() == entity;
        if (remaining(player, tick) == 0) return false;
        return bodies.putIfAbsent(player, new Body<>(player, entity, tick + BODY_TICKS)) == null;
    }

    public Body<T> body(UUID player) { return bodies.get(player); }
    public Body<T> take(UUID player) { return bodies.remove(player); }
    public List<Body<T>> bodies() { return List.copyOf(bodies.values()); }
    public void clearCombat(UUID player) { combatUntil.remove(player); }
    public void expireCombat(long tick) { combatUntil.values().removeIf(deadline -> deadline <= tick); }
    public void clear() { bodies.clear(); combatUntil.clear(); }
}
