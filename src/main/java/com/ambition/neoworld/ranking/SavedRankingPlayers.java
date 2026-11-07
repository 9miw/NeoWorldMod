package com.ambition.neoworld.ranking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.ambition.neoworld.data.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;

/** Read-only offline snapshot. Never loads an entity or writes a player save. */
final class SavedRankingPlayers {
    private SavedRankingPlayers() { }
    static Map<UUID,RankingTable.Entry> read(Path directory) throws IOException {
        var result=new HashMap<UUID,RankingTable.Entry>();
        if(!Files.exists(directory)) return Map.of();
        try(var paths=Files.list(directory)) {
            for(Path path:paths.filter(p -> p.getFileName().toString().endsWith(".dat")).toList()) {
                String name=path.getFileName().toString(); UUID id;
                try { id=UUID.fromString(name.substring(0,name.length()-4)); }
                catch(IllegalArgumentException ignored) { continue; }
                result.put(id,entry(id,NbtIo.readCompressed(path,NbtAccounter.create(64L*1024*1024))));
            }
        }
        return Map.copyOf(result);
    }
    static RankingTable.Entry entry(UUID id,CompoundTag player) {
        var attachments=player.getCompound("neoforge:attachments");
        var tag=attachments.getCompound("neoworld:player_data");
        PlayerData data=PlayerData.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();
        // Corrupt negative currency/EXP must not silently produce a successful partial table.
        if(tag.getLong("silver")<0 || tag.getLong("exp")<0) throw new IllegalArgumentException("Invalid saved ranking values");
        return new RankingTable.Entry(id,"ผู้เล่น "+id.toString().substring(0,8),data.getLevel(),data.getExp(),data.getSilver(),0,0,0,data.getTotalExp());
    }

    static RankingAppearance appearance(Path file,net.minecraft.core.HolderLookup.Provider registries) throws IOException {
        if(!Files.exists(file)) return RankingAppearance.EMPTY;
        var attachments=NbtIo.readCompressed(file,NbtAccounter.create(64L*1024*1024)).getCompound("neoforge:attachments");
        if(!attachments.contains("neoworld:ranking_appearance",10)) return RankingAppearance.EMPTY;
        return RankingAppearance.CODEC.parse(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE,registries),
                attachments.getCompound("neoworld:ranking_appearance")).getOrThrow();
    }
}
