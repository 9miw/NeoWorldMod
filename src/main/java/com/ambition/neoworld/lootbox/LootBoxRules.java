package com.ambition.neoworld.lootbox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

public final class LootBoxRules {
    public static final int MAX_REWARDS = 9;
    private LootBoxRules() {}
    public static void validateId(String id) {
        if (id == null || !id.matches("[a-z0-9_-]{1,48}"))
            throw new IllegalArgumentException("ID ต้องยาว 1–48 ตัว ใช้ a-z, 0-9, _ หรือ -");
    }
    public static void validateChance(double chance) {
        if (!Double.isFinite(chance) || chance < 0 || chance > 100)
            throw new IllegalArgumentException("น้ำหนักต้องอยู่ระหว่าง 0–100 (0 คือปิดรายการ)");
    }
    public static void validateCount(List<Double> weights, int count) {
        if (weights.isEmpty() || weights.size() > MAX_REWARDS)
            throw new IllegalArgumentException("กล่องต้องมีรางวัล 1–9 รายการ");
        weights.forEach(LootBoxRules::validateChance);
        if (count < 1 || count > weights.stream().filter(weight -> weight > 0).count())
            throw new IllegalArgumentException("จำนวนที่สุ่มต้องไม่น้อยกว่า 1 และไม่เกินรายการที่มีน้ำหนักมากกว่า 0");
    }
    /** Sequential weighted sampling without replacement. Values are weights, not independent percentages. */
    public static List<Integer> select(List<Double> weights, int count, DoubleSupplier random) {
        validateCount(weights, count);
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) if (weights.get(i) > 0) candidates.add(i);
        List<Integer> result = new ArrayList<>();
        for (int draw = 0; draw < count; draw++) {
            double total = candidates.stream().mapToDouble(weights::get).sum();
            double roll = random.getAsDouble();
            if (!Double.isFinite(roll) || roll < 0 || roll >= 1) throw new IllegalArgumentException("Invalid random roll");
            double ticket = roll * total;
            int selected = candidates.getLast();
            for (int candidate : candidates) {
                if (ticket < weights.get(candidate)) { selected = candidate; break; }
                ticket -= weights.get(candidate);
            }
            result.add(selected);
            candidates.remove(Integer.valueOf(selected));
        }
        return List.copyOf(result);
    }
}
