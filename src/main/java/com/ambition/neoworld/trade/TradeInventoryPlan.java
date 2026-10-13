package com.ambition.neoworld.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Immutable stack arithmetic, independent of the Minecraft runtime. Identity includes components. */
public final class TradeInventoryPlan {
    private TradeInventoryPlan() {}
    public record Stack<T>(T identity, int count, int limit) {
        public Stack {
            if (count < 0 || limit <= 0 || (count > 0 && identity == null)) throw new IllegalArgumentException("Invalid stack");
        }
        Stack<T> withCount(int count) { return new Stack<>(count == 0 ? null : identity, count, limit); }
        boolean empty() { return count == 0; }
    }
    public record Offer<T>(int slot, Stack<T> stack) {}

    public static <T> List<Stack<T>> plan(List<Stack<T>> source, List<Offer<T>> outgoing,
            List<Offer<T>> incoming, BiPredicate<T, T> sameIdentity) {
        if (source.size() != 36 || outgoing.size() > 9 || incoming.size() > 9) throw new IllegalArgumentException("รายการไม่ถูกต้อง");
        List<Stack<T>> result = new ArrayList<>(source);
        boolean[] seen = new boolean[36];
        for (Offer<T> offer : outgoing) {
            int slot = offer.slot;
            Stack<T> item = offer.stack;
            if (slot < 0 || slot >= 36 || seen[slot] || item.empty() || item.count > item.limit) throw new IllegalArgumentException("ช่องไอเทมไม่ถูกต้อง");
            seen[slot] = true;
            Stack<T> actual = result.get(slot);
            if (actual.empty() || !sameIdentity.test(actual.identity, item.identity) || actual.count < item.count) throw new IllegalArgumentException("ไอเทมในกระเป๋าเปลี่ยนแล้ว");
            result.set(slot, actual.withCount(actual.count - item.count));
        }
        for (Offer<T> offer : incoming) {
            Stack<T> item = offer.stack;
            if (item.empty() || item.count > item.limit) throw new IllegalArgumentException("จำนวนไอเทมไม่ถูกต้อง");
            int remaining = item.count;
            for (int slot = 0; slot < 36 && remaining > 0; slot++) {
                Stack<T> actual = result.get(slot);
                if (!actual.empty() && sameIdentity.test(actual.identity, item.identity)) {
                    int amount = Math.min(remaining, Math.max(0, Math.min(64, actual.limit) - actual.count));
                    result.set(slot, actual.withCount(actual.count + amount)); remaining -= amount;
                }
            }
            for (int slot = 0; slot < 36 && remaining > 0; slot++) {
                if (result.get(slot).empty()) {
                    int amount = Math.min(remaining, Math.min(64, item.limit));
                    result.set(slot, item.withCount(amount)); remaining -= amount;
                }
            }
            if (remaining > 0) throw new IllegalArgumentException("กระเป๋ามีพื้นที่ไม่เพียงพอ");
        }
        return List.copyOf(result);
    }
}
