package com.ambition.neoworld.shop;

import com.ambition.neoworld.trade.TradeInventoryPlan;
import com.ambition.neoworld.trade.TradeInventoryPlan.Offer;
import com.ambition.neoworld.trade.TradeInventoryPlan.Stack;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Adapt the existing inventory planner to shop quantities spanning more than nine stacks. */
public final class ShopInventoryPlan {
    private ShopInventoryPlan() {}
    public static <T> List<Stack<T>> plan(List<Stack<T>> source, Stack<T> unit, int quantity, boolean sell, BiPredicate<T,T> same) {
        if (source.size() != 36 || unit.count() != 1 || quantity < 1 || quantity > 64)
            throw new IllegalArgumentException("Invalid shop inventory plan");
        List<Offer<T>> moves = new ArrayList<>();
        int remaining = quantity;
        if (sell) {
            for (int i = 0; i < source.size() && remaining > 0; i++) {
                Stack<T> stack = source.get(i);
                if (stack.count() == 0 || !same.test(stack.identity(), unit.identity())) continue;
                int take = Math.min(remaining, stack.count());
                moves.add(new Offer<>(i, new Stack<>(stack.identity(), take, stack.limit()))); remaining -= take;
            }
            if (remaining > 0) throw new IllegalArgumentException("Insufficient matching goods");
        } else {
            while (remaining > 0) {
                int count = Math.min(remaining, Math.min(64, unit.limit()));
                moves.add(new Offer<>(0, new Stack<>(unit.identity(),count,unit.limit()))); remaining -= count;
            }
        }
        List<Stack<T>> result = List.copyOf(source);
        for (int start = 0; start < moves.size(); start += 9) {
            List<Offer<T>> batch = moves.subList(start, Math.min(moves.size(),start+9));
            result = TradeInventoryPlan.plan(result, sell ? batch : List.of(), sell ? List.of() : batch, same);
        }
        return result;
    }
}
