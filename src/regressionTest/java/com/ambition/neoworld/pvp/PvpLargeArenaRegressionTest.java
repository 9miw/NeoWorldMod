package com.ambition.neoworld.pvp;

import java.util.List;
import com.ambition.neoworld.pvp.arena.PvpCapturePlan;
import com.ambition.neoworld.pvp.arena.PvpArenaGeometry;
import com.ambition.neoworld.pvp.arena.PvpPlacementCursor;
import com.ambition.neoworld.pvp.room.PvpRecoveryData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class PvpLargeArenaRegressionTest {
    public static void run() {
        var plan=PvpCapturePlan.create(BlockPos.ZERO,new BlockPos(255,127,255),ResourceLocation.parse("neoworld:pvp/large"));
        check(plan.volume()==8_388_608L && plan.parts().size()==108,"Capture accepts exactly eight million cells split into standard pieces");
        check(PvpArenaGeometry.size(plan.parts().stream().map(p -> new PvpArenaGeometry.Part(p.offset(),p.size())).toList()).equals(new BlockPos(256,128,256)),"Large multipart geometry reconstructs original dimensions");
        var formerFailure=PvpCapturePlan.create(BlockPos.ZERO,new BlockPos(99,29,99),ResourceLocation.parse("neoworld:pvp/medium"));
        check(formerFailure.volume()==300_000,"Former 262144-cell failure now fits");
        reject(() -> PvpCapturePlan.checkSize(257,128,256));
        reject(() -> PvpCapturePlan.checkSize(513,1,1));
        var recovery=new PvpRecoveryData();
        var region=new PvpRecoveryData.Region(new BlockPos(1_000_000,32,1_000_000),256,128,256);
        recovery.reserve(0,region);
        check(PvpRecoveryData.load(recovery.save(new CompoundTag(),null),null).regions().get(0).equals(region),"Large partially-built reservations survive restart for cleanup");
        reject(() -> new PvpRecoveryData().reserve(0,new PvpRecoveryData.Region(region.origin(),512,33,512)));
        reject(() -> new PvpRecoveryData().reserve(1,region));
        var cursor=new PvpPlacementCursor(List.of(0,20000,0,7)); var first=cursor.next();
        check(first.part()==1 && first.from()==0 && first.size()==8192,"Placement skips empty pieces and caps one batch");
        check(cursor.next().equals(first) && cursor.completed()==0,"A failed or deferred write cannot advance the cursor");
        reject(() -> cursor.advance(new PvpPlacementCursor.Slice(1,0,8193)));
        cursor.advance(new PvpPlacementCursor.Slice(1,0,10));
        check(cursor.next().from()==10 && cursor.completed()==10,"Chunk-limited partial batches retain remaining blocks");
        reject(() -> cursor.advance(first));
        long total=10;
        while(!cursor.finished()) { var slice=cursor.next(); check(slice.size()<=8192,"Every later batch stays bounded"); total+=slice.size(); cursor.advance(slice); }
        check(total==20007 && cursor.completed()==cursor.total(),"Placement neither skips nor duplicates block entries across pieces");
        var full=new PvpPlacementCursor(List.of(8_388_608)); int batches=0;
        while(!full.finished()) { full.advance(full.next()); batches++; }
        check(batches==1024,"Even a large legacy single-file template is placed in bounded batches");
        System.out.println("PvP large arena limits, recovery and bounded placement regression checks passed");
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid large arena/progress accepted");
    }
}
