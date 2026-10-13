package com.ambition.neoworld.pvp.ranked;

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

/** Pending final results survive world saves and retry against the same match UUID. */
public final class PvpResultOutbox extends SavedData {
    private static final Factory<PvpResultOutbox> FACTORY=new Factory<>(PvpResultOutbox::new,PvpResultOutbox::load);
    private final Map<UUID,Result> pending=new LinkedHashMap<>();
    public record Result(UUID match,UUID a,UUID b,String nameA,String nameB,double score,String reason) { }
    public static PvpResultOutbox get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"neoworld_pvp_pending_results"); }
    public void add(RankedMatch match) {
        if (match.result==null) throw new IllegalArgumentException("Match is not complete");
        if (pending.putIfAbsent(match.id,new Result(match.id,match.a,match.b,match.nameA,match.nameB,match.result,match.reason))==null) setDirty();
    }
    public List<Result> all() { return List.copyOf(pending.values()); }
    public void saved(UUID match) { pending.remove(match); setDirty(); }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        ListTag entries=new ListTag();
        for (Result result:pending.values()) {
            CompoundTag entry=new CompoundTag(); entry.putUUID("match",result.match()); entry.putUUID("a",result.a()); entry.putUUID("b",result.b());
            entry.putString("nameA",result.nameA()); entry.putString("nameB",result.nameB()); entry.putDouble("score",result.score()); entry.putString("reason",result.reason()); entries.add(entry);
        }
        tag.put("results",entries); return tag;
    }
    public static PvpResultOutbox load(CompoundTag tag,HolderLookup.Provider registries) {
        var outbox=new PvpResultOutbox(); var entries=tag.getList("results",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++) {
            var entry=entries.getCompound(i);
            if(!entry.hasUUID("match") || !entry.hasUUID("a") || !entry.hasUUID("b")) continue;
            double score=entry.getDouble("score"); if(score!=0 && score!=0.5 && score!=1) continue;
            var result=new Result(entry.getUUID("match"),entry.getUUID("a"),entry.getUUID("b"),entry.getString("nameA"),entry.getString("nameB"),score,entry.getString("reason"));
            if(!result.a().equals(result.b())) outbox.pending.put(result.match(),result);
        }
        return outbox;
    }
}
