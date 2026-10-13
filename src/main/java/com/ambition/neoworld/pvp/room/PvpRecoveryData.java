package com.ambition.neoworld.pvp.room;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable return locations and reserved regions; rooms themselves are transient. */
public final class PvpRecoveryData extends SavedData {
    private static final Factory<PvpRecoveryData> FACTORY = new Factory<>(PvpRecoveryData::new, PvpRecoveryData::load);
    private final Map<UUID, CompoundTag> returns = new LinkedHashMap<>();
    private final Map<Integer, Region> regions = new LinkedHashMap<>();
    public record Region(BlockPos origin, int x, int y, int z) { }
    public static PvpRecoveryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "neoworld_pvp_recovery");
    }
    public Map<Integer, Region> regions() { return Map.copyOf(regions); }
    public void reserve(int slot, Region region) {
        if (!validRegion(slot, region) || regions.containsKey(slot)) throw new IllegalArgumentException("Invalid or occupied PvP slot");
        regions.put(slot, region); setDirty();
    }
    public void release(int slot) { regions.remove(slot); setDirty(); }
    public CompoundTag point(UUID player) { return returns.get(player); }
    public void capture(ServerPlayer player) {
        if (returns.containsKey(player.getUUID())) throw new IllegalArgumentException("A return location is still pending");
        CompoundTag point = new CompoundTag();
        point.putString("dimension", player.serverLevel().dimension().location().toString());
        point.putDouble("x", player.getX()); point.putDouble("y", player.getY()); point.putDouble("z", player.getZ());
        point.putFloat("yaw", player.getYRot()); point.putFloat("pitch", player.getXRot());
        point.putFloat("health", player.getHealth());
        point.putInt("gameMode",player.gameMode.getGameModeForPlayer().getId());
        returns.put(player.getUUID(), point); setDirty();
    }
    public void recovered(UUID player) { returns.remove(player); setDirty(); }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        returns.forEach((id, point) -> { CompoundTag entry = point.copy(); entry.putUUID("player", id); players.add(entry); });
        tag.put("returns", players);
        ListTag slots = new ListTag();
        regions.forEach((id, region) -> {
            CompoundTag entry = new CompoundTag(); entry.putInt("slot", id);
            entry.putLong("origin", region.origin().asLong());
            entry.putInt("x", region.x()); entry.putInt("y", region.y()); entry.putInt("z", region.z()); slots.add(entry);
        });
        tag.put("regions", slots); return tag;
    }
    public static PvpRecoveryData load(CompoundTag tag, HolderLookup.Provider registries) {
        PvpRecoveryData data = new PvpRecoveryData();
        ListTag players = tag.getList("returns", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            if (entry.hasUUID("player")) data.returns.put(entry.getUUID("player"), entry.copy());
        }
        ListTag slots = tag.getList("regions", Tag.TAG_COMPOUND);
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag entry = slots.getCompound(i);
            int x = entry.getInt("x"), y = entry.getInt("y"), z = entry.getInt("z");
            int slot = entry.getInt("slot");
            Region region = new Region(BlockPos.of(entry.getLong("origin")), x, y, z);
            if (validRegion(slot, region)) data.regions.put(slot, region);
        }
        return data;
    }
    private static boolean validRegion(int slot, Region region) {
        return slot >= 0 && slot < 128 && region.origin().equals(new BlockPos(1_000_000 + slot*1024, 32, 1_000_000))
                && region.x() > 0 && region.x() <= 512 && region.y() > 0 && region.y() <= 224
                && region.z() > 0 && region.z() <= 512 && (long)region.x()*region.y()*region.z() <= 262144;
    }
}
