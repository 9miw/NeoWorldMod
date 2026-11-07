package com.ambition.neoworld.lootbox;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** World-owned definitions. ItemStack serialization preserves all reward components. */
public final class LootBoxDefinitions extends SavedData {
    private static final Factory<LootBoxDefinitions> FACTORY = new Factory<>(LootBoxDefinitions::new, LootBoxDefinitions::load);
    private final Map<String, List<Entry>> definitions = new LinkedHashMap<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private CompoundTag blockedOriginal;
    public record Entry(ItemStack item, double chance) {
        public Entry {
            LootBoxRules.validateChance(chance);
            if (item.isEmpty() || item.getCount() > item.getMaxStackSize())
                throw new IllegalArgumentException("รางวัลต้องเป็นไอเทม 1 stack ที่ถูกต้อง");
            item = item.copy();
        }
        @Override public ItemStack item() { return item.copy(); }
    }
    public static LootBoxDefinitions get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "neoworld_loot_boxes");
    }
    private void requireHealthy() {
        if (blockedOriginal != null) throw new IllegalArgumentException("ข้อมูลกล่องสุ่มเสียหาย กรุณาซ่อม neoworld_loot_boxes.dat ก่อนตั้งค่าหรือเปิดกล่อง");
    }
    public Set<String> ids() { requireHealthy(); return Set.copyOf(definitions.keySet()); }
    public List<Entry> entries(String id) {
        requireHealthy();
        LootBoxRules.validateId(id);
        var entries = definitions.get(id);
        if (entries == null) throw new IllegalArgumentException("ไม่พบกล่อง: " + id);
        return entries;
    }
    public void create(String id) {
        requireHealthy();
        LootBoxRules.validateId(id);
        if (definitions.containsKey(id)) throw new IllegalArgumentException("ID กล่องนี้มีอยู่แล้ว");
        definitions.put(id, List.of()); counts.put(id, 1); setDirty();
    }
    public int count(String id) { entries(id); return counts.getOrDefault(id, 1); }
    public void setCount(String id, int count) {
        LootBoxRules.validateCount(entries(id).stream().map(Entry::chance).toList(), count);
        counts.put(id, count); setDirty();
    }
    public void add(String id, ItemStack item, double chance) {
        var current = entries(id);
        if (current.size() >= LootBoxRules.MAX_REWARDS) throw new IllegalArgumentException("กล่องรองรับไม่เกิน 9 รางวัล");
        if (current.stream().anyMatch(entry -> ItemStack.isSameItemSameComponents(entry.item(), item)))
            throw new IllegalArgumentException("มีไอเทมพร้อมข้อมูลนี้แล้ว ลบรายการเดิมก่อนเพิ่มใหม่");
        var next = new ArrayList<>(current);
        next.add(new Entry(item, chance));
        definitions.put(id, List.copyOf(next)); setDirty();
    }
    public void remove(String id, int index) {
        var current = entries(id);
        if (index < 1 || index > current.size()) throw new IllegalArgumentException("ไม่พบลำดับรางวัล");
        var next = new ArrayList<>(current); next.remove(index - 1);
        definitions.put(id, List.copyOf(next)); setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (blockedOriginal != null) return blockedOriginal.copy();
        var boxes = new CompoundTag();
        definitions.forEach((id, entries) -> {
            var list = new ListTag();
            for (var entry : entries) {
                var value = new CompoundTag();
                value.put("item", entry.item().save(registries));
                value.putDouble("chance", entry.chance()); list.add(value);
            }
            boxes.put(id, list);
        });
        var draws = new CompoundTag(); counts.forEach(draws::putInt);
        tag.putInt("format", 2); tag.put("boxes", boxes); tag.put("counts", draws); return tag;
    }
    public static LootBoxDefinitions load(CompoundTag tag, HolderLookup.Provider registries) {
        try { return loadValid(tag, registries); }
        catch (IllegalArgumentException | IllegalStateException invalid) {
            // A thrown loader error makes vanilla fall back to an empty writable catalog.
            var blocked = new LootBoxDefinitions(); blocked.blockedOriginal = tag.copy();
            com.ambition.neoworld.NeoWorld.LOGGER.error("Loot box definitions disabled; original data preserved: {}", invalid.getMessage());
            return blocked;
        }
    }
    private static LootBoxDefinitions loadValid(CompoundTag tag, HolderLookup.Provider registries) {
        int format = tag.getInt("format");
        if ((format != 1 && format != 2) || !tag.contains("boxes", Tag.TAG_COMPOUND)
                || (format == 2 && !tag.contains("counts", Tag.TAG_COMPOUND)))
            throw new IllegalStateException("Invalid loot box definitions; preserve the original save for repair");
        var data = new LootBoxDefinitions(); var boxes = tag.getCompound("boxes");
        for (String id : boxes.getAllKeys()) {
            data.create(id);
            if (!(boxes.get(id) instanceof ListTag list) || list.size() > LootBoxRules.MAX_REWARDS
                    || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND))
                throw new IllegalStateException("Invalid reward list: " + id);
            for (int i = 0; i < list.size(); i++) {
                var entry = list.getCompound(i);
                if (!entry.contains("item", Tag.TAG_COMPOUND) || !entry.contains("chance", Tag.TAG_DOUBLE))
                    throw new IllegalStateException("Invalid saved reward: " + id);
                var item = ItemStack.parse(registries, entry.getCompound("item"))
                        .orElseThrow(() -> new IllegalStateException("Missing reward item: " + id));
                data.add(id, item, entry.getDouble("chance"));
            }
            if (format == 2) {
                var draws = tag.getCompound("counts");
                if (!draws.contains(id, Tag.TAG_INT) || draws.getInt(id) < 1 || draws.getInt(id) > LootBoxRules.MAX_REWARDS)
                    throw new IllegalStateException("Invalid saved draw count: " + id);
                // Editing may temporarily leave fewer candidates; opening rejects until repaired.
                data.counts.put(id, draws.getInt(id));
            }
        }
        data.setDirty(false); return data;
    }
}
