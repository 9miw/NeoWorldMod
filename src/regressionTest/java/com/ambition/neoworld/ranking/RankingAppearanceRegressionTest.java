package com.ambition.neoworld.ranking;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.ambition.neoworld.network.ranking.RankingDataPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public final class RankingAppearanceRegressionTest {
    public static void run() {
        var before=Instant.parse("2026-10-27T16:59:59Z");
        var after=Instant.parse("2026-10-27T17:00:00Z");
        long day=RankingAppearance.day(before);
        check(RankingAppearance.day(after)==day+1,"Thai midnight boundary");
        var saved=new RankingAppearance(day,"texture-value","signature",java.util.Collections.nCopies(6,ItemStack.EMPTY),Map.of());
        check(!saved.needsCapture(before) && saved.needsCapture(after),"only first login of each Thai date captures");
        check(RankingAppearance.EMPTY.needsCapture(before),"missing snapshot needs capture");
        var tag=RankingAppearance.CODEC.encodeStart(NbtOps.INSTANCE,saved).getOrThrow();
        var restored=RankingAppearance.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();
        check(restored.day()==day && restored.texture().equals(saved.texture()) && restored.signature().equals(saved.signature()),"saved skin property survives restart");
        UUID id=UUID.randomUUID();
        var profile=restored.profile(id,"SavedPlayer");
        check(profile.getProperties().get("textures").iterator().next().signature().equals("signature"),"offline model receives recorded signed texture property");
        try {
            var directory=java.nio.file.Files.createTempDirectory("ranking-appearance-regression-");
            var file=directory.resolve(id+".dat");
            try {
                var attachments=new net.minecraft.nbt.CompoundTag(); attachments.put("neoworld:ranking_appearance",tag);
                var root=new net.minecraft.nbt.CompoundTag(); root.put("neoforge:attachments",attachments);
                net.minecraft.nbt.NbtIo.writeCompressed(root,file);
                var offline=SavedRankingPlayers.appearance(file,RegistryAccess.EMPTY);
                check(offline.day()==day && offline.texture().equals("texture-value"),"actual compressed offline file preserves daily appearance");
                check(SavedRankingPlayers.appearance(directory.resolve("missing.dat"),RegistryAccess.EMPTY).day()==-1,"missing offline snapshot remains explicit");
            } finally { java.nio.file.Files.deleteIfExists(file); java.nio.file.Files.deleteIfExists(directory); }
        } catch(java.io.IOException error) { throw new AssertionError(error); }
        var payload=new RankingDataPayload(UUID.randomUUID(),RankingTable.Category.PVP,"",new RankingTable.View(List.of(),null,0),List.of(new RankingDataPayload.Preview(id,saved)));
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            RankingDataPayload.STREAM_CODEC.encode(b,payload);
            var decoded=RankingDataPayload.STREAM_CODEC.decode(b);
            check(decoded.previews().size()==1 && decoded.previews().getFirst().id().equals(id)
                    && decoded.previews().getFirst().appearance().texture().equals("texture-value"),"offline preview payload survives round trip");
        } finally { b.release(); }
        try { new RankingDataPayload(payload.request(),payload.category(),"",payload.view(),java.util.Collections.nCopies(4,new RankingDataPayload.Preview(id,saved))); throw new AssertionError("four podium previews accepted"); }
        catch(IllegalArgumentException expected) { }
        System.out.println("Daily appearance regression passed");
    }
    private static void check(boolean value,String label) { if(!value) throw new AssertionError(label); }
}
