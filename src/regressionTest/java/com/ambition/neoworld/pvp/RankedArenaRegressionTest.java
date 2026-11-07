package com.ambition.neoworld.pvp;

import java.util.List;
import java.util.Random;
import java.util.HashSet;
import com.ambition.neoworld.pvp.ranked.RankedArenaSelector;

public final class RankedArenaRegressionTest {
    public static void run() {
        var maps=List.of("draft","arena_1","saving","broken","arena_2","arena_2");
        var seen=new HashSet<String>(); var random=new Random(723);
        for(int i=0;i<100;i++) {
            var map=RankedArenaSelector.pick(maps,id -> id.equals("arena_1") || id.equals("arena_2"),random).orElseThrow();
            check(map.equals("arena_1") || map.equals("arena_2"),"Only usable maps can be chosen"); seen.add(map);
        }
        check(seen.size()==2,"Both available maps can be selected");
        var single=RankedArenaSelector.pick(maps,id -> id.equals("arena_2"),random);
        check(single.orElseThrow().equals("arena_2"),"One usable map needs no fallback to invalid maps");
        var bounded=new Random(1) {
            @Override public int nextInt(int bound) { check(bound==2,"Duplicate IDs cannot bias the map pool"); return bound-1; }
        };
        check(RankedArenaSelector.pick(maps,id -> id.startsWith("arena_"),bounded).orElseThrow().equals("arena_2"),"Choice indexes the filtered pool");
        var never=new Random(1) { @Override public int nextInt(int bound) { throw new AssertionError("Empty pool must not request randomness"); } };
        check(RankedArenaSelector.pick(maps,id -> false,never).isEmpty() && RankedArenaSelector.pick(List.of(),id -> true,never).isEmpty(),"Empty/unusable pools are explicit and safe");
        var queue=com.ambition.neoworld.command.pvp.PvpRankedCommand.queue().build();
        check(queue.getCommand()!=null && queue.getChildren().isEmpty(),"Queue command executes directly without an arena argument");
        System.out.println("PvP random ranked arena and global queue regression checks passed");
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
