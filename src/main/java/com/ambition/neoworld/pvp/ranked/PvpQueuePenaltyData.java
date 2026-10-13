package com.ambition.neoworld.pvp.ranked;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Wall-clock bans survive relogging and normal world saves/restarts. */
public final class PvpQueuePenaltyData extends SavedData {
    private static final Factory<PvpQueuePenaltyData> FACTORY=new Factory<>(PvpQueuePenaltyData::new,PvpQueuePenaltyData::load);
    private final Map<UUID,Long> until=new HashMap<>();
    public static PvpQueuePenaltyData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"neoworld_pvp_queue_penalties"); }
    public void penalize(UUID player,long now) { until.put(player,now+60_000); setDirty(); }
    public int remaining(UUID player,long now) {
        long deadline=until.getOrDefault(player,0L);
        if(deadline<=now) { if(until.remove(player)!=null) setDirty(); return 0; }
        return (int)Math.min(Integer.MAX_VALUE,(deadline-now+999)/1000);
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        CompoundTag entries=new CompoundTag(); until.forEach((id,end) -> entries.putLong(id.toString(),end)); tag.put("penalties",entries); return tag;
    }
    public static PvpQueuePenaltyData load(CompoundTag tag,HolderLookup.Provider registries) {
        var data=new PvpQueuePenaltyData(); var entries=tag.getCompound("penalties");
        for(String id:entries.getAllKeys()) { try { data.until.put(UUID.fromString(id),entries.getLong(id)); } catch(IllegalArgumentException ignored) { } }
        return data;
    }
}
