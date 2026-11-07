package com.ambition.neoworld.ranking;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RankingTableRegressionTest {
    public static void main(String[] args) {
        var entries = new ArrayList<RankingTable.Entry>();
        for (int i=1;i<=12;i++) entries.add(new RankingTable.Entry(new UUID(0,i),"P"+i,i,100-i,Long.MAX_VALUE-i,i,13-i,i));
        var own = entries.getFirst().id();
        var levels = RankingTable.select(RankingTable.Category.LEVEL,entries,own);
        check(levels.leaders().size()==10 && levels.position()==12,"personal outside top ten");
        check(levels.leaders().getFirst().level()==12,"level descending");
        var coins = RankingTable.select(RankingTable.Category.SILVER,entries,own);
        check(coins.position()==1 && coins.leaders().getFirst().silver()==Long.MAX_VALUE-1,"silver without overflow");
        var a=new RankingTable.Entry(new UUID(0,1),"A",10,80,0,1000,2,3);
        var b=new RankingTable.Entry(new UUID(0,2),"B",10,90,0,1000,3,2);
        var c=new RankingTable.Entry(new UUID(0,3),"C",10,90,0,1000,3,2);
        check(RankingTable.select(RankingTable.Category.LEVEL,List.of(a,c,b),own).leaders().getFirst().id().equals(b.id()),"EXP then stable UUID tie");
        var highTotal=new RankingTable.Entry(new UUID(0,5),"High total",10,80,0,0,0,0,1000000);
        var highProgress=new RankingTable.Entry(new UUID(0,6),"High progress",10,90,0,0,0,0,10);
        check(RankingTable.select(RankingTable.Category.LEVEL,List.of(highTotal,highProgress),own)
                .leaders().getFirst().id().equals(highProgress.id()),"displayed total EXP must not change the existing level/current-EXP order");
        check(RankingTable.select(RankingTable.Category.PVP,List.of(a,c,b),own).leaders().getFirst().id().equals(b.id()),"PvP wins then UUID tie");
        var unplayed=new RankingTable.Entry(new UUID(0,4),"New",1,0,0,1000,0,0);
        check(RankingTable.select(RankingTable.Category.PVP,List.of(unplayed),unplayed.id()).position()==0,"unplayed unranked");
        check(RankingTable.select(RankingTable.Category.LEVEL,List.of(),own).leaders().isEmpty(),"empty table");
        try { new RankingTable.Entry(own,"bad",1,-1,0,1000,0,0); throw new AssertionError("negative EXP accepted"); }
        catch(IllegalArgumentException expected) { }
        System.out.println("Ranking table regression passed");
    }
    private static void check(boolean condition,String label) { if(!condition) throw new AssertionError(label); }
}
