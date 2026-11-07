package com.ambition.neoworld.network.dungeon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.dungeon.definition.DungeonDefinition;
import com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus;
import com.ambition.neoworld.dungeon.room.DungeonRoomType;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import com.ambition.neoworld.dungeon.definition.DungeonLootPreview;

public record DungeonBrowserDataPayload(List<Entry> entries) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 256;
    public static final Type<DungeonBrowserDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "dungeon_browser_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonBrowserDataPayload> STREAM_CODEC =
            StreamCodec.of(DungeonBrowserDataPayload::encode, DungeonBrowserDataPayload::decode);

    public DungeonBrowserDataPayload {
        entries = List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES || entries.stream().mapToInt(e -> e.rewardItems().size()).sum() > 1024)
            throw new IllegalArgumentException("Dungeon preview packet too large");
    }

    public static DungeonBrowserDataPayload fromDefinitions(MinecraftServer server, Iterable<DungeonDefinition> definitions) {
        List<Entry> result = new ArrayList<>();
        int budget = 1024;
        int presetByteBudget = 131072;
        var previews = new java.util.HashMap<ResourceLocation, DungeonLootPreview.Preview>();
        for (DungeonDefinition definition : definitions) {
            if (definition.status() != DungeonDefinitionStatus.ENABLED) continue;
            if (result.size() >= MAX_ENTRIES) break;
            var reward = definition.reward();
            var preview = reward == null || reward.lootTableId() == null || budget == 0
                    ? new DungeonLootPreview.Preview(List.of(), budget == 0)
                    : previews.computeIfAbsent(reward.lootTableId(), id -> DungeonLootPreview.read(server, id));
            List<DungeonRewardDetail> details = null;
            if (reward != null && reward.presetId() != null && budget > 0) {
                List<ItemStack> savedItems = new ArrayList<>();
                details = new ArrayList<>();
                boolean incomplete = false;
                try {
                    var preset = com.ambition.neoworld.dungeon.definition.DungeonDefinitionData.get(server)
                            .lootPresetItems(reward.presetId(), server.registryAccess());
                    for (int slot = 0; slot < preset.size(); slot++) {
                        ItemStack stack = preset.get(slot);
                        if (stack.isEmpty()) continue;
                        var rule = com.ambition.neoworld.dungeon.definition.DungeonDefinitionData.get(server).lootRule(reward.presetId(), slot);
                        if (rule.chance() == 0) continue;
                        var detail = DungeonRewardDetail.preset(rule, stack.getCount());
                        incomplete |= rule.chance() < 100 || rule.minimum() != rule.maximum();
                        if (rule.maximum() > 0) stack = stack.copyWithCount(rule.maximum());
                        var bytes = new java.io.ByteArrayOutputStream();
                        var encoded = new net.minecraft.nbt.CompoundTag();
                        encoded.put("item", stack.saveOptional(server.registryAccess()));
                        net.minecraft.nbt.NbtIo.write(encoded, new java.io.DataOutputStream(bytes));
                        if (bytes.size() > 8192 || bytes.size() > presetByteBudget) { incomplete = true; continue; }
                        presetByteBudget -= bytes.size();
                        savedItems.add(stack.copy());
                        details.add(detail);
                    }
                } catch (IllegalArgumentException | java.io.IOException exception) { incomplete = true; }
                preview = new DungeonLootPreview.Preview(savedItems, incomplete);
            }
            List<ItemStack> items = preview.items().stream().limit(budget).toList();
            List<DungeonRewardDetail> itemDetails = details == null
                    ? java.util.Collections.nCopies(items.size(), DungeonRewardDetail.UNKNOWN)
                    : List.copyOf(details.subList(0, items.size()));
            boolean partial = preview.incomplete() || items.size() < preview.items().size();
            budget -= items.size();
            int modes = 0;
            if (definition.supports(DungeonRoomType.SOLO)) modes |= 1;
            if (definition.supports(DungeonRoomType.TRIO)) modes |= 2;
            if (definition.supports(DungeonRoomType.RAID)) modes |= 4;
            result.add(new Entry(
                    definition.id(), definition.displayName(), definition.minimumLevel(),
                    definition.timeLimitSeconds(), modes, definition.monsterWaves().size(),
                    definition.boss() == null ? "" : definition.boss().cloneName(),
                    definition.structureId() == null ? "" : definition.structureId().toString(),
                    reward == null ? "" : reward.sourceId().toString(),
                    0, 0L, items, partial,
                    definition.imageId() == null ? "" : definition.imageId().toString(), itemDetails));
        }
        result.sort(Comparator.comparingInt(Entry::minimumLevel).thenComparing(Entry::displayName));
        return new DungeonBrowserDataPayload(result);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, DungeonBrowserDataPayload payload) {
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeUtf(entry.id, 64);
            buffer.writeUtf(entry.displayName, 128);
            buffer.writeVarInt(entry.minimumLevel);
            buffer.writeVarInt(entry.timeLimitSeconds);
            buffer.writeByte(entry.modeMask);
            buffer.writeVarInt(entry.waveCount);
            buffer.writeUtf(entry.bossName, 128);
            buffer.writeUtf(entry.structureId, 256);
            buffer.writeUtf(entry.lootTableId, 256);
            buffer.writeVarInt(entry.experience);
            buffer.writeVarLong(entry.money);
            buffer.writeVarInt(entry.rewardItems.size());
            for (ItemStack stack : entry.rewardItems) ItemStack.STREAM_CODEC.encode(buffer, stack);
            buffer.writeBoolean(entry.previewIncomplete);
            buffer.writeUtf(entry.imageId, 256);
            for (DungeonRewardDetail detail : entry.rewardDetails) DungeonRewardDetail.STREAM_CODEC.encode(buffer, detail);
        }
    }

    private static DungeonBrowserDataPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid dungeon count: " + size);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Entry entry = new Entry(buffer.readUtf(64), buffer.readUtf(128), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readUnsignedByte(), buffer.readVarInt(), buffer.readUtf(128),
                    buffer.readUtf(256), buffer.readUtf(256), buffer.readVarInt(), buffer.readVarLong(),
                    readItems(buffer), buffer.readBoolean(), buffer.readUtf(256));
            List<DungeonRewardDetail> details = new ArrayList<>(entry.rewardItems.size());
            for (int slot=0; slot<entry.rewardItems.size(); slot++) details.add(DungeonRewardDetail.STREAM_CODEC.decode(buffer));
            entries.add(new Entry(entry.id, entry.displayName, entry.minimumLevel, entry.timeLimitSeconds,
                    entry.modeMask, entry.waveCount, entry.bossName, entry.structureId, entry.lootTableId,
                    entry.experience, entry.money, entry.rewardItems, entry.previewIncomplete, entry.imageId, details));
        }
        return new DungeonBrowserDataPayload(entries);
    }

    private static List<ItemStack> readItems(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > DungeonLootPreview.MAX_ITEMS) throw new IllegalArgumentException("Invalid reward preview size");
        List<ItemStack> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) items.add(ItemStack.STREAM_CODEC.decode(buffer));
        return items;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(String id, String displayName, int minimumLevel, int timeLimitSeconds,
                        int modeMask, int waveCount, String bossName, String structureId,
                        String lootTableId, int experience, long money, List<ItemStack> rewardItems, boolean previewIncomplete,
                        String imageId, List<DungeonRewardDetail> rewardDetails) {
        public Entry(String id, String displayName, int minimumLevel, int timeLimitSeconds,
                     int modeMask, int waveCount, String bossName, String structureId, String lootTableId,
                     int experience, long money, List<ItemStack> rewardItems, boolean previewIncomplete, String imageId) {
            this(id, displayName, minimumLevel, timeLimitSeconds, modeMask, waveCount, bossName, structureId,
                    lootTableId, experience, money, rewardItems, previewIncomplete, imageId,
                    java.util.Collections.nCopies(rewardItems.size(), DungeonRewardDetail.UNKNOWN));
        }
        public Entry {
            if (imageId.length() > 256) throw new IllegalArgumentException("Image resource too long");
            if (rewardItems.size() > DungeonLootPreview.MAX_ITEMS) throw new IllegalArgumentException("Too many reward previews");
            rewardItems = rewardItems.stream().map(ItemStack::copy).toList();
            rewardDetails = List.copyOf(rewardDetails);
            if (rewardDetails.size() != rewardItems.size()) throw new IllegalArgumentException("Reward detail/slot mismatch");
        }
    }
}
