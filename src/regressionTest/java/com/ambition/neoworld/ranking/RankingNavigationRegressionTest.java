package com.ambition.neoworld.ranking;

import com.ambition.neoworld.client.gui.ranking.RankingNavigation;

public final class RankingNavigationRegressionTest {
    public static void run() {
        if(RankingNavigation.TABS.size()!=9) throw new AssertionError("nine ranking tabs expected");
        for(int i=0;i<9;i++) {
            if((RankingNavigation.TABS.get(i).category()!=null)!=(i<3)) throw new AssertionError("only original categories may request server data");
            if(!RankingNavigation.visible(i,Math.max(0,i-4))) throw new AssertionError("every tab must be reachable");
        }
        if(RankingNavigation.clampOffset(-1)!=0 || RankingNavigation.clampOffset(99)!=4) throw new AssertionError("scroll limits");
        if(RankingNavigation.visible(5,0) || RankingNavigation.visible(3,4) || !RankingNavigation.visible(8,4)) throw new AssertionError("visible viewport bounds");
        System.out.println("Ranking navigation regression passed");
    }
}
