package com.ambition.neoworld.ranking;

import com.ambition.neoworld.client.gui.ranking.RankingLayout;

public final class RankingLayoutRegressionTest {
    public static void run() {
        int previousBottom=68;
        for(int i=0;i<10;i++) {
            var row=RankingLayout.levelRow(i);
            if(row.y()<previousBottom || row.y()+row.height()>291) throw new AssertionError("level rows overlap or exceed table");
            if(RankingLayout.levelRowAt(row.y()+row.height()/2F)!=i) throw new AssertionError("level tooltip row mismatch");
            previousBottom=row.y()+row.height();
        }
        if(!(RankingLayout.levelTextScale(0)>RankingLayout.levelTextScale(1)
                && RankingLayout.levelTextScale(1)>RankingLayout.levelTextScale(2)
                && RankingLayout.levelTextScale(2)>RankingLayout.levelTextScale(3))) throw new AssertionError("top three must step down in font size");
        if(RankingLayout.levelRowAt(52)!=-1 || RankingLayout.levelRowAt(294)!=-1) throw new AssertionError("header/footer must not hover a player");
        for(var region:RankingLayout.MODELS) {
            if(region.y()+region.height()>RankingLayout.NAME_Y || region.x()<162 || region.x()+region.width()>592)
                throw new AssertionError("model overlaps labels or frame");
            if(region.height()<99) throw new AssertionError("full body lacks vertical space");
            float wide=region.fit(3.2F,2.15F);
            if(wide*3.2F>region.width()+0.001F || wide*2.15F>region.height()+0.001F)
                throw new AssertionError("wide fashion exceeds preview region");
        }
        int last=RankingLayout.ROW_FIRST+6*RankingLayout.ROW_STEP;
        if(last+9>RankingLayout.TABLE_BOTTOM || RankingLayout.TABLE_BOTTOM>=RankingLayout.PERSONAL_Y-8)
            throw new AssertionError("seven rows collide with personal strip");
        for(int guiScale=1;guiScale<=3;guiScale++) {
            int width=(int)Math.ceil(1280D/guiScale),height=(int)Math.ceil(720D/guiScale);
            float scale=Math.min((width-8F)/640,(height-8F)/360);
            int left=Math.round((width-640*scale)/2),top=Math.round((height-360*scale)/2);
            var pose=new org.joml.Matrix4f().translation(left,top,0).scale(scale,scale,1);
            for(var region:RankingLayout.MODELS) {
                var clip=region.screenBounds(pose);
                int expectedX=(int)Math.floor(left+region.x()*scale);
                int expectedY=(int)Math.floor(top+region.y()*scale);
                int right=(int)Math.ceil(left+(region.x()+region.width())*scale);
                int bottom=(int)Math.ceil(top+(region.y()+region.height())*scale);
                if(clip.x()!=expectedX || clip.y()!=expectedY || clip.width()!=right-expectedX || clip.height()!=bottom-expectedY)
                    throw new AssertionError("scissor must follow the render pose at GUI Scale "+guiScale);
            }
        }
        System.out.println("Full-body layout regression passed");
    }
}
