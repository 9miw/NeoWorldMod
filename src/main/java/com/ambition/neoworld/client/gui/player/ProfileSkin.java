package com.ambition.neoworld.client.gui.player;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Profile-only artwork extracted from the approved design; all content stays dynamic. */
final class ProfileSkin {
    record Sprite(ResourceLocation texture, int width, int height, int sourceEdge) {}
    static final Sprite SHELL=sprite("frame",1672,940,40);
    static final Sprite[] TABS = {sprite("tab_normal",320,80,24), sprite("tab_hover",320,80,24), sprite("tab_selected",320,80,24), sprite("tab_disabled",320,80,24)};
    static final Sprite[] BUTTONS = {sprite("button_normal",320,80,24), sprite("button_hover",320,80,24), sprite("button_selected",320,80,24), sprite("button_disabled",320,80,24)};
    static final Sprite SECTION = sprite("section",320,56,24);
    static final Sprite ROW = sprite("row_normal",320,64,16), ROW_HOVER = sprite("row_hover",320,64,16);
    static final Sprite PANEL = sprite("panel",320,192,36), CARD=sprite("card",160,192,24);
    static final Sprite PORTRAIT=sprite("portrait",160,256,24);
    static final Sprite PROGRESS_TRACK=sprite("progress_track",320,40,12),PROGRESS_FILL=sprite("progress_fill",300,16,0);
    // Existing icons are retained independently from the newly approved frame/control artwork.
    static final Sprite SWORDS=existingIcon("swords",146,145),SHIELD=existingIcon("shield",147,168),BOOT=existingIcon("boot",129,138);
    private ProfileSkin() {}
    private static Sprite sprite(String name, int width, int height,int edge) {
        return new Sprite(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID,"textures/gui/player/v5/"+name+".png"),width,height,edge);
    }
    private static Sprite existingIcon(String name,int width,int height) {
        return new Sprite(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID,"textures/gui/player/v3/"+name+".png"),width,height,0);
    }
    static void frame(GuiGraphics g, Sprite s, int x, int y, int width, int height, int corner) {
        // Each normalized component has measured source caps; ornament never spans the stretchable center.
        int edge = s.sourceEdge;
        int cx = Math.min(corner, width / 2), cy = Math.min(corner, height / 2);
        int edgeY = Math.min(edge, (s.height - 1) / 2);
        int[] sw = {edge,s.width-2*edge,edge}, sh = {edgeY,s.height-2*edgeY,edgeY};
        int[] dw = {cx,width-2*cx,cx}, dh = {cy,height-2*cy,cy};
        RenderSystem.enableBlend();
        int ty=y, v=0;
        for(int row=0;row<3;row++) {
            int tx=x, u=0;
            for(int col=0;col<3;col++) {
                if(dw[col]>0 && dh[row]>0) g.blit(s.texture,tx,ty,dw[col],dh[row],u,v,sw[col],sh[row],s.width,s.height);
                tx+=dw[col]; u+=sw[col];
            }
            ty+=dh[row]; v+=sh[row];
        }
    }
    static void icon(GuiGraphics g, Sprite s, int x, int y, int size) {
        float factor=size/(float)Math.max(s.width,s.height);
        int w=Math.max(1,Math.round(s.width*factor)),h=Math.max(1,Math.round(s.height*factor));
        RenderSystem.enableBlend();
        g.blit(s.texture,x+(size-w)/2,y+(size-h)/2,w,h,0,0,s.width,s.height,s.width,s.height);
    }
    static void progress(GuiGraphics g,int x,int y,int width,int height,float progress,int color) {
        frame(g,PROGRESS_TRACK,x,y,width,height,3);
        int inner=width-6,filled=Math.round(inner*Math.clamp(progress,0,1));
        if(filled<=0) return;
        g.setColor(((color>>16)&255)/255F,((color>>8)&255)/255F,(color&255)/255F,1);
        try {
            var s=PROGRESS_FILL;
            g.blit(s.texture,x+3,y+3,filled,height-6,0,0,Math.max(1,Math.round(s.width*filled/(float)inner)),s.height,s.width,s.height);
        } finally { g.setColor(1,1,1,1); }
    }
}
