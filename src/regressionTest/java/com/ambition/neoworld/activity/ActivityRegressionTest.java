package com.ambition.neoworld.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.mojang.serialization.JsonOps;

public final class ActivityRegressionTest {
    private static int checks;
    public static void main(String[] args) {
        var rewards = ActivityRewards.defaults();
        var data = new ActivityData();
        var beforeReset = Instant.parse("2026-10-13T20:59:59Z"); // 03:59:59 Bangkok
        var afterReset = Instant.parse("2026-10-13T21:00:00Z");
        check(ActivityData.activityDate(beforeReset).equals(LocalDate.of(2026, 10, 13)), "before 04:00 belongs to previous day");
        check(ActivityData.activityDate(afterReset).equals(LocalDate.of(2026, 10, 14)), "04:00 starts new day");
        check(ActivityData.nextReset(beforeReset) == afterReset.getEpochSecond(), "next reset honors Bangkok timezone");
        data.roll(beforeReset);
        check(data.checkIn(rewards.daily(), rewards.monthly()), "first daily check-in accepted");
        check(!data.checkIn(rewards.daily(), rewards.monthly()) && data.pending.size() == 2 && data.monthDays == 1,
                "repeated check-in cannot create duplicate daily or monthly reward");
        data.addOnlineSeconds(899, rewards.online());
        check(data.onlineMask == 0, "online reward cannot be claimed before threshold");
        data.addOnlineSeconds(1, rewards.online());
        check(data.onlineMask == 1 && data.pending.size() == 3, "threshold grants one entitlement");
        data.addOnlineSeconds(0, rewards.online());
        check(data.pending.size() == 3, "repeated sampling does not duplicate reward");
        data.addOnlineSeconds(900, rewards.online());
        var restored = ActivityData.CODEC.parse(JsonOps.INSTANCE,
                ActivityData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        check(restored.onlineSeconds == 1800 && restored.checkedIn && restored.monthDays == 1 && restored.pending.size() == 4,
                "reconnect/save round trip preserves eligibility and pending rewards");
        check(restored.pending.getFirst().reward().equals(rewards.daily()), "saved reward preserves original config snapshot");
        data.roll(afterReset);
        check(!data.checkedIn && data.onlineSeconds == 0 && data.onlineMask == 0 && data.monthDays == 1,
                "daily reset preserves monthly progress");
        check(data.pending.size() == 4, "full-inventory entitlements survive daily reset");
        data.roll(Instant.parse("2026-10-31T20:59:59Z"));
        check(data.monthDays == 1, "calendar first day before 04:00 still belongs to old month");
        data.roll(Instant.parse("2026-10-31T21:00:00Z"));
        check(data.monthDays == 0 && data.pending.size() == 4, "monthly reset preserves pending reward snapshots");
        check(!data.roll(beforeReset), "clock rollback cannot reopen older reward periods");
        for (int day = 0; day < 31; day++) {
            data.roll(Instant.parse("2026-10-31T21:00:00Z").plusSeconds(day * 86400L));
            data.checkIn(rewards.daily(), rewards.monthly());
        }
        long oldMonth = 2026 * 12L + 11;
        check(data.pending.stream().filter(p -> p.key().startsWith("monthly/" + oldMonth + "/")).count() == 28,
                "monthly calendar never grants more than 28 milestones");
        var saturating = new ActivityData(); saturating.roll(afterReset);
        saturating.addOnlineSeconds(Integer.MAX_VALUE, rewards.online());
        saturating.addOnlineSeconds(Integer.MAX_VALUE, rewards.online());
        check(saturating.onlineSeconds == 7200 && saturating.onlineMask == 15 && saturating.pending.size() == 4,
                "time saturation does not overflow or duplicate entitlements");
        check(ActivityReward.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{\"silver\":-1,\"items\":[]}")).error().isPresent(),
                "negative reward currency rejected");
        boolean invalid = false;
        try { saturating.addOnlineSeconds(-1, rewards.online()); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "negative online time rejected");
        var original = List.of(new Stack("stone", 63, 64), new Stack("dirt", 64, 64));
        var access = new ActivityInventoryPlan.StackAccess<Stack>() {
            public int count(Stack stack) { return stack.count(); }
            public int limit(Stack stack) { return stack.limit(); }
            public boolean same(Stack a, Stack b) { return a.item().equals(b.item()); }
            public Stack copy(Stack stack) { return new Stack(stack.item(), stack.count(), stack.limit()); }
            public Stack withCount(Stack stack, int count) { return new Stack(stack.item(), count, stack.limit()); }
        };
        check(ActivityInventoryPlan.plan(original, List.of(new Stack("stone", 2, 64)), 64, access).isEmpty(),
                "full inventory rejects whole reward instead of partial insertion");
        check(original.getFirst().count() == 63, "failed planning does not modify live inventory");
        var empty = new Stack("", 0, 64);
        var plan = ActivityInventoryPlan.plan(List.of(new Stack("stone", 63, 64), empty),
                List.of(new Stack("stone", 2, 64)), 64, access).orElseThrow();
        check(plan.getFirst().count() == 64 && plan.get(1).count() == 1, "reward merges stacks then uses empty slots");
        check(ActivityInventoryPlan.plan(List.of(empty), List.of(new Stack("potion", 2, 1)), 64, access).isEmpty(),
                "nonstackable rewards require one slot per item");
        check(ActivityInventoryPlan.plan(original, List.of(), 64, access).isPresent(), "money-only reward works with full inventory");
        check(ActivityInventoryPlan.plan(List.of(empty), List.of(new Stack("stone", 64, 64), new Stack("dirt", 1, 64)),
                64, access).isEmpty(), "multi-item rewards are rejected atomically when later items do not fit");
        check(ActivityInventoryPlan.plan(List.of(empty), List.of(new Stack("stone", 17, 64)), 16, access).isEmpty(),
                "inventory stack limits are honored");
        var request = new com.ambition.neoworld.network.activity.ActivityRequestPayload(java.util.UUID.randomUUID(),
                com.ambition.neoworld.network.activity.ActivityRequestPayload.Action.CLAIM, "online/20740/1", 2, 0);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                net.minecraft.core.RegistryAccess.EMPTY);
        try {
            com.ambition.neoworld.network.activity.ActivityRequestPayload.STREAM_CODEC.encode(buffer, request);
            check(com.ambition.neoworld.network.activity.ActivityRequestPayload.STREAM_CODEC.decode(buffer).equals(request),
                    "bounded request codec round trip preserves action, key and correlation ID");
        } finally { buffer.release(); }
        boolean rejected = false;
        try { new com.ambition.neoworld.network.activity.ActivityRequestPayload(request.requestId(), request.action(), "", -1, 0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid network tabs rejected");
        rejected = false;
        try { new com.ambition.neoworld.network.activity.ActivityRequestPayload(request.requestId(), request.action(), "x".repeat(97), 0, 0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "oversized reward keys rejected");
        System.out.println("Passed " + checks + " activity regression checks.");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private record Stack(String item, int count, int limit) {}
}
