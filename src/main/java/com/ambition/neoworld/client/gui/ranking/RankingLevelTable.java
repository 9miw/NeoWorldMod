package com.ambition.neoworld.client.gui.ranking;

import java.util.List;
import java.util.Locale;
import com.ambition.neoworld.ranking.RankingTable.Entry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Level-only table. Reuses measured shell/frame regions; contains no entity or skin rendering. */
final class RankingLevelTable {
    private static final ResourceLocation SHELL=ResourceLocation.fromNamespaceAndPath("neoworld","textures/gui/ranking/shell_full_body.png");
    private static final int TEXT=0xFFE7ECEF,MUTED=0xFFABB4BA,GOLD=0xFFF2C14E;
    private RankingLevelTable() { }
    static void backdrop(GuiGraphics g) {
        // Preserve the outer frame and navigation; compose the right area without the three podiums.
        image(g,0,0,161,360,0,0,420,940);
        image(g,161,0,431,30,420,0,1128,78);
        image(g,592,0,48,360,1548,0,125,940);
        image(g,161,333,431,27,420,869,1128,71);
        image(g,161,30,431,303,145,190,200,470);
        panel(g,162,42,430,264,423,571,1125,227);
        panel(g,162,311,430,22,423,809,1125,58);
    }
    static void render(GuiGraphics g,Font font,List<Entry> leaders,Entry own,int position,String status) {
        cell(g,font,"อันดับ",169,52,40,TEXT,1);
        cell(g,font,"ผู้เล่น",218,52,140,TEXT,1);
        cell(g,font,"เลเวล",366,52,68,TEXT,1);
        cell(g,font,"EXP สะสม",444,52,140,TEXT,1);
        g.fill(169,65,585,66,0xFF505E66);
        for(int i=0;i<leaders.size();i++) {
            var row=RankingLayout.levelRow(i); var entry=leaders.get(i);
            float size=RankingLayout.levelTextScale(i);
            int color=switch(i) { case 0 -> GOLD; case 1 -> 0xFFCBD7E5; case 2 -> 0xFFD99D77; default -> TEXT; };
            if(i%2==0) g.fill(row.x(),row.y(),row.x()+row.width(),row.y()+row.height(),0x193D7774);
            float y=row.y()+(row.height()-font.lineHeight*size)/2F;
            cell(g,font,""+(i+1),169,y,40,color,size);
            cell(g,font,entry.name(),218,y,140,color,size);
            cell(g,font,"Lv. "+entry.level(),366,y,68,color,size);
            cell(g,font,number(entry.totalExp()),444,y,140,color,size);
        }
        if(!status.isEmpty()) cell(g,font,status,177,170,405,MUTED,1);
        cell(g,font,"EXP สะสม: นับตั้งแต่เริ่มระบบ ไม่รวมประวัติย้อนหลัง",171,294,414,MUTED,1);
        cell(g,font,"อันดับของคุณ",168,320,80,TEXT,1);
        cell(g,font,own==null ? "—" : position==0 ? "ยังไม่มีอันดับ" : "#"+position,248,320,40,GOLD,1);
        if(own!=null) {
            cell(g,font,own.name(),293,320,97,TEXT,1);
            cell(g,font,"Lv. "+own.level(),394,320,60,TEXT,1);
            cell(g,font,number(own.totalExp())+" EXP",459,320,126,GOLD,1);
        }
    }
    private static String number(long value) { return String.format(Locale.US,"%,d",value); }
    private static void cell(GuiGraphics g,Font font,String text,int x,float y,int width,int color,float requested) {
        float size=Math.min(requested,width/(float)Math.max(1,font.width(text)));
        g.pose().pushPose(); g.pose().translate(x+(width-font.width(text)*size)/2F,y,0); g.pose().scale(size,size,1);
        g.drawString(font,text,0,0,color,false); g.pose().popPose();
    }
    private static void image(GuiGraphics g,int x,int y,int w,int h,int u,int v,int sw,int sh) {
        g.blit(SHELL,x,y,w,h,u,v,sw,sh,1673,940);
    }
    private static void panel(GuiGraphics g,int x,int y,int w,int h,int u,int v,int sw,int sh) {
        int[] widths={4,w-8,4},heights={4,h-8,4},sourcesX={16,sw-32,16},sourcesY={16,sh-32,16};
        int dy=y,sourceY=v;
        for(int row=0;row<3;row++) {
            int dx=x,sourceX=u;
            for(int col=0;col<3;col++) {
                image(g,dx,dy,widths[col],heights[row],sourceX,sourceY,sourcesX[col],sourcesY[row]);
                dx+=widths[col]; sourceX+=sourcesX[col];
            }
            dy+=heights[row]; sourceY+=sourcesY[row];
        }
    }
}
