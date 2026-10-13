package com.ambition.neoworld.client.gui.overlay;

import com.ambition.neoworld.client.input.PvpQueueKeyHandler;
import com.ambition.neoworld.client.state.ClientPvpQueueData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

/** Small read-only sidebar; cancel uses the key binding, not a hidden cursor. */
public final class PvpQueueOverlay implements LayeredDraw.Layer {
    @Override public void render(GuiGraphics g,DeltaTracker delta) {
        var mc=Minecraft.getInstance(); var state=ClientPvpQueueData.state();
        if(mc.player==null || mc.options.hideGui || mc.screen!=null || !state.queued()) return;
        var font=mc.font;
        String header=Component.translatable("hud.neoworld.pvp_queue.title").getString();
        String status=Component.translatable("hud.neoworld.pvp_queue.searching").getString();
        String cancel=Component.translatable("hud.neoworld.pvp_queue.cancel",PvpQueueKeyHandler.CANCEL.getTranslatedKeyMessage()).getString();
        int w=Math.min(154,Math.max(116,Math.max(font.width(header),font.width(cancel))+14));
        int line=font.lineHeight+3,h=line*5+12;
        int x=g.guiWidth()-w-5,y=(g.guiHeight()-h)/2;
        // Thin gunmetal/silver frame follows the compact scoreboard-sized approved preview.
        g.fill(x,y,x+w,y+h,0xC5101519);
        g.fill(x,y,x+w,y+1,0xFF7B858C); g.fill(x,y+h-1,x+w,y+h,0xFF4B535A);
        g.fill(x,y,x+1,y+h,0xFF4B535A); g.fill(x+w-1,y,x+w,y+h,0xFF7B858C);
        g.drawCenteredString(font,font.plainSubstrByWidth(header,w-12),x+w/2,y+6,0xFF63D9CF);
        g.drawString(font,font.plainSubstrByWidth(status,w-12),x+7,y+6+line,0xFFABB4BA,false);
        String time=String.format(java.util.Locale.ROOT,"%02d:%02d",state.elapsedSeconds()/60,state.elapsedSeconds()%60);
        row(g,Component.translatable("hud.neoworld.pvp_queue.time").getString(),time,x,y+6+line*2,w);
        row(g,Component.translatable("hud.neoworld.pvp_queue.rating").getString(),String.format(java.util.Locale.ROOT,"%,d",state.rating()),x,y+6+line*3,w);
        g.fill(x+5,y+line*4+3,x+w-5,y+h-5,0xB0176B65);
        g.drawCenteredString(font,font.plainSubstrByWidth(cancel,w-14),x+w/2,y+6+line*4,0xFF8FE5DE);
    }
    private void row(GuiGraphics g,String label,String value,int x,int y,int w) {
        var font=Minecraft.getInstance().font;
        g.drawString(font,font.plainSubstrByWidth(label,Math.max(8,w-font.width(value)-22)),x+7,y,0xFFABB4BA,false);
        g.drawString(font,value,x+w-7-font.width(value),y,0xFFF2C14E,false);
    }
}
