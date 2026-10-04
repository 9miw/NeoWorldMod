package com.ambition.neoworld.network;

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

public record DungeonBrowserDataPayload(List<Entry> entries) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 256;
    public static final Type<DungeonBrowserDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "dungeon_browser_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonBrowserDataPayload> STREAM_CODEC =
            StreamCodec.of(DungeonBrowserDataPayload::encode, DungeonBrowserDataPayload::decode);

    public DungeonBrowserDataPayload {
        entries = List.copyOf(entries);
    }

    public static DungeonBrowserDataPayload fromDefinitions(Iterable<DungeonDefinition> definitions) {
        List<Entry> result = new ArrayList<>();
        for (DungeonDefinition definition : definitions) {
            if (definition.status() != DungeonDefinitionStatus.ENABLED) continue;
            int modes = 0;
            if (definition.supports(DungeonRoomType.SOLO)) modes |= 1;
            if (definition.supports(DungeonRoomType.TRIO)) modes |= 2;
            if (definition.supports(DungeonRoomType.RAID)) modes |= 4;
            result.add(new Entry(
                    definition.id(), definition.displayName(), definition.minimumLevel(),
                    definition.timeLimitSeconds(), modes, definition.monsterWaves().size(),
                    definition.boss() == null ? "" : definition.boss().cloneName(),
                    definition.structureId() == null ? "" : definition.structureId().toString(),
                    definition.reward() == null || definition.reward().lootTableId() == null
                            ? "" : definition.reward().lootTableId().toString(),
                    definition.reward() == null ? 0 : definition.reward().experience(),
                    definition.reward() == null ? 0L : definition.reward().money()));
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
        }
    }

    private static DungeonBrowserDataPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid dungeon count: " + size);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buffer.readUtf(64), buffer.readUtf(128), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readUnsignedByte(), buffer.readVarInt(), buffer.readUtf(128),
                    buffer.readUtf(256), buffer.readUtf(256), buffer.readVarInt(), buffer.readVarLong()));
        }
        return new DungeonBrowserDataPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(String id, String displayName, int minimumLevel, int timeLimitSeconds,
                        int modeMask, int waveCount, String bossName, String structureId,
                        String lootTableId, int experience, long money) {
    }
}
