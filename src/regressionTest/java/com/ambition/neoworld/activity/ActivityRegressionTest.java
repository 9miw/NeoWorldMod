package com.ambition.neoworld.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.mojang.serialization.JsonOps;

public final class ActivityRegressionTest {
    private static int checks;
    public static void main(String[] args) {
        var rewards = ActivityRewards.defaults();
        check(rewards.daily().silver() == 0 && rewards.monthly().stream().allMatch(r -> r.silver() == 0)
                && rewards.online().stream().allMatch(r -> r.silver() == 0), "defaults never credit currency directly");
        var convertedMoney = new ActivityReward(100, List.of()).itemRewards();
        check(convertedMoney.silver() == 0 && convertedMoney.items().size() == 1
                && convertedMoney.items().getFirst().item().equals("neoworld:silver_coin")
                && convertedMoney.items().getFirst().count() == 1 && convertedMoney.items().getFirst().coinValue() == 100,
                "legacy direct silver converts to one valued coin, without duplicate credit");
        check(convertedMoney.itemRewards().equals(convertedMoney), "repeated coin conversion is idempotent");
        var restoredCoin = ActivityReward.CODEC.parse(JsonOps.INSTANCE,
                ActivityReward.CODEC.encodeStart(JsonOps.INSTANCE, convertedMoney).getOrThrow()).getOrThrow();
        check(restoredCoin.equals(convertedMoney), "coin value survives config and pending-reward serialization");
        var plainCoin = ActivityReward.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"items\":[{\"item\":\"neoworld:diamond_coin\",\"count\":5}]}")).getOrThrow();
        check(plainCoin.silver() == 0 && plainCoin.items().getFirst().coinValue() == 1, "item-only config defaults to unit coin value");
        check(ActivityReward.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"items\":[{\"item\":\"neoworld:silver_coin\",\"count\":1,\"coinValue\":0}]}" )).error().isPresent(),
                "zero or negative coin values cannot be configured");
        var snapshotId = java.util.UUID.randomUUID();
        var snapshot = new com.ambition.neoworld.network.activity.ActivitySnapshotProgress(snapshotId);
        check(!snapshot.accept(java.util.UUID.randomUUID(), 0, 0, 1), "stale snapshot response rejected");
        snapshot.accept(snapshotId, 0, 0, 1); snapshot.accept(snapshotId, 1, 0, 30);
        snapshot.accept(snapshotId, 2, 0, 4); snapshot.accept(snapshotId, 3, 0, 13);
        snapshot.accept(snapshotId, 3, 2, 13);
        check(!snapshot.complete(), "snapshot waits for every inbox page before exposing cached tabs");
        snapshot.accept(snapshotId, 3, 0, 13);
        check(!snapshot.complete(), "duplicate response cannot count as a missing page");
        snapshot.accept(snapshotId, 3, 1, 13);
        check(snapshot.complete(), "out-of-order pages complete a single opening snapshot");
        check(!snapshot.accept(snapshotId, 3, 3, 13), "page beyond inbox total rejected");
        var emptySnapshot = new com.ambition.neoworld.network.activity.ActivitySnapshotProgress(snapshotId);
        for (int tab = 0; tab < 4; tab++) emptySnapshot.accept(snapshotId, tab, 0, 0);
        check(emptySnapshot.complete(), "empty inbox snapshot completes without retry or polling");
        var stone = new ActivityReward.ItemGrant("neoworld:upgrade_stone", 2, "");
        check(new ActivityReward(0, List.of(stone)).isBundle() == false, "one item kind with multiple quantity stays an item");
        check(new ActivityReward(100, List.of(stone)).isBundle(), "currency and item show a reward box");
        check(new ActivityReward(0, List.of(stone, stone)).isBundle() == false, "duplicate entries of the same item kind stay one item preview");
        check(new ActivityReward(0, List.of(stone, new ActivityReward.ItemGrant("minecraft:diamond", 1, ""))).isBundle(),
                "different item kinds show a reward box");
        var data = new ActivityData();
        var beforeReset = Instant.parse("2026-10-13T20:59:59Z"); // 03:59:59 Bangkok
        var afterReset = Instant.parse("2026-10-13T21:00:00Z");
        check(ActivityData.activityDate(beforeReset).equals(LocalDate.of(2026, 10, 13)), "before 04:00 belongs to previous day");
        check(ActivityData.activityDate(afterReset).equals(LocalDate.of(2026, 10, 14)), "04:00 starts new day");
        check(ActivityData.nextReset(beforeReset) == afterReset.getEpochSecond(), "next reset honors Bangkok timezone");
        data.roll(beforeReset);
        check(data.checkIn(rewards.daily()), "first daily check-in accepted");
        check(!data.checkIn(rewards.daily()) && data.pending.size() == 1 && data.monthDays == 0,
                "daily check-in does not auto-grant a monthly reward");
        check(data.monthlyStatus(12) == 3 && data.monthlyStatus(13) == 1 && data.monthlyStatus(14) == 0,
                "only current calendar day is claimable; past expired and future locked");
        check(!data.canClaimMonthly(data.monthlyKey(12)) && !data.canClaimMonthly(data.monthlyKey(14)),
                "forged requests for past/future dates rejected");
        check(data.canClaimMonthly(data.monthlyKey(13)), "today can be retried before successful delivery");
        check(data.markMonthlyClaimed(data.monthlyKey(13)) && !data.markMonthlyClaimed(data.monthlyKey(13)),
                "monthly reward can only be marked once");
        data.addOnlineSeconds(899, rewards.online());
        check(data.onlineMask == 0, "online reward cannot be claimed before threshold");
        data.addOnlineSeconds(1, rewards.online());
        check(data.onlineMask == 1 && data.pending.size() == 2, "threshold grants one entitlement");
        data.addOnlineSeconds(0, rewards.online());
        check(data.pending.size() == 2, "repeated sampling does not duplicate reward");
        data.addOnlineSeconds(900, rewards.online());
        var restored = ActivityData.CODEC.parse(JsonOps.INSTANCE,
                ActivityData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        check(restored.onlineSeconds == 1800 && restored.checkedIn && restored.monthDays == 1 && restored.pending.size() == 3
                        && restored.monthlyStatus(13) == 2,
                "reconnect/save round trip preserves eligibility and pending rewards");
        check(restored.pending.getFirst().reward().equals(rewards.daily()), "saved reward preserves original config snapshot");
        data.roll(afterReset);
        check(!data.checkedIn && data.onlineSeconds == 0 && data.onlineMask == 0 && data.monthDays == 1,
                "daily reset preserves monthly progress");
        check(data.pending.size() == 3, "nonmonthly entitlements survive daily reset");
        check(data.monthlyStatus(13) == 2 && data.monthlyStatus(14) == 1, "new day opens only its own slot");
        data.roll(Instant.parse("2026-10-31T20:59:59Z"));
        check(data.monthDays == 1, "calendar first day before 04:00 still belongs to old month");
        data.roll(Instant.parse("2026-10-31T21:00:00Z"));
        check(data.monthDays == 0 && data.pending.size() == 3, "monthly reset preserves nonmonthly pending rewards");
        check(!data.roll(beforeReset), "clock rollback cannot reopen older reward periods");
        for (int day = 0; day < 31; day++) {
            data.roll(Instant.parse("2026-10-31T21:00:00Z").plusSeconds(day * 86400L));
            data.checkIn(rewards.daily());
            data.markMonthlyClaimed(data.monthlyKey(data.currentMonthDay()));
        }
        check(data.pending.stream().noneMatch(p -> p.key().startsWith("monthly/")), "calendar rewards cannot bypass expiration through the inbox");
        var calendar = new ActivityData();
        calendar.roll(Instant.parse("2026-10-29T21:00:00Z"));
        check(calendar.monthlyStatus(30) == 1 && calendar.canClaimMonthly(calendar.monthlyKey(30)), "day 30 is claimable on the 30th");
        calendar.roll(Instant.parse("2026-10-30T21:00:00Z"));
        check(calendar.monthlyStatus(30) == 3 && !calendar.canClaimMonthly(calendar.monthlyKey(30))
                && !calendar.canClaimMonthly(calendar.monthlyKey(31)), "day 31 cannot claim day 30 or invent a 31st slot");
        calendar.roll(Instant.parse("2027-02-27T21:00:00Z"));
        check(calendar.monthlyStatus(28) == 1 && calendar.monthlyStatus(29) == 0 && calendar.monthlyStatus(30) == 0,
                "February does not unlock nonexistent dates");
        var legacy = ActivityData.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"day\":" + beforeReset.atZone(ActivityData.ZONE).minusHours(4).toLocalDate().toEpochDay()
                        + ",\"month\":" + (2026 * 12 + 10) + ",\"month_days\":3}")).getOrThrow();
        check(legacy.monthlyStatus(1) == 2 && legacy.monthlyStatus(3) == 2, "legacy redeemed milestones remain blocked against duplicates");
        var legacyConfig = rewards.monthly().subList(0, 28);
        var upgraded = ActivityRewards.upgradeMonthly(legacyConfig);
        check(upgraded.size() == 30 && upgraded.subList(0, 28).equals(legacyConfig), "28-day custom config upgraded without replacing old rewards");
        calendar.pending.add(new ActivityReward.Pending("monthly/old/1", "old", rewards.daily()));
        calendar.roll(Instant.parse("2027-02-27T21:00:00Z"));
        check(calendar.pending.isEmpty(), "legacy monthly inbox entries cannot be claimed after calendar migration");
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
