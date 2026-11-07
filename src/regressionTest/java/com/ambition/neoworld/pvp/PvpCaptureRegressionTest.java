package com.ambition.neoworld.pvp;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import com.ambition.neoworld.pvp.arena.PvpArenaData;
import com.ambition.neoworld.pvp.arena.PvpArenaGeometry;
import com.ambition.neoworld.pvp.arena.PvpArenaPiece;
import com.ambition.neoworld.pvp.arena.PvpCapturePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class PvpCaptureRegressionTest {
    public static void run() {
        var name=ResourceLocation.parse("neoworld:pvp/arena_1");
        var one=PvpCapturePlan.create(BlockPos.ZERO,BlockPos.ZERO,name);
        check(one.volume()==1 && one.parts().size()==1 && one.parts().getFirst().id().toString().endsWith("arena_1_01"),"One-block selections are inclusive and PvP-namespaced");
        var exact=PvpCapturePlan.create(BlockPos.ZERO,new BlockPos(47,47,47),name);
        check(exact.parts().size()==1 && exact.volume()==48L*48*48,"48-cube selection has no extra pieces");
        var edge=PvpCapturePlan.create(BlockPos.ZERO,new BlockPos(48,2,2),name);
        check(edge.parts().size()==2 && edge.parts().get(1).offset().getX()==48 && edge.parts().get(1).size().getX()==1,"Partial final piece retains its edge block");
        var plan=PvpCapturePlan.create(new BlockPos(100,80,-50),new BlockPos(4,31,-2),name);
        check(plan.equals(PvpCapturePlan.create(new BlockPos(4,31,-2),new BlockPos(100,80,-50),name)),"Corner order does not change saved placement");
        var boxes=plan.parts().stream().map(p -> new PvpArenaGeometry.Part(p.offset(),p.size())).toList();
        check(PvpArenaGeometry.size(boxes).equals(new BlockPos(97,50,49)) && plan.parts().size()==12,"Pieces reconstruct full arena dimensions without overlap");
        var names=new HashSet<ResourceLocation>(); long volume=0;
        for(var part:plan.parts()) {
            check(names.add(part.id()) && part.origin().subtract(plan.minimum()).equals(part.offset()),"Piece IDs and offsets are stable");
            check(part.size().getX()<=48 && part.size().getY()<=48 && part.size().getZ()<=48,"Pieces fit vanilla structure dimensions");
            var last=part.origin().offset(part.size()).offset(-1,-1,-1);
            check(last.getX()<=plan.maximum().getX() && last.getY()<=plan.maximum().getY() && last.getZ()<=plan.maximum().getZ(),"Edge pieces remain inside selection");
            volume+=(long)part.size().getX()*part.size().getY()*part.size().getZ();
        }
        check(volume==plan.volume() && volume==97L*50*49,"Partition preserves every selected block");
        reject(() -> PvpCapturePlan.create(BlockPos.ZERO,new BlockPos(511,223,511),name));
        reject(() -> PvpCapturePlan.create(BlockPos.ZERO,new BlockPos(512,0,0),name));
        reject(() -> PvpCapturePlan.create(new BlockPos(Integer.MIN_VALUE,0,0),new BlockPos(Integer.MAX_VALUE,0,0),name));
        reject(() -> PvpCapturePlan.create(BlockPos.ZERO,BlockPos.ZERO,ResourceLocation.parse("test:../escape")));
        reject(() -> PvpCapturePlan.create(BlockPos.ZERO,BlockPos.ZERO,ResourceLocation.parse("test:arena.nbt")));
        reject(() -> PvpArenaGeometry.size(List.of(boxes.getFirst(),boxes.getFirst())));
        var size=new BlockPos(97,50,49);
        PvpArenaGeometry.markers(size,Map.of("pvp_spawn_a",new BlockPos(47,1,2),"pvp_spawn_b",new BlockPos(48,48,2)));
        reject(() -> PvpArenaGeometry.markers(size,Map.of("pvp_spawn_a",new BlockPos(1,1,1))));
        reject(() -> PvpArenaGeometry.markers(size,Map.of("pvp_spawn_a",new BlockPos(1,0,1),"pvp_spawn_b",new BlockPos(2,1,1))));
        reject(() -> PvpArenaGeometry.markers(size,Map.of("pvp_spawn_a",new BlockPos(1,1,1),"pvp_spawn_b",new BlockPos(2,49,1))));

        PvpMapCatalogRegressionTest.run();
        var legacy=new CompoundTag(); var entries=new CompoundTag(); entries.putString("arena_1",name.toString()); legacy.put("arenas",entries);
        var data=PvpArenaData.load(legacy,null);
        var original=data.get("arena_1");
        check(original.pieces().equals(List.of(new PvpArenaPiece(name,BlockPos.ZERO))),"Legacy single-template arenas migrate at zero offset");
        check(data.create("draft") && data.pieces("draft").isEmpty(),"New arenas start empty and can be excluded from play");
        check(!data.create("draft") && !data.create("../invalid"),"Duplicate and unsafe arena IDs are rejected");
        var pieces=plan.parts().stream().map(p -> new PvpArenaPiece(p.id(),p.offset())).toList();
        data.update("arena_1",original,pieces);
        reject(() -> data.update("arena_1",original,List.of(new PvpArenaPiece(name,BlockPos.ZERO))));
        check(data.pieces("arena_1").equals(pieces),"A stale capture cannot overwrite a newer definition");
        reject(() -> data.update("arena_1",data.get("arena_1"),List.of()));
        var loaded=PvpArenaData.load(data.save(new CompoundTag(),null),null);
        check(loaded.pieces("arena_1").equals(pieces) && loaded.get("draft")!=null && loaded.pieces("draft").isEmpty(),"Multipart offsets and empty drafts survive restart");
        reject(() -> new PvpArenaPiece(name,new BlockPos(-1,0,0)));
        permissions();
        System.out.println("PvP capture, multipart geometry, migration and command permission regression checks passed");
    }
    private static void permissions() {
        var root=com.ambition.neoworld.command.pvp.PvpArenaCommand.buildCommand().build();
        check(root.getChild("arena")==null && root.getChild("admin")!=null,"Old admin path is removed");
        var ordinary=new net.minecraft.commands.CommandSourceStack(net.minecraft.commands.CommandSource.NULL,net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec2.ZERO,null,0,"Test",net.minecraft.network.chat.Component.literal("Test"),null,null);
        var operator=ordinary.withPermission(2);
        check(root.canUse(ordinary) && !root.getChild("admin").canUse(ordinary) && root.getChild("admin").canUse(operator),"Only admin subtree requires OP level 2");
        var structure=root.getChild("admin").getChild("structure");
        check(structure.getChild("tool")!=null && structure.getChild("save")!=null && structure.getChild("cancel")!=null,"Capture commands exist under admin");
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        dispatcher.getRoot().addChild(root);
        for(var command:List.of("pvp admin delete arena_1","pvp admin structure save map_one","pvp admin structure save neoworld:pvp/map_one",
                "pvp admin structure update neoworld:pvp/map_one","pvp admin structure delete map_one confirm token",
                "pvp admin set arena_1 structure neoworld:pvp/map_one")) {
            var parsed=dispatcher.parse(command,operator);
            check(!parsed.getReader().canRead() && parsed.getContext().getCommand()!=null,"Catalog command parses: "+command);
        }
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid capture/layout accepted");
    }
}
