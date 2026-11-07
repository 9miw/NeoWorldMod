package com.ambition.neoworld.dungeon.stats;

import com.ambition.neoworld.dungeon.room.DungeonRoomType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-thread retry journal; acknowledged rows are already committed in the shared database. */
public final class DungeonResultOutbox extends SavedData {
    private static final Factory<DungeonResultOutbox> FACTORY = new Factory<>(DungeonResultOutbox::new, DungeonResultOutbox::load);
    private final Map<UUID, DungeonClear> pending = new LinkedHashMap<>();

    public static DungeonResultOutbox get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "neoworld_dungeon_results_pending");
    }

    public void add(DungeonClear clear) {
        var previous = pending.putIfAbsent(clear.runId(), clear);
        if (previous != null && !previous.equals(clear)) throw new IllegalArgumentException("Conflicting pending dungeon result");
        if (previous == null) setDirty();
    }

    public List<DungeonClear> pending(int limit) {
        return pending.values().stream().limit(limit).toList();
    }

    public void acknowledge(UUID runId) {
        if (pending.remove(runId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", 1);
        ListTag runs = new ListTag();
        for (var clear : pending.values()) {
            CompoundTag row = new CompoundTag();
            row.putUUID("run", clear.runId()); row.putString("dungeon", clear.dungeonId());
            row.putString("mode", clear.mode().name()); row.putLong("ticks", clear.clearTicks());
            row.putLong("completed_at_ms", clear.completedAtMillis());
            ListTag members = new ListTag();
            for (var member : clear.members()) {
                CompoundTag m = new CompoundTag(); m.putUUID("player", member.playerId());
                m.putBoolean("credited", member.credited()); members.add(m);
            }
            row.put("members", members); runs.add(row);
        }
        tag.put("runs", runs);
        return tag;
    }

    public static DungeonResultOutbox load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("version") != 1 || !tag.contains("runs", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Unsupported dungeon result outbox format");
        }
        var data = new DungeonResultOutbox();
        ListTag runs = (ListTag) tag.get("runs");
        if (!runs.isEmpty() && runs.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Invalid dungeon result list");
        }
        for (Tag entry : runs) {
            CompoundTag row = (CompoundTag) entry;
            if (!row.contains("ticks", Tag.TAG_LONG) || !row.contains("completed_at_ms", Tag.TAG_LONG)
                    || !row.contains("members", Tag.TAG_LIST)) {
                throw new IllegalArgumentException("Incomplete pending dungeon result");
            }
            var members = new ArrayList<DungeonClear.Member>();
            for (Tag member : row.getList("members", Tag.TAG_COMPOUND)) {
                var m = (CompoundTag) member;
                if (!m.contains("credited", Tag.TAG_BYTE)) throw new IllegalArgumentException("Missing dungeon credit flag");
                members.add(new DungeonClear.Member(m.getUUID("player"), m.getBoolean("credited")));
            }
            data.add(new DungeonClear(row.getUUID("run"), row.getString("dungeon"),
                    DungeonRoomType.valueOf(row.getString("mode")), row.getLong("ticks"),
                    row.getLong("completed_at_ms"), members));
        }
        return data;
    }
}
