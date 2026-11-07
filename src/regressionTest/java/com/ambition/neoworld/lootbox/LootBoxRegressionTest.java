package com.ambition.neoworld.lootbox;

import com.ambition.neoworld.trade.TradeInventoryPlan.Stack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LootBoxRegressionTest {
    private static int checks;
    public static void main(String[] args) {
        var full = new ArrayList<>(Collections.nCopies(36, new Stack<>("stone", 64, 64)));
        full.set(0, new Stack<>("box", 1, 64));
        var box = new Stack<>("box", 1, 64);
        var prize = new Stack<>("diamond", 3, 64);
        check(LootBoxInventoryPlan.hasRewardSlot(full, 0), "last main-hand box guarantees reward space before rolling");
        check(!LootBoxInventoryPlan.hasRewardSlot(full, -1), "offhand does not free a main-inventory slot");
        var result = LootBoxInventoryPlan.plan(full, 0, box, prize, String::equals);
        check(result.get(0).identity().equals("diamond") && result.get(0).count() == 3,
                "last main-hand box frees a slot for reward");
        check(full.get(0).identity().equals("box"), "planning leaves source untouched");
        full.set(0, new Stack<>("box", 2, 64));
        reject(() -> LootBoxInventoryPlan.plan(full, 0, box, prize, String::equals));
        check(full.get(0).count() == 2, "full inventory rejection preserves boxes");
        reject(() -> LootBoxInventoryPlan.plan(full, -1, box, prize, String::equals));
        full.set(8, new Stack<>("diamond", 61, 64));
        check(!LootBoxInventoryPlan.hasRewardSlot(full, -1), "partial compatible stacks cannot be used to filter rolls");
        result = LootBoxInventoryPlan.plan(full, -1, box, prize, String::equals);
        check(result.get(8).count() == 64 && result.get(0).count() == 2, "offhand reward merges without consuming main-hand box");
        full.set(8, new Stack<>("diamond:custom_components", 61, 64));
        reject(() -> LootBoxInventoryPlan.plan(full, -1, box, prize, String::equals));
        reject(() -> LootBoxInventoryPlan.plan(full, 1, box, prize, String::equals));
        reject(() -> LootBoxInventoryPlan.plan(full, 36, box, prize, String::equals));
        reject(() -> LootBoxInventoryPlan.plan(full, -2, box, prize, String::equals));
        reject(() -> LootBoxInventoryPlan.plan(full, 0, new Stack<>("box", 2, 64), prize, String::equals));
        reject(() -> LootBoxInventoryPlan.plan(full, 0, box, new Stack<>("diamond", 65, 64), String::equals));
        full.set(8, new Stack<>(null, 0, 64));
        check(LootBoxInventoryPlan.hasRewardSlot(full, -1), "empty slot allows every reward without reroll filtering");
        reject(() -> LootBoxRules.validateId("../escape"));
        reject(() -> LootBoxRules.validateId("Bad ID"));
        LootBoxRules.validateId("starter_01");
        reject(() -> LootBoxRules.validateChance(Double.NaN));
        reject(() -> LootBoxRules.validateChance(100.01));
        reject(() -> LootBoxRules.validateChance(-.01));
        check(LootBoxRules.select(List.of(0D,100D,50D), 2, () -> .499).equals(List.of(1,2)), "zero-weight entries excluded and selections never repeat");
        check(LootBoxRules.select(List.of(90D,10D),1,() -> .89).equals(List.of(0)), "weighted lower boundary");
        check(LootBoxRules.select(List.of(90D,10D),1,() -> .9).equals(List.of(1)), "weighted upper boundary");
        reject(() -> LootBoxRules.select(List.of(0D,50D), 2, () -> .5));
        reject(() -> LootBoxRules.select(List.of(100D), 0, () -> .5));
        reject(() -> LootBoxRules.select(List.of(100D), 1, () -> Double.NaN));
        var eight = List.of(10D,20D,30D,40D,50D,60D,70D,80D);
        var random = new java.util.Random(12345);
        for (int run = 0; run < 200; run++) {
            var chosen = LootBoxRules.select(eight, 3, random::nextDouble);
            check(chosen.size() == 3 && chosen.stream().distinct().count() == 3
                    && chosen.stream().allMatch(index -> index >= 0 && index < 8), "eight candidates produce exactly three unique rewards");
        }
        var bundle = List.of(new Stack<>("iron",3,64), new Stack<>("gold",2,64), prize);
        full.set(0,new Stack<>("box",1,64));
        check(!LootBoxInventoryPlan.hasRewardSlots(full,0,3), "reserve all possible rewards before random selection");
        full.set(9,new Stack<>(null,0,64));
        check(LootBoxInventoryPlan.hasRewardSlots(full,0,3), "last box plus two empty slots fit three rewards");
        check(!LootBoxInventoryPlan.hasRewardSlots(full,-1,3), "offhand does not count box slot towards bundle capacity");
        var bundleResult = LootBoxInventoryPlan.plan(full,0,box,bundle,String::equals);
        check(bundleResult.get(0).identity().equals("iron") && bundleResult.get(8).identity().equals("gold")
                && bundleResult.get(9).identity().equals("diamond"), "complete bundle delivered with one box consumed");
        full.set(9,new Stack<>("stone",64,64));
        reject(() -> LootBoxInventoryPlan.plan(full,0,box,bundle,String::equals));
        check(full.get(0).identity().equals("box"), "insufficient bundle capacity leaves box untouched");
        full.set(0,new Stack<>("box",2,64));
        check(LootBoxInventoryPlan.plan(full,0,box,List.<Stack<String>>of(),String::equals).get(0).count() == 1,
                "a valid random miss consumes exactly one box");
        System.out.println("Loot box regression checks passed: " + checks);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected rejection");
    }
}
