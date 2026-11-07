package com.ambition.neoworld.world;

import com.ambition.neoworld.world.structure.*;
import com.ambition.neoworld.dungeon.definition.*;
import com.ambition.neoworld.dungeon.structure.DungeonTemplatePiece;
import com.ambition.neoworld.pvp.arena.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class WorldStructureRegressionTest {
    public static void run() {
        migration();consumers();commands();partition();
        System.out.println("World structure catalog, legacy migration, consumer adapters and command regression checks passed");
    }
    private static List<StructurePiece> pieces(String id) { return List.of(new StructurePiece(ResourceLocation.parse(id),BlockPos.ZERO)); }
    private static void migration() {
        var d=new DungeonDefinitionData();var p=new PvpArenaData();var catalog=new StructureCatalogData();
        var dungeonName=ResourceLocation.parse("neoworld:dungeon/mine");var pvpName=ResourceLocation.parse("neoworld:pvp/arena");
        var a=pieces("neoworld:dungeon/mine_01");var b=pieces("neoworld:pvp/arena_01");
        d.addMap(dungeonName,DungeonDefinitionData.dungeonPieces(a));p.addMap(pvpName,PvpArenaData.arenaPieces(b));
        var legacyD=d.save(new CompoundTag(),null);var legacyP=p.save(new CompoundTag(),null);
        catalog.importLegacy("dungeon",Map.of(dungeonName,a));catalog.importLegacy("pvp",Map.of(pvpName,b));
        check(d.save(new CompoundTag(),null).equals(legacyD) && p.save(new CompoundTag(),null).equals(legacyP),
                "Import must not alter the original feature saved data");
        d.attachStructureCatalog(catalog);p.attachStructureCatalog(catalog);
        check(d.map(pvpName).orElseThrow().equals(DungeonDefinitionData.dungeonPieces(b)),"Dungeon can link a central PvP-compatible map");
        check(p.map(dungeonName).equals(PvpArenaData.arenaPieces(a)),"PvP can link a central dungeon-compatible map");
        var fresh=ResourceLocation.parse("test:shared/new_map");d.addMap(fresh,DungeonDefinitionData.dungeonPieces(a));
        check(p.map(fresh).equals(PvpArenaData.arenaPieces(a)),"Feature adapters read one authoritative catalog");
        var reloaded=StructureCatalogData.load(catalog.save(new CompoundTag(),null),null);
        check(reloaded.map(dungeonName).orElseThrow().equals(a),"Namespaced IDs and offsets survive central reload");
        reloaded.removeMap(dungeonName,a);reloaded=StructureCatalogData.load(reloaded.save(new CompoundTag(),null),null);
        reloaded.importLegacy("dungeon",Map.of(dungeonName,a));
        check(reloaded.map(dungeonName).isEmpty(),"Once-migrated legacy data cannot resurrect deleted maps");
        var conflict=new StructureCatalogData();conflict.addMap(dungeonName,b);
        rejects(() -> conflict.importLegacy("dungeon",Map.of(dungeonName,a,fresh,a)));
        check(conflict.map(fresh).isEmpty() && conflict.map(dungeonName).orElseThrow().equals(b),"Conflicting migration is non-destructive");
        conflict.importLegacy("dungeon",Map.of(dungeonName,b,fresh,a));
        check(conflict.map(fresh).isPresent(),"Rejected migrations do not mark their source completed");
        var definition=d.create("mine").orElseThrow();d.updateStructurePieces(definition,DungeonDefinitionData.dungeonPieces(a));
        d.updateStatus(definition,DungeonDefinitionStatus.ENABLED);
        p.create("arena");p.update("arena",p.get("arena"),PvpArenaData.arenaPieces(a));p.validated("arena");
        var replacement=pieces("test:shared/revision_01");var backup=ResourceLocation.parse("test:shared/backup");
        catalog.replaceMap(dungeonName,a,replacement,backup);
        d.relinkStructures(DungeonDefinitionData.dungeonPieces(a),DungeonDefinitionData.dungeonPieces(replacement));
        p.relinkStructures(PvpArenaData.arenaPieces(a),PvpArenaData.arenaPieces(replacement));
        check(definition.status()==DungeonDefinitionStatus.DRAFT && definition.structurePieces().equals(DungeonDefinitionData.dungeonPieces(replacement)),
                "Dungeon relinking resets readiness while retaining the new layout");
        check(!p.get("arena").validated() && p.pieces("arena").equals(PvpArenaData.arenaPieces(replacement)),
                "PvP relinking resets validation for the same shared revision");
        check(catalog.map(backup).orElseThrow().equals(a),"The previous template layout remains recoverable");
        rejects(() -> catalog.replaceMap(dungeonName,a,replacement,ResourceLocation.parse("test:stale")));
        var mutable=new BlockPos.MutableBlockPos(1,2,3);var piece=new StructurePiece(ResourceLocation.parse("test:map"),mutable);
        mutable.set(7,8,9);check(piece.offset().equals(new BlockPos(1,2,3)),"Mutable caller coordinates cannot alter catalog pieces");
        var generic=ResourceLocation.parse("test:too_big_for_pvp");catalog.addMap(generic,List.of(new StructurePiece(ResourceLocation.parse("test:large"),new BlockPos(600,0,0))));
        check(p.map(generic)==null && !p.mapIds().contains(generic),"PvP filters incompatible generic maps without losing them from World");
    }
    private static void consumers() {
        var a=pieces("test:map_01");var b=pieces("test:map/rev_01");var calls=new ArrayList<String>();
        StructureConsumers.register(null,new StructureConsumers.Consumer() {
            public String checkUpdate(List<StructurePiece> pieces) { return "active instance"; }
            public String checkDelete(List<StructurePiece> pieces) { return "linked feature"; }
            public void replaced(List<StructurePiece> old,List<StructurePiece> next) { calls.add("one"); }
        });
        StructureConsumers.register(null,new StructureConsumers.Consumer() {
            public String checkUpdate(List<StructurePiece> pieces) { return null; }
            public String checkDelete(List<StructurePiece> pieces) { return null; }
            public void replaced(List<StructurePiece> old,List<StructurePiece> next) { calls.add("two"); }
        });
        try {
            check(StructureConsumers.checkUpdate(null,a)!=null && StructureConsumers.checkDelete(null,a)!=null,
                    "Any linked consumer can veto unsafe update/deletion");
            StructureConsumers.replaced(null,a,b);check(calls.equals(List.of("one","two")),"All registered features receive accepted replacement layouts");
        } finally { StructureConsumers.clear(null); }
        check(StructureConsumers.checkUpdate(null,a)==null,"Lifecycle cleanup releases shared consumer references");
    }
    private static void commands() {
        var root=com.ambition.neoworld.command.WorldManagementCommand.buildCommand().build();
        var structure=root.getChild("structure");
        check(structure!=null && root.getChild("admin").getChild("structure")!=null,"World exposes direct and admin structure routes");
        for(String command:List.of("tool","pos1","pos2","selection","cancel","scan","save","update","list","delete","place"))
            check(structure.getChild(command)!=null,"Missing shared command: "+command);
        check(structure.getChild("delete").getChild("name").getChild("confirm")!=null,"Shared deletion keeps player-bound confirmation");
        check(structure.getChild("place").getChild("name").getCommand()==null && structure.getChild("place").getChild("name").getChild("confirm")!=null,
                "World placement requires explicit confirmation before replacing blocks");
        check(StructureCaptureService.mapId("mine").toString().equals("neoworld:structures/mine"),"World names default to a generic folder");
        check(com.ambition.neoworld.dungeon.structure.DungeonCaptureService.mapId("mine").toString().equals("neoworld:dungeon/mine"),"Legacy Dungeon short IDs are preserved");
        check(PvpCaptureService.mapId("mine").toString().equals("neoworld:pvp/mine"),"Legacy PvP short IDs are preserved");
        check(StructureCaptureService.mapId("other:maps/mine").toString().equals("other:maps/mine"),"Explicit namespaces remain untouched");
    }
    private static void partition() {
        var id=ResourceLocation.parse("test:generic/map");
        var plan=StructureCapturePlan.create(new BlockPos(100,80,-50),new BlockPos(4,31,-2),id);
        var old=com.ambition.neoworld.dungeon.structure.DungeonCapturePlan.create(new BlockPos(100,80,-50),new BlockPos(4,31,-2),id);
        check(plan.volume()==old.volume() && plan.parts().size()==old.parts().size(),"Shared partitioning retains legacy geometry");
        for(int i=0;i<plan.parts().size();i++)check(plan.parts().get(i).offset().equals(old.parts().get(i).offset())
                && plan.parts().get(i).id().equals(old.parts().get(i).id()),"Template names and offsets do not change during extraction");
        check(new StructureLayout("generic",pieces("test:map"),Set.of()).requiredMarkers().isEmpty(),"World never requires Dungeon or PvP markers");
        rejects(() -> new StructureLayout("large",Collections.nCopies(513,new StructurePiece(id,BlockPos.ZERO)),Set.of()));
        var malformed=new CompoundTag();malformed.putString("maps","not a map");
        rejects(() -> StructureCatalogData.load(malformed,null));
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run(); } catch(IllegalArgumentException|IllegalStateException expected) { return; }throw new AssertionError("Expected rejection"); }
}
