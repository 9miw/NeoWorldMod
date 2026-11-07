package com.ambition.neoworld.client.gui.ranking;

import java.util.List;

/** Source-space geometry measured against shell_full_body.png (1673x940). */
public final class RankingLayout {
    public static final int NAME_Y=172,TABLE_HEADER=222,ROW_FIRST=234,ROW_STEP=10,TABLE_BOTTOM=306,PERSONAL_Y=320;
    public record Region(int x,int y,int width,int height) {
        public float fit(float modelWidth,float modelHeight) { return Math.min(width/modelWidth,height/modelHeight); }
        /** GuiGraphics scissor coordinates ignore the pose; convert our translated/scaled source bounds explicitly. */
        public Region screenBounds(org.joml.Matrix4fc pose) {
            var first=pose.transformPosition(new org.joml.Vector3f(x,y,0));
            var last=pose.transformPosition(new org.joml.Vector3f(x+width,y+height,0));
            int left=(int)Math.floor(Math.min(first.x,last.x)),top=(int)Math.floor(Math.min(first.y,last.y));
            int right=(int)Math.ceil(Math.max(first.x,last.x)),bottom=(int)Math.ceil(Math.max(first.y,last.y));
            return new Region(left,top,right-left,bottom-top);
        }
    }
    // Index is ranking position minus one; center champion is deliberately larger.
    public static final List<Region> MODELS=List.of(new Region(303,60,110,104),new Region(181,65,94,99),new Region(449,65,92,99));
    public static Region levelRow(int index) {
        if(index<0 || index>=10) throw new IllegalArgumentException("Invalid level row");
        int y=70;
        for(int i=0;i<index;i++) y+=levelRowHeight(i);
        return new Region(166,y,424,levelRowHeight(index));
    }
    private static int levelRowHeight(int index) { return switch(index) { case 0 -> 33; case 1 -> 29; case 2 -> 25; default -> 19; }; }
    public static float levelTextScale(int index) { return switch(index) { case 0 -> 1.65F; case 1 -> 1.4F; case 2 -> 1.2F; default -> 1F; }; }
    public static int levelRowAt(float y) {
        for(int i=0;i<10;i++) { var row=levelRow(i); if(y>=row.y() && y<row.y()+row.height()) return i; }
        return -1;
    }
    private RankingLayout() { }
}
