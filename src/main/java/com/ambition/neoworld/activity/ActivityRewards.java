package com.ambition.neoworld.activity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.neoforged.fml.loading.FMLPaths;

/** Reloaded only at server startup, never while a claim is being processed. */
public record ActivityRewards(ActivityReward daily, List<ActivityReward> monthly,
        List<ActivityReward> online, int afkSeconds) {
    public ActivityRewards { monthly = List.copyOf(monthly); online = List.copyOf(online); }
    public static ActivityRewards defaults() {
        var heal = new ActivityReward.ItemGrant("minecraft:potion", 1, "minecraft:healing");
        var stone = new ActivityReward.ItemGrant("neoworld:upgrade_stone", 1, "");
        var monthly = new ArrayList<ActivityReward>();
        for (int day = 1; day <= 28; day++) {
            monthly.add(new ActivityReward(day % 7 == 0 ? day * 100L : 50,
                    day % 7 == 0 ? List.of(new ActivityReward.ItemGrant(stone.item(), day / 7, "")) : List.of()));
        }
        return new ActivityRewards(new ActivityReward(100, List.of(heal)), monthly,
                List.of(new ActivityReward(0, List.of(heal)), new ActivityReward(100, List.of()),
                        new ActivityReward(0, List.of(stone)), new ActivityReward(300, List.of(stone, heal))), 300);
    }
    public static Path path() { return FMLPaths.CONFIGDIR.get().resolve("neoworld/activity-rewards.json"); }
    public static ActivityRewards load() throws java.io.IOException {
        var path = path();
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(toJson(defaults())),
                    StandardCharsets.UTF_8);
        }
        var json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        var daily = parse(json.getAsJsonObject("daily"));
        var monthly = new ArrayList<ActivityReward>();
        var online = new ArrayList<ActivityReward>();
        for (var value : json.getAsJsonArray("monthly")) monthly.add(parse(value.getAsJsonObject()));
        for (var value : json.getAsJsonArray("online")) online.add(parse(value.getAsJsonObject()));
        int afk = com.mojang.serialization.Codec.intRange(1, 3600).parse(JsonOps.INSTANCE, json.get("afkSeconds")).getOrThrow();
        if (monthly.size() != 28 || online.size() != 4 || afk < 1 || afk > 3600)
            throw new IllegalArgumentException("Expected 28 monthly, 4 online rewards and afkSeconds in 1..3600");
        return new ActivityRewards(daily, monthly, online, afk);
    }
    public static JsonObject toJson(ActivityRewards rewards) {
        var json = new JsonObject();
        json.add("daily", ActivityReward.CODEC.encodeStart(JsonOps.INSTANCE, rewards.daily()).getOrThrow());
        json.add("monthly", ActivityReward.CODEC.listOf().encodeStart(JsonOps.INSTANCE, rewards.monthly()).getOrThrow());
        json.add("online", ActivityReward.CODEC.listOf().encodeStart(JsonOps.INSTANCE, rewards.online()).getOrThrow());
        json.addProperty("afkSeconds", rewards.afkSeconds());
        return json;
    }
    private static ActivityReward parse(JsonObject json) {
        var reward = ActivityReward.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        if (reward.items().size() > 16 || reward.silver() == 0 && reward.items().isEmpty())
            throw new IllegalArgumentException("Reward must be nonempty and have at most 16 item entries");
        return reward;
    }
}
