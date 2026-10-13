package com.ambition.neoworld.client.gui.pvp;

import java.util.UUID;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.network.pvp.PvpMatchOfferPayload;
import com.ambition.neoworld.network.pvp.PvpMatchConfirmPayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.network.PacketDistributor;

/** Characters and their equipment are preview copies, never live-world entity mutations. */
public final class PvpMatchConfirmScreen extends Screen {
    private static final ResourceLocation FRAME=ResourceLocation.fromNamespaceAndPath("neoworld","textures/gui/pvp/match_confirm_frame.png");
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.FEET,EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD};
    private PvpMatchOfferPayload data;
    private RemotePlayer self,opponent;
    private NeoButton ready;
    private boolean sent;
    private int sentAt,age,left,top;
    private float scale;
    public PvpMatchConfirmScreen(PvpMatchOfferPayload data) { super(Component.literal("ยืนยันคู่ต่อสู้")); this.data=data; }
    public UUID offerId() { return data.offer(); }
    @Override protected void init() {
        scale=Math.min((width-16)/1000F,(height-16)/850F);
        left=Math.round((width-1000*scale)/2); top=Math.round((height-850*scale)/2);
        if(self==null && minecraft.level!=null) { self=preview(data.self()); opponent=preview(data.opponent()); }
        addRenderableWidget(new NeoButton(Button.builder(Component.literal("X"),ignored -> onClose())
                .bounds(x(1255),y(112),size(49),size(49))
                .tooltip(Tooltip.create(Component.literal("ปิดหน้าต่าง (เวลายืนยันยังนับต่อ)")))).style(NeoButton.Style.DESTRUCTIVE));
        ready=addRenderableWidget(new NeoButton(Button.builder(NeoGuiIcons.label(NeoGuiIcons.READY,"พร้อม"),ignored -> {
            if(sent || data.selfReady() || data.seconds()==0) return;
            sent=true; sentAt=age; updateButton();
            PacketDistributor.sendToServer(new PvpMatchConfirmPayload(data.offer()));
        }).bounds(x(653),y(762),size(367),size(68))).style(NeoButton.Style.PRIMARY));
        updateButton();
    }
    private RemotePlayer preview(PvpMatchOfferPayload.Player player) {
        var entity=new RemotePlayer(minecraft.level,new GameProfile(player.id(),player.name()));
        for(int i=0;i<SLOTS.length;i++) entity.setItemSlot(SLOTS[i],player.equipment().get(i).copy());
        return entity;
    }
    public void update(PvpMatchOfferPayload payload) { if(!payload.offer().equals(data.offer())) return; data=payload; if(data.selfReady()) sent=false; updateButton(); }
    private void updateButton() {
        if(ready==null) return;
        ready.active=!sent && !data.selfReady() && data.seconds()>0;
        ready.selected(data.selfReady());
        ready.setMessage(NeoGuiIcons.label(sent ? NeoGuiIcons.REFRESH : NeoGuiIcons.READY,
                data.selfReady() ? "พร้อมแล้ว" : sent ? "กำลังยืนยัน..." : "พร้อม"));
    }
    @Override public void tick() { age++; if(sent && age-sentAt>60) { sent=false; updateButton(); } }
    @Override public boolean isPauseScreen() { return false; }
    private int x(int source) { return left+Math.round((source-335)*scale); }
    private int y(int source) { return top+Math.round((source-45)*scale); }
    private int size(int source) { return Math.max(1,Math.round(source*scale)); }
    private void text(GuiGraphics g,String value,int center,int sourceY,int w,int color,float h) {
        float zoom=h/font.lineHeight; String trimmed=font.plainSubstrByWidth(value,(int)(w/zoom));
        g.pose().pushPose(); g.pose().translate(x(center),y(sourceY),0); g.pose().scale(scale*zoom,scale*zoom,1);
        g.drawString(font,trimmed,-font.width(trimmed)/2,0,color,false); g.pose().popPose();
    }
    private void rect(GuiGraphics g,int sx,int sy,int w,int h,int color) {
        g.fill(x(sx),y(sy),x(sx+w),y(sy+h),color);
    }
    private void pedestal(GuiGraphics g,int center,boolean confirmed,float time) {
        // The approved stone platform remains in the static frame; these are separate animated reflections.
        float pulse=(float)(0.5+0.5*Math.sin(time/12F));
        int hue=confirmed ? 0x2FB5A7 : 0x657F85;
        for(int band=0;band<10;band++) {
            int alpha=(int)((confirmed ? 12+20*pulse : 6+9*pulse)*(band+1)/10F);
            rect(g,center-123,489+band*5,246,5,(alpha<<24)|hue);
        }
        // Thin stepped arc follows the existing platform top rather than covering the stone artwork.
        for(int row=0;row<3;row++) {
            int inset=row==0 ? 27 : row==1 ? 11 : 3;
            int alpha=(int)(confirmed ? 80+50*pulse : 25+20*pulse);
            rect(g,center-130+inset,541+row*2,260-inset*2,2,(alpha<<24)|hue);
        }
    }
    private void statusIcon(GuiGraphics g,int center,boolean confirmed,float time) {
        if(confirmed) {
            g.pose().pushPose(); g.pose().translate(x(center-98),y(697),0);
            g.pose().scale(scale*2F,scale*2F,1);
            g.drawString(font,NeoGuiIcons.icon(NeoGuiIcons.READY),0,0,0xFFFFFFFF,false);
            g.pose().popPose();
        } else {
            int[][] points={{0,-14},{10,-10},{14,0},{10,10},{0,14},{-10,10},{-14,0},{-10,-10}};
            int frame=((int)(time/2))%8;
            for(int i=0;i<points.length;i++) {
                int trail=(i-frame+8)%8;
                int shade=235-trail*20;
                int color=0xFF000000|(shade<<16)|(shade<<8)|shade;
                rect(g,center-80+points[i][0]-3,711+points[i][1]-3,6,6,color);
            }
        }
    }
    private void player(GuiGraphics g,PvpMatchOfferPayload.Player player,RemotePlayer entity,int center,boolean confirmed,float time) {
        text(g,player.name(),center,193,290,0xFFE7ECEF,32);
        pedestal(g,center,confirmed,time);
        if(entity!=null) InventoryScreen.renderEntityInInventoryFollowsMouse(g,x(center-155),y(235),x(center+155),y(555),size(145),0.0625F,x(835),y(390),entity);
        text(g,"คะแนน",center-102,590,110,0xFFE7ECEF,25);
        text(g,String.format(java.util.Locale.ROOT,"%,d",player.rating()),center+97,590,115,0xFFF2C14E,28);
        text(g,"อันดับ",center-102,642,110,0xFFE7ECEF,25);
        text(g,player.position()<0 ? "—" : player.position()==0 ? "ยังไม่มีอันดับ" : "#"+player.position(),center+88,642,140,0xFFF2C14E,player.position()==0 ? 20 : 28);
        statusIcon(g,center,confirmed,time);
        text(g,confirmed ? "พร้อมแล้ว" : "รอกดพร้อม",center+25,697,220,confirmed ? 0xFF63D9CF : 0xFFABB4BA,28);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g,mouseX,mouseY,partialTick);
        g.blit(FRAME,left,top,size(1000),size(850),335,45,1000,850,1672,941);
        text(g,"ยืนยันคู่ต่อสู้",835,79,400,0xFFE7ECEF,39);
        float animation=age+partialTick;
        player(g,data.self(),self,556,data.selfReady(),animation); player(g,data.opponent(),opponent,1116,data.opponentReady(),animation);
        text(g,"ยืนยันภายใน",835,215,180,0xFFE7ECEF,27);
        int timerColor=data.seconds()<=5 ? 0xFFFF7B72 : 0xFFF2C14E;
        text(g,Integer.toString(data.seconds()),835,265,170,timerColor,100);
        text(g,"วินาที",835,373,160,0xFFABB4BA,29);
        text(g,"VS",838,460,170,0xFF171B1E,78);
        text(g,"VS",835,455,170,0xFFE7ECEF,78);
        rect(g,747,463,3,69,0xFF2FB5A7); rect(g,923,463,3,69,0xFF2FB5A7);
        text(g,data.selfReady() ? "รอคู่ต่อสู้ยืนยัน" : "กดพร้อมภายในเวลาที่กำหนด",835,843,770,0xFFABB4BA,23);
        // Draw widgets directly so the background blur only runs before the frame and characters.
        for(var widget:renderables) widget.render(g,mouseX,mouseY,partialTick);
    }
}
