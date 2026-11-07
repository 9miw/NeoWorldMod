package com.ambition.neoworld.ranking;

import java.util.List;
import java.util.UUID;
import com.ambition.neoworld.network.ranking.RankingDataPayload;
import com.ambition.neoworld.network.ranking.RankingRequestPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class RankingNetworkRegressionTest {
    public static void main(String[] args) {
        // Same minimal headless registry fixture as QuestCommandPacketRegressionTest.
        try {
            var bootstrapped=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
            bootstrapped.setAccessible(true); bootstrapped.setBoolean(null,true);
        } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
        RankingTableRegressionTest.main(args);
        RankingSnapshotCacheRegressionTest.run();
        RankingAppearanceRegressionTest.run();
        RankingLayoutRegressionTest.run();
        RankingNavigationRegressionTest.run();
        com.ambition.neoworld.client.gui.widget.NeoButtonScaleRegressionTest.run();
        UUID id=UUID.randomUUID(), request=UUID.randomUUID();
        var entry=new RankingTable.Entry(id,"ผู้เล่น",80,999,Long.MAX_VALUE,1700,10,2,Long.MAX_VALUE);
        var data=new RankingDataPayload(request,RankingTable.Category.SILVER,"",new RankingTable.View(List.of(entry),entry,1));
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            RankingDataPayload.STREAM_CODEC.encode(b,data);
            check(data.equals(RankingDataPayload.STREAM_CODEC.decode(b)),"response round trip including long silver");
            var query=new RankingRequestPayload(request,RankingTable.Category.LEVEL);
            RankingRequestPayload.STREAM_CODEC.encode(b,query);
            check(query.equals(RankingRequestPayload.STREAM_CODEC.decode(b)),"request round trip");
            b.clear(); b.writeUUID(request); b.writeEnum(RankingTable.Category.PVP); b.writeUtf(""); b.writeVarInt(11);
            try { RankingDataPayload.STREAM_CODEC.decode(b); throw new AssertionError("oversized list accepted"); }
            catch(IllegalArgumentException expected) { }
        } finally { b.release(); }
        var values=new CompoundTag(); values.putInt("level",50); values.putLong("exp",456); values.putLong("silver",9999999999L);
        values.putLong("total_exp",12345678L);
        var attachments=new CompoundTag(); attachments.put("neoworld:player_data",values);
        var player=new CompoundTag(); player.put("neoforge:attachments",attachments);
        var saved=SavedRankingPlayers.entry(id,player);
        check(saved.level()==50 && saved.exp()==456 && saved.silver()==9999999999L,"saved attachment extraction");
        check(saved.totalExp()==12345678L,"offline cumulative EXP is separate from current EXP");
        check(saved.named("Renamed").totalExp()==12345678L,"resolving names must retain cumulative EXP");
        check(SavedRankingPlayers.entry(id,new CompoundTag()).level()==1,"older player without attachment uses defaults");
        values.putLong("silver",-1);
        try { SavedRankingPlayers.entry(id,player); throw new AssertionError("corrupt balance accepted"); }
        catch(IllegalArgumentException expected) { }
        System.out.println("Ranking network and save regression passed");
    }
    private static void check(boolean value,String label) { if(!value) throw new AssertionError(label); }
}
