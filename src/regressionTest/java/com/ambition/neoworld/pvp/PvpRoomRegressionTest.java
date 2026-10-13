package com.ambition.neoworld.pvp;

import java.util.UUID;
import com.ambition.neoworld.pvp.room.PvpRoom;
import com.ambition.neoworld.pvp.room.PvpRecoveryData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import com.ambition.neoworld.pvp.ranked.RankedMatch;
import com.ambition.neoworld.pvp.ranked.PvpResultOutbox;

public final class PvpRoomRegressionTest {
    public static void main(String[] args) {
        PvpGuiRegressionTest.run();
        PvpCacheRegressionTest.run();
        PvpMatchConfirmationRegressionTest.run();
        var bounds=new com.ambition.neoworld.pvp.arena.PvpArenaBounds(30,20,30);
        check(bounds.spawnFits(0,1,0) && bounds.spawnFits(29,18,29),"Spawn markers need no manual bounds and fit saved template edges");
        check(!bounds.spawnFits(5,0,5) && !bounds.spawnFits(5,19,5),"Spawn requires floor and head room");
        check(bounds.contains(29.9,19.9,29.9) && !bounds.contains(30,5,5) && !bounds.contains(-0.1,5,5),"Automatic boundary is half-open at saved size");
        check(!bounds.contains(Double.NaN,5,5),"Invalid coordinates are outside arena");
        UUID owner = UUID.randomUUID(), opponent = UUID.randomUUID(), outsider = UUID.randomUUID();
        PvpRoom room = new PvpRoom(1, "arena", owner);
        room.setReady(owner, true);
        reject(room::begin);
        room.join(opponent);
        check(!room.ready(owner), "Joining resets owner readiness");
        reject(() -> room.join(outsider)); reject(() -> room.setReady(outsider, true)); reject(() -> room.leave(outsider));
        room.setReady(owner, true); room.setReady(opponent, true);
        check(room.canStart(), "Two ready players can start");
        room.leave(opponent);
        check(!room.canStart() && !room.ready(owner), "Leaving resets readiness");
        room.join(opponent); room.setReady(owner, true); room.setReady(opponent, true);
        check(room.members().get(0).equals(owner), "Owner occupies spawn A");
        room.begin();
        reject(() -> room.join(outsider)); reject(() -> room.setReady(owner, false)); reject(() -> room.leave(opponent));
        check(room.state() == PvpRoom.State.COUNTDOWN, "Countdown before combat");
        room.activate(); check(room.state() == PvpRoom.State.ACTIVE && !room.canStart(), "Start cannot repeat");
        reject(room::begin);

        var data = new PvpRecoveryData();
        var region = new PvpRecoveryData.Region(new BlockPos(1_000_000, 32, 1_000_000), 32, 16, 32);
        data.reserve(0, region); reject(() -> data.reserve(0, region));
        reject(() -> data.reserve(1, region));
        var loaded = PvpRecoveryData.load(data.save(new CompoundTag(), null), null);
        check(loaded.regions().get(0).equals(region), "Reserved slot survives serialization");
        loaded.release(0); check(loaded.regions().isEmpty(), "Cleaned slot is released");

        CompoundTag saved = data.save(new CompoundTag(), null);
        ListTag players = new ListTag(); CompoundTag point = new CompoundTag();
        point.putUUID("player", owner); point.putString("dimension", "minecraft:overworld");
        point.putDouble("x", 12.5); point.putFloat("health", 17); point.putInt("gameMode",2); players.add(point); saved.put("returns", players);
        var recovery = PvpRecoveryData.load(saved, null);
        check(recovery.point(owner).getDouble("x") == 12.5 && recovery.point(owner).getFloat("health") == 17,
                "Return location and health survive restart");
        check(recovery.point(owner).getInt("gameMode")==2,"Original Adventure mode survives recovery serialization");
        recovery.recovered(owner); check(recovery.point(owner) == null, "Return is consumed once");
        CompoundTag badSlot = saved.getList("regions", 10).getCompound(0);
        badSlot.putLong("origin", BlockPos.ZERO.asLong());
        check(PvpRecoveryData.load(saved, null).regions().isEmpty(), "Cleanup never loads a region outside reserved slots");
        var match=new RankedMatch(owner,opponent,"Owner","Opponent",0);
        match.round(owner); check(match.result==null,"First round does not award match Rating");
        match.round(opponent); check(match.result==null,"One win each requires deciding round");
        match.round(owner); check(match.result==1,"Two wins finalize match");
        match.forfeit(owner); check(match.result==1,"Final result cannot be reversed");
        var surrender=new RankedMatch(owner,opponent,"Owner","Opponent",0);
        surrender.forfeit(opponent); check(surrender.result==1,"Opponent forfeit awards owner match");
        reject(() -> surrender.forfeit(outsider));
        var disconnected=new RankedMatch(owner,opponent,"Owner","Opponent",0);
        disconnected.began=true; disconnected.round(owner);
        check(disconnected.exit(owner) && disconnected.result==0,"Disconnected player loses immediately despite leading in rounds");
        check(!disconnected.exit(opponent) && disconnected.result==0,"Later disconnect cannot reverse the decided match");
        var leaving=new RankedMatch(owner,opponent,"Owner","Opponent",0);
        leaving.began=true; check(leaving.exit(opponent) && leaving.result==1,"Leaving uses the same immediate forfeit rule");
        var beforeStart=new RankedMatch(owner,opponent,"Owner","Opponent",0);
        check(!beforeStart.exit(owner) && beforeStart.result==null,"Exit before combat does not assign a rated loss");
        reject(() -> leaving.exit(outsider));
        var outbox=new PvpResultOutbox(); outbox.add(match); outbox.add(match);
        check(outbox.all().size()==1,"Same match has one pending result");
        var restored=PvpResultOutbox.load(outbox.save(new CompoundTag(),null),null);
        check(restored.all().get(0).match().equals(match.id) && restored.all().get(0).score()==1,"Pending result survives restart");
        restored.saved(match.id); check(restored.all().isEmpty(),"Committed result leaves outbox");
        var roundPause=com.ambition.neoworld.pvp.room.RoundIntermission.begin(500,opponent,false,null);
        check(roundPause.seconds(500)==5 && roundPause.seconds(520)==4,"Round break counts down from five seconds");
        check(!roundPause.finished(599) && roundPause.seconds(599)==1,"No teleport before full five seconds");
        check(roundPause.finished(600) && roundPause.seconds(600)==0 && !roundPause.terminal(),"Round restarts at exactly 100 ticks");
        var finalPause=com.ambition.neoworld.pvp.room.RoundIntermission.begin(700,opponent,true,new com.ambition.neoworld.pvp.ranked.PvpRatingRepository.ResultReport(true,1000,1016,1000,984));
        check(finalPause.terminal() && !finalPause.finished(799) && finalPause.finished(800),"Final result stays visible for five seconds before return");
        System.out.println("PvP room and recovery regression checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid operation was accepted");
    }
}
