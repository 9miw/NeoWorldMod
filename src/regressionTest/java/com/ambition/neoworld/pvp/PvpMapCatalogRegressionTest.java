package com.ambition.neoworld.pvp;

import java.nio.file.Files;
import java.util.List;
import com.ambition.neoworld.pvp.arena.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class PvpMapCatalogRegressionTest {
    public static void run() {
        var data=new PvpArenaData();
        var name=PvpCaptureService.mapId("test");
        check(name.equals(ResourceLocation.parse("neoworld:pvp/test")),"Short map names use PvP namespace");
        check(PvpCaptureService.mapId("other:test").equals(ResourceLocation.parse("other:test")),"Explicit namespace retained");
        var old=List.of(new PvpArenaPiece(ResourceLocation.parse("neoworld:pvp/test_01"),BlockPos.ZERO));
        var next=List.of(new PvpArenaPiece(ResourceLocation.parse("neoworld:pvp/test/rev_new_01"),BlockPos.ZERO));
        var backup=PvpCaptureService.mapId("test/backup_old");
        data.addMap(name,old);
        check(data.all().isEmpty(),"Saving a map does not create an arena");
        data.create("a"); data.create("b");
        data.update("a",data.get("a"),old); data.update("b",data.get("b"),old);
        data.replaceMap(name,old,next,backup);
        check(data.map(backup).equals(old) && data.map(name).equals(next),"Update retains old revision");
        check(data.pieces("a").equals(next) && data.pieces("b").equals(next),"All linked arenas follow revision");
        check(!data.get("a").validated() && !data.get("b").validated(),"Updated arenas require validation");
        reject(() -> data.removeMap(name,next));
        reject(() -> data.replaceMap(name,old,next,PvpCaptureService.mapId("stale")));
        data.validated("a");
        var loaded=PvpArenaData.load(data.save(new CompoundTag(),null),null);
        check(loaded.map(name).equals(next) && loaded.map(backup).equals(old),"Catalog survives restart");
        check(loaded.get("a").validated() && !loaded.get("b").validated(),"Validation state survives restart");
        loaded.removeMap(backup,old);
        check(loaded.map(backup)==null,"Unreferenced backup can be removed");
        check(loaded.removeArena("a") && !loaded.removeArena("a"),"Arena removal reports missing registrations");
        check(loaded.map(name).equals(next) && loaded.pieces("b").equals(next),"Removing an arena preserves its map and other arena users");
        check(loaded.mapUsers(next).equals(List.of("b")),"Deleted arenas no longer reference catalog maps");
        var afterDelete=PvpArenaData.load(loaded.save(new CompoundTag(),null),null);
        check(afterDelete.get("a")==null && afterDelete.map(name).equals(next),"Arena deletion survives restart without deleting maps");
        archive();
    }
    private static void archive() {
        try {
            var temp=Files.createTempDirectory("pvp-map-regression-");
            var generated=Files.createDirectories(temp.resolve("generated"));
            var first=generated.resolve("one.nbt"); var second=generated.resolve("two.nbt");
            Files.writeString(first,"one"); Files.writeString(second,"two");
            var backup=temp.resolve("backup");
            PvpMapFiles.archive(List.of(first,second),generated,backup);
            check(!Files.exists(first) && Files.readString(backup.resolve("one.nbt")).equals("one"),"Deletion archives generated files");
            Files.move(backup.resolve("one.nbt"),first);
            var rollback=temp.resolve("rollback");
            try {
                PvpMapFiles.archive(List.of(first,generated.resolve(".").resolve("one.nbt")),generated,rollback);
                throw new AssertionError("Second move should fail after the first consumes its source");
            } catch(java.io.IOException expected) {
                check(Files.readString(first).equals("one") && !Files.exists(rollback.resolve("one.nbt")),"Partial moves are rolled back");
            }
            var absent=generated.resolve("absent.nbt");
            try { PvpMapFiles.archive(List.of(first,absent),generated,temp.resolve("failure")); throw new AssertionError("Missing file accepted"); }
            catch(java.io.IOException expected) { check(Files.exists(first),"Validation failure does not move any files"); }
            var outside=temp.resolve("outside.nbt"); Files.writeString(outside,"outside");
            try { PvpMapFiles.archive(List.of(outside),generated,temp.resolve("escape")); throw new AssertionError("External file accepted"); }
            catch(java.io.IOException expected) { check(Files.exists(outside),"Outside files preserved"); }
            // Explicitly remove only the test files and directories created above.
            Files.delete(first); Files.delete(outside); Files.delete(backup.resolve("two.nbt"));
            Files.delete(rollback); Files.delete(backup); Files.delete(generated); Files.delete(temp);
        } catch(java.io.IOException exception) { throw new AssertionError(exception); }
    }
    private static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); } catch(IllegalArgumentException expected) { }
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
