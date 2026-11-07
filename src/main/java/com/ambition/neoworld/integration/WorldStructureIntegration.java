package com.ambition.neoworld.integration;

import com.ambition.neoworld.dungeon.definition.DungeonDefinitionData;
import com.ambition.neoworld.dungeon.instance.DungeonRuntime;
import com.ambition.neoworld.pvp.arena.PvpArenaData;
import com.ambition.neoworld.pvp.room.PvpRoomManager;
import com.ambition.neoworld.world.structure.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Feature adapters: marker/gameplay rules never enter the shared structure engine. */
public final class WorldStructureIntegration {
    private static final Set<MinecraftServer> READY = Collections.newSetFromMap(new WeakHashMap<>());
    private WorldStructureIntegration() {}
    public static void ensure(MinecraftServer server) {
        if(READY.contains(server))return;
        var catalog=StructureCatalogData.get(server);
        var dungeon=DungeonDefinitionData.get(server);var pvp=PvpArenaData.get(server);
        Map<ResourceLocation,List<StructurePiece>> oldDungeon=new LinkedHashMap<>(),oldPvp=new LinkedHashMap<>();
        dungeon.legacyMaps().forEach((id,pieces) -> oldDungeon.put(id,DungeonDefinitionData.worldPieces(pieces)));
        pvp.legacyMaps().forEach((id,pieces) -> oldPvp.put(id,PvpArenaData.worldPieces(pieces)));
        catalog.importLegacy("dungeon",oldDungeon);catalog.importLegacy("pvp",oldPvp);
        dungeon.attachStructureCatalog(catalog);pvp.attachStructureCatalog(catalog);
        StructureConsumers.registerAccess(server,com.ambition.neoworld.dungeon.structure.DungeonCaptureService::allowed);
        StructureConsumers.register(server,new StructureConsumers.Consumer() {
            public String checkUpdate(List<StructurePiece> pieces) {
                var original=DungeonDefinitionData.dungeonPieces(pieces);var users=dungeon.mapUsers(original);
                if(users.stream().anyMatch(d -> !d.structurePieces().equals(original)))return "มีดันใช้บางส่วนของแผนที่นี้ กรุณาเปลี่ยนการเชื่อมก่อนอัปเดต";
                if(users.stream().anyMatch(d -> DungeonRuntime.get(server).activeInstances().stream().anyMatch(run -> run.dungeonId().equals(d.id()))))
                    return "แผนที่มีดันกำลังใช้งาน กรุณารอให้จบรอบก่อน";
                return null;
            }
            public String checkDelete(List<StructurePiece> pieces) {
                return dungeon.mapUsers(DungeonDefinitionData.dungeonPieces(pieces)).isEmpty()?null:"ยังมีดันเชื่อมกับแผนที่นี้";
            }
            public void replaced(List<StructurePiece> original,List<StructurePiece> replacement) {
                dungeon.relinkStructures(DungeonDefinitionData.dungeonPieces(original),DungeonDefinitionData.dungeonPieces(replacement));
            }
        });
        StructureConsumers.register(server,new StructureConsumers.Consumer() {
            private List<String> users(List<StructurePiece> pieces) {
                var ids=pieces.stream().map(StructurePiece::templateId).collect(java.util.stream.Collectors.toSet());
                return pvp.all().entrySet().stream().filter(e -> e.getValue().pieces().stream().anyMatch(p -> ids.contains(p.template())))
                        .map(Map.Entry::getKey).toList();
            }
            public String checkUpdate(List<StructurePiece> pieces) {
                for(String id:users(pieces)) {
                    if(!PvpArenaData.worldPieces(pvp.pieces(id)).equals(pieces))return "สนามใช้บางส่วนของแมพ กรุณาเปลี่ยนการเชื่อมก่อน";
                    if(PvpRoomManager.get(server).arenaInUse(id))return "สนามกำลังมีผู้เล่น: "+id;
                }
                return null;
            }
            public String checkReplacement(List<StructurePiece> original,List<StructurePiece> replacement) {
                String error=checkUpdate(original);if(error!=null)return error;
                if(!users(original).isEmpty())try { PvpArenaData.arenaPieces(replacement); }
                catch(IllegalArgumentException exception) { return "ขนาดแผนที่เกินขอบเขต Structure ของสนาม PvP"; }
                return null;
            }
            public String checkDelete(List<StructurePiece> pieces) { return users(pieces).isEmpty()?null:"ยังมีสนาม PvP เชื่อมกับแผนที่นี้"; }
            public void replaced(List<StructurePiece> original,List<StructurePiece> replacement) {
                if(!users(original).isEmpty())pvp.relinkStructures(PvpArenaData.arenaPieces(original),PvpArenaData.arenaPieces(replacement));
            }
        });
        StructureConsumers.activate(server);READY.add(server);
    }
    public static void stop(MinecraftServer server) { READY.remove(server);StructureConsumers.clear(server); }
}
