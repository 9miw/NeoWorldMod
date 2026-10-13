package com.ambition.neoworld.activity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.ambition.neoworld.activity.ActivityReward.Pending;
import com.ambition.neoworld.economy.PlayerEconomyManager;
import com.ambition.neoworld.network.activity.ActivityRequestPayload;
import com.ambition.neoworld.network.activity.ActivityStatePayload;
import com.ambition.neoworld.network.activity.ActivityStatePayload.Entry;
import com.ambition.neoworld.registry.ModAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.network.PacketDistributor;

/** Every mutation runs on the server thread and uses the acting player from the network context. */
public final class ActivityService {
    private ActivityService() {}
    public static List<ItemStack> stacks(ActivityReward reward) {
        var result = new ArrayList<ItemStack>();
        for (var grant : reward.items()) {
            var id = ResourceLocation.parse(grant.item());
            if (!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR)
                throw new IllegalArgumentException("Unknown reward item: " + id);
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(id), grant.count());
            if (!grant.potion().isEmpty()) {
                if (stack.getItem() != Items.POTION && stack.getItem() != Items.SPLASH_POTION && stack.getItem() != Items.LINGERING_POTION)
                    throw new IllegalArgumentException("Potion contents require a potion item");
                var key = ResourceKey.create(Registries.POTION, ResourceLocation.parse(grant.potion()));
                var potion = BuiltInRegistries.POTION.getHolder(key).orElseThrow(() -> new IllegalArgumentException("Unknown potion: " + key));
                stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
            }
            result.add(stack);
        }
        return result;
    }
    public static void validate(ActivityRewards rewards) {
        stacks(rewards.daily()); rewards.monthly().forEach(ActivityService::stacks); rewards.online().forEach(ActivityService::stacks);
    }
    public static void handle(ServerPlayer player, ActivityRequestPayload request) {
        var runtime = ActivityEvents.runtime(player.getServer());
        if (runtime == null || !runtime.allowRequest(player)) return;
        var rewards = runtime.rewards;
        var data = player.getData(ModAttachments.ACTIVITY_DATA);
        var now = Instant.now();
        String message = "";
        boolean current = data.roll(now);
        if (request.action() != ActivityRequestPayload.Action.QUERY && (!player.isAlive() || player.isSpectator())) {
            send(player, request, data, rewards, now, runtime.isAfk(player), "รับรางวัลได้เมื่อมีชีวิตและไม่ได้อยู่โหมด Spectator");
            return;
        }
        if (!current && request.action() != ActivityRequestPayload.Action.QUERY) message = "เวลาเซิร์ฟเวอร์ย้อนหลัง กรุณารอรอบปัจจุบัน";
        else if (request.action() != ActivityRequestPayload.Action.QUERY) {
            if (request.action() == ActivityRequestPayload.Action.CHECK_IN || request.action() == ActivityRequestPayload.Action.CLAIM_ALL)
                data.checkIn(rewards.daily(), rewards.monthly());
            int claimed = 0;
            var targets = new ArrayList<Pending>();
            for (var pending : data.pending) {
                if (request.action() == ActivityRequestPayload.Action.CLAIM_ALL
                        || request.action() == ActivityRequestPayload.Action.CHECK_IN && pending.key().equals("daily/" + data.day)
                        || request.action() == ActivityRequestPayload.Action.CLAIM && pending.key().equals(request.key())) targets.add(pending);
            }
            for (var pending : targets) {
                if (grant(player, pending.reward())) { data.pending.remove(pending); claimed++; }
            }
            message = claimed > 0 ? "รับแล้ว " + claimed + " รายการ" : targets.isEmpty() ? "ไม่มีรางวัลที่รับได้"
                    : "รับไม่ได้ ตรวจพื้นที่กระเป๋า ยอดเงิน และไอเทมรางวัล • สิทธิ์ยังค้างรับ";
            if (claimed > 0 && claimed < targets.size()) message += " • ที่เหลือยังค้างรับ";
            // Inventory, balance and claim markers share the normal player save/logout lifecycle.
        }
        send(player, request, data, rewards, now, runtime.isAfk(player), message);
    }
    private static boolean grant(ServerPlayer player, ActivityReward reward) {
        var money = player.getData(ModAttachments.PLAYER_DATA);
        if (money.getSilver() > Long.MAX_VALUE - reward.silver()) return false;
        List<ItemStack> items;
        try { items = stacks(reward); }
        catch (IllegalArgumentException exception) { return false; } // Keep old snapshots if their mod/item was removed.
        var inventory = player.getInventory();
        var plan = ActivityInventoryPlan.plan(inventory.items, items, inventory.getMaxStackSize());
        if (plan.isEmpty()) return false;
        if (reward.silver() > 0 && !money.addSilver(reward.silver())) return false;
        for (int i = 0; i < inventory.items.size(); i++) inventory.items.set(i, plan.get().get(i));
        inventory.setChanged(); player.inventoryMenu.broadcastChanges(); player.containerMenu.broadcastChanges();
        if (reward.silver() > 0) PlayerEconomyManager.syncToClient(player);
        return true;
    }
    private static void send(ServerPlayer player, ActivityRequestPayload request, ActivityData data,
            ActivityRewards rewards, Instant now, boolean afk, String message) {
        var rows = new ArrayList<Entry>();
        switch (request.tab()) {
            case 0 -> rows.add(entry(data, "daily/" + data.day, "เช็กอินรายวัน", rewards.daily(), data.checkedIn,
                    data.checkedIn ? 2 : 1));
            case 1 -> {
                for (int i = 0; i < 28; i++) rows.add(entry(data, "monthly/" + data.month + "/" + (i + 1),
                        "วันที่ " + (i + 1) + ((i + 1) % 7 == 0 ? " • โบนัส" : ""), rewards.monthly().get(i),
                        data.monthDays > i, data.monthDays > i ? 2 : 0));
            }
            case 2 -> {
                for (int i = 0; i < 4; i++) rows.add(entry(data, "online/" + data.day + "/" + i,
                        "ออนไลน์ " + ActivityData.ONLINE_MINUTES[i] + " นาที", rewards.online().get(i),
                        (data.onlineMask & (1 << i)) != 0, 0));
            }
            case 3 -> {} // Build previews only for the requested inbox page, including on long-running servers.
        }
        int total = request.tab() == 3 ? data.pending.size() : rows.size();
        int page = Math.min(request.page(), Math.max(0, (total - 1) / ActivityStatePayload.PAGE_SIZE));
        int start = page * ActivityStatePayload.PAGE_SIZE;
        List<Entry> displayed;
        if (request.tab() == 3) {
            for (var pending : data.pending.subList(start, Math.min(total, start + ActivityStatePayload.PAGE_SIZE)))
                rows.add(preview(pending.key(), pending.label(), pending.reward(), 1));
            displayed = rows;
        } else displayed = rows.subList(start, Math.min(total, start + ActivityStatePayload.PAGE_SIZE));
        PacketDistributor.sendToPlayer(player, new ActivityStatePayload(request.requestId(), request.tab(), page, total,
                data.monthDays, data.onlineSeconds, ActivityData.nextReset(now), afk, data.checkedIn, data.pending.size(), message, displayed));
    }
    private static Entry entry(ActivityData data, String key, String label, ActivityReward reward, boolean earned, int status) {
        var pending = data.pending.stream().filter(p -> p.key().equals(key)).findFirst();
        return pending.map(p -> preview(key, label, p.reward(), 1)).orElseGet(() -> preview(key, label, reward, earned ? 2 : status));
    }
    private static Entry preview(String key, String label, ActivityReward reward, int status) {
        String text = reward.silver() > 0 ? reward.silver() + " Silver" : "";
        ItemStack icon = ItemStack.EMPTY;
        try {
            var stacks = stacks(reward);
            if (!stacks.isEmpty()) icon = stacks.getFirst().copyWithCount(1);
            for (var stack : stacks) text += (text.isEmpty() ? "" : ", ") + stack.getHoverName().getString() + " x" + stack.getCount();
        } catch (IllegalArgumentException exception) { text += " [ไอเทมไม่พร้อมใช้งาน]"; }
        if (text.length() > 512) text = text.substring(0, 509) + "...";
        return new Entry(key, label, text, status, icon);
    }
}
