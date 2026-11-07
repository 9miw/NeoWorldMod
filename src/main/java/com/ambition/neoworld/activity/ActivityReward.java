package com.ambition.neoworld.activity;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Immutable reward snapshots keep earned rewards stable when configuration changes. */
public record ActivityReward(long silver, List<ItemGrant> items) {
    public static final Codec<ActivityReward> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.validate(value -> value >= 0 && value <= 1_000_000_000L
                    ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "Silver must be in 0..1000000000"))
                    .optionalFieldOf("silver", 0L).forGetter(ActivityReward::silver),
            ItemGrant.CODEC.listOf().fieldOf("items").forGetter(ActivityReward::items)
    ).apply(i, ActivityReward::new));
    public ActivityReward { items = List.copyOf(items); }
    /** Legacy direct money becomes an ordinary coin stack; the economy owns its conversion. */
    public ActivityReward itemRewards() {
        if (silver == 0) return this;
        var converted = new java.util.ArrayList<>(items);
        converted.add(new ItemGrant("neoworld:silver_coin", 1, "", silver));
        return new ActivityReward(0, converted);
    }
    public boolean isBundle() {
        long kinds = items.stream().map(item -> item.item() + "|" + item.potion() + "|" + item.coinValue()).distinct().count();
        return kinds + (silver > 0 ? 1 : 0) > 1;
    }
    public record ItemGrant(String item, int count, String potion, long coinValue) {
        public ItemGrant(String item, int count, String potion) { this(item, count, potion, 1L); }
        public static final Codec<ItemGrant> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("item").forGetter(ItemGrant::item),
                Codec.intRange(1, 256).fieldOf("count").forGetter(ItemGrant::count),
                Codec.STRING.optionalFieldOf("potion", "").forGetter(ItemGrant::potion),
                Codec.LONG.validate(value -> value > 0 && value <= 1_000_000_000L
                        ? com.mojang.serialization.DataResult.success(value)
                        : com.mojang.serialization.DataResult.error(() -> "coinValue must be in 1..1000000000"))
                        .optionalFieldOf("coinValue", 1L).forGetter(ItemGrant::coinValue)
        ).apply(i, ItemGrant::new));
    }
    public record Pending(String key, String label, ActivityReward reward) {
        public static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(Pending::key),
                Codec.STRING.fieldOf("label").forGetter(Pending::label),
                ActivityReward.CODEC.fieldOf("reward").forGetter(Pending::reward)
        ).apply(i, Pending::new));
    }
}
