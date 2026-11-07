package com.ambition.neoworld.network.dungeon;

import com.ambition.neoworld.dungeon.definition.DungeonLootRule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Per-preview-slot rules, never inferred from a generated ItemStack or an item ID. */
public record DungeonRewardDetail(boolean exact, double chance, int minimum, int maximum) {
    public static final DungeonRewardDetail UNKNOWN = new DungeonRewardDetail(false, 0, 0, 0);
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonRewardDetail> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeBoolean(p.exact); if (p.exact) { b.writeDouble(p.chance); b.writeVarInt(p.minimum); b.writeVarInt(p.maximum); } },
            b -> b.readBoolean() ? new DungeonRewardDetail(true,b.readDouble(),b.readVarInt(),b.readVarInt()) : UNKNOWN);

    public DungeonRewardDetail {
        if (!Double.isFinite(chance) || chance < 0 || chance > 100
                || (exact ? minimum < 1 || maximum < minimum : chance != 0 || minimum != 0 || maximum != 0))
            throw new IllegalArgumentException("Invalid reward preview detail");
    }

    public static DungeonRewardDetail preset(DungeonLootRule rule, int originalCount) {
        return new DungeonRewardDetail(true, rule.chance(), rule.minimum() == 0 ? originalCount : rule.minimum(),
                rule.maximum() == 0 ? originalCount : rule.maximum());
    }
}
