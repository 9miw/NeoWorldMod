package com.ambition.neoworld.network.lootbox;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.ambition.neoworld.lootbox.LootBoxRules;

/** Server-owned result; receiving it has no gameplay side effects. */
public record LootBoxOpenedPayload(UUID opening, boolean automatic, List<ItemStack> rewards) implements CustomPacketPayload {
    public LootBoxOpenedPayload(List<ItemStack> rewards) { this(UUID.randomUUID(), false, rewards); }
    public LootBoxOpenedPayload {
        if (rewards.size() > LootBoxRules.MAX_REWARDS || rewards.stream().anyMatch(ItemStack::isEmpty))
            throw new IllegalArgumentException("Invalid reward list");
        rewards = rewards.stream().map(ItemStack::copy).toList();
    }
    public static final Type<LootBoxOpenedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "loot_box_opened"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LootBoxOpenedPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                buffer.writeUUID(payload.opening()); buffer.writeBoolean(payload.automatic());
                buffer.writeVarInt(payload.rewards().size());
                for (var reward : payload.rewards()) ItemStack.STREAM_CODEC.encode(buffer, reward);
            }, buffer -> {
                UUID opening = buffer.readUUID(); boolean automatic = buffer.readBoolean();
                int count = buffer.readVarInt();
                if (count < 0 || count > LootBoxRules.MAX_REWARDS) throw new IllegalArgumentException("Invalid reward count");
                List<ItemStack> rewards = new ArrayList<>();
                for (int i = 0; i < count; i++) rewards.add(ItemStack.STREAM_CODEC.decode(buffer));
                return new LootBoxOpenedPayload(opening, automatic, rewards);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
