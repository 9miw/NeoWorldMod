package com.ambition.neoworld.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import com.ambition.neoworld.network.TradeActionPayload;
import com.ambition.neoworld.network.TradeStatePayload;
import com.ambition.neoworld.trade.TradeInventoryPlan.Offer;
import com.ambition.neoworld.trade.TradeInventoryPlan.Stack;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.inventory.ClickType;

public final class TradeRegressionTest {
    private static int checks;
    public static void main(String[] args) {
        money(); inventory(); agreement(); codec(); slots();
        System.out.println("Passed " + checks + " trade regression checks.");
    }
    private static void money() {
        check(TradePlan.balance(100, 0, 0) == 100, "Zero Silver permitted");
        check(TradePlan.balance(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE) == Long.MAX_VALUE, "Maximum net exchange");
        rejects(() -> TradePlan.balance(10, 11, 100), "Incoming cannot fund offer");
        rejects(() -> TradePlan.balance(10, -1, 0), "Negative outgoing");
        rejects(() -> TradePlan.balance(10, 0, -1), "Negative incoming");
        rejects(() -> TradePlan.balance(Long.MAX_VALUE, 0, 1), "Overflow");
        Random r = new Random(9817);
        for (int i = 0; i < 500; i++) {
            long a = r.nextInt(100000), b = r.nextInt(100000);
            long outA = r.nextLong(a + 1), outB = r.nextLong(b + 1);
            check(TradePlan.balance(a, outA, outB) + TradePlan.balance(b, outB, outA) == a + b, "Silver conservation");
        }
    }
    private static void inventory() {
        var a = empty(); var b = empty();
        a.set(0, stack("diamond", 40)); b.set(35, stack("emerald", 12));
        var oa = new Offer<>(0, stack("diamond", 17)); var ob = new Offer<>(35, stack("emerald", 8));
        var ra = plan(a, List.of(oa), List.of(ob)); var rb = plan(b, List.of(ob), List.of(oa));
        check(total(ra, "diamond") + total(rb, "diamond") == 40, "Diamond conservation");
        check(total(ra, "emerald") + total(rb, "emerald") == 12, "Emerald conservation");
        check(a.get(0).count() == 40 && b.get(35).count() == 12 && oa.stack().count() == 17, "Inputs unchanged");
        var full = new ArrayList<Stack<String>>();
        for (int i = 0; i < 36; i++) full.add(stack("stone", 64));
        rejects(() -> plan(full, List.of(), List.of(oa)), "Full inventory");
        check(full.stream().allMatch(s -> s.count() == 64), "Failure preserves input");
        check(total(plan(full, List.of(new Offer<>(0, full.get(0))), List.of(oa)), "diamond") == 17, "Outgoing frees capacity");
        rejects(() -> plan(a, List.of(oa, oa), List.of()), "Duplicate source");
        rejects(() -> plan(a, List.of(new Offer<>(0, stack("diamond", 41))), List.of()), "Count changed");
        rejects(() -> plan(a, List.of(new Offer<>(36, stack("diamond", 1))), List.of()), "Invalid slot");
        rejects(() -> plan(a, List.of(new Offer<>(0, stack("diamond:renamed", 17))), List.of()), "Components changed");
        rejects(() -> plan(a, List.of(new Offer<>(0, stack("diamond", 0))), List.of()), "Empty offer");
        rejects(() -> plan(a, List.of(), List.of(new Offer<>(0, stack("diamond", 65)))), "Oversized incoming");
        rejects(() -> plan(a, java.util.Collections.nCopies(10, oa), List.of()), "Offer limit");
        var merge = empty(); merge.set(0, stack("diamond", 63));
        var merged = plan(merge, List.of(), List.of(oa));
        check(merged.get(0).count() == 64 && total(merged, "diamond") == 80, "Merge spillover");
        var named = plan(merge, List.of(), List.of(new Offer<>(0, stack("diamond:renamed", 17))));
        check(named.get(0).count() == 63 && named.get(1).identity().equals("diamond:renamed"), "Distinct components never merge");
        var tools = plan(empty(), List.of(), List.of(new Offer<>(0, new Stack<>("sword:enchanted", 1, 1))));
        check(tools.get(0).limit() == 1 && tools.get(0).count() == 1, "Unstackable limits");
        Random r = new Random(519);
        for (int i = 0; i < 100; i++) {
            int ca = r.nextInt(1, 65), cb = r.nextInt(1, 65);
            var ia = empty(); var ib = empty();
            ia.set(0, stack("diamond", ca)); ib.set(0, stack("diamond", cb));
            var ga = new Offer<>(0, stack("diamond", r.nextInt(1, ca + 1)));
            var gb = new Offer<>(0, stack("diamond", r.nextInt(1, cb + 1)));
            check(total(plan(ia, List.of(ga), List.of(gb)), "diamond") + total(plan(ib, List.of(gb), List.of(ga)), "diamond") == ca + cb, "Same-item conservation");
        }
    }
    private static void agreement() {
        var s = new TradeAgreement();
        check(!s.ready(100), "Unconfirmed cannot commit");
        check(s.confirm(0, 0, 5) && !s.ready(100), "One confirmation insufficient");
        check(s.confirm(1, 0, 10) && !s.ready(69) && s.ready(70), "Server countdown");
        check(!s.confirm(1, 0, 20) && s.deadline() == 70, "Duplicate confirmation idempotent");
        s.changed();
        check(s.revision() == 1 && !s.confirmed(0) && !s.confirmed(1) && !s.ready(100), "Change clears both");
        check(!s.confirm(0, 0, 100), "Stale confirmation rejected");
        check(s.confirm(1, 1, 110) && s.confirm(0, 1, 115) && !s.ready(174) && s.ready(175), "Fresh countdown");
        s.changed(); check(s.deadline() == 0 && !s.ready(1000), "Change invalidates commit");
    }
    private static void codec() {
        UUID id = UUID.randomUUID();
        for (var action : TradeActionPayload.Action.values()) {
            var p = new TradeActionPayload(id, 23, action, 35, 64, Long.MAX_VALUE);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                TradeActionPayload.STREAM_CODEC.encode(buffer, p);
                check(TradeActionPayload.STREAM_CODEC.decode(buffer).equals(p), "Action round trip " + action);
            } finally { buffer.release(); }
        }
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeUUID(id); buffer.writeVarInt(0); buffer.writeVarInt(999);
            try { TradeActionPayload.STREAM_CODEC.decode(buffer); throw new AssertionError("Invalid action accepted"); }
            catch (RuntimeException expected) { checks++; }
        } finally { buffer.release(); }
        for (int invalid : new int[] {-1, 10, Integer.MAX_VALUE}) {
            var input = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                input.writeUUID(id); input.writeVarInt(0); input.writeEnum(TradeStatePayload.Phase.EDITING);
                input.writeUtf("Partner", 16); input.writeBoolean(false); input.writeVarInt(invalid);
                rejects(() -> TradeStatePayload.STREAM_CODEC.decode(input), "Bounded state offers");
            } finally { input.release(); }
        }
        for (int invalid : new int[] {-1, 0, 37, Integer.MAX_VALUE}) {
            var input = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                input.writeUUID(id); input.writeVarInt(0); input.writeEnum(TradeStatePayload.Phase.EDITING);
                input.writeUtf("Partner", 16); input.writeBoolean(false); input.writeVarInt(0); input.writeVarInt(0);
                input.writeLong(0); input.writeLong(0); input.writeLong(0);
                input.writeBoolean(false); input.writeBoolean(false); input.writeVarInt(0); input.writeVarInt(invalid);
                rejects(() -> TradeStatePayload.STREAM_CODEC.decode(input), "Bounded state inventory");
            } finally { input.release(); }
        }
    }
    private static Stack<String> stack(String id, int count) { return new Stack<>(id, count, 64); }
    private static void slots() {
        for (ClickType type : ClickType.values()) {
            for (int cell = 0; cell < 18; cell++) {
                var decision = TradeSlotPolicy.click(cell, 0, type, 35, 54);
                check(decision != TradeSlotPolicy.Decision.VANILLA, "Preview never reaches vanilla mutation " + type);
                if (cell >= 9) check(decision == TradeSlotPolicy.Decision.REJECT, "Partner offer cannot be removed");
            }
        }
        check(TradeSlotPolicy.click(0, 0, ClickType.PICKUP, 35, 54) == TradeSlotPolicy.Decision.REMOVE_REFERENCE, "Local preview click removes reference only");
        check(TradeSlotPolicy.click(0, 0, ClickType.PICKUP, -1, 54) == TradeSlotPolicy.Decision.REJECT, "Empty preview rejected");
        check(TradeSlotPolicy.click(0, 0, ClickType.PICKUP, 36, 54) == TradeSlotPolicy.Decision.REJECT, "Invalid preview source rejected");
        check(TradeSlotPolicy.click(0, 99, ClickType.PICKUP, 0, 54) == TradeSlotPolicy.Decision.REJECT, "Malformed button rejected");
        for (ClickType type : ClickType.values()) {
            check(TradeSlotPolicy.click(18, 0, type, -1, 54) == TradeSlotPolicy.Decision.VANILLA, "Real inventory retains native input " + type);
        }
        check(TradeSlotPolicy.click(-999, 0, ClickType.PICKUP, -1, 54) == TradeSlotPolicy.Decision.VANILLA, "Native outside cursor handling");
        for (int index : new int[] {-42, 54, Integer.MAX_VALUE}) {
            check(TradeSlotPolicy.click(index, 0, ClickType.PICKUP, -1, 54) == TradeSlotPolicy.Decision.REJECT, "Invalid menu slot rejected");
        }
    }
    private static ArrayList<Stack<String>> empty() {
        var list = new ArrayList<Stack<String>>();
        for (int i = 0; i < 36; i++) list.add(new Stack<>(null, 0, 64));
        return list;
    }
    private static List<Stack<String>> plan(List<Stack<String>> src, List<Offer<String>> out, List<Offer<String>> in) {
        return TradeInventoryPlan.plan(src, out, in, String::equals);
    }
    private static int total(List<Stack<String>> inventory, String id) {
        return inventory.stream().filter(s -> id.equals(s.identity())).mapToInt(Stack::count).sum();
    }
    private static void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); checks++; }
    private static void rejects(Runnable op, String name) {
        try { op.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected rejection: " + name);
    }
}
