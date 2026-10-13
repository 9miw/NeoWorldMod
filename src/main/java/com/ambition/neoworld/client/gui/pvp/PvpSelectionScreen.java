package com.ambition.neoworld.client.gui.pvp;

import java.util.UUID;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.network.pvp.PvpGuiRequestPayload;
import com.ambition.neoworld.network.pvp.PvpGuiDataPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Coordinates come from the approved mockup; text, values and controls are dynamic. */
public final class PvpSelectionScreen extends Screen {
    private enum Page { SELECT, ARENA, ROOM, TOP }
    private static final ResourceLocation FRAME=texture("selection_frame"),SWORDS=texture("swords"),TROPHY=texture("trophy");
    private Page page=Page.SELECT;
    private PvpGuiRequestPayload.Action choosing=PvpGuiRequestPayload.Action.CREATE;
    private PvpGuiDataPayload data;
    private UUID pending;
    private PvpGuiRequestPayload.Action pendingAction;
    private int age,sentAt;
    private String arena="",status="กำลังโหลดข้อมูล...";
    private float scale;
    private int left,top;
    public PvpSelectionScreen() { super(Component.literal("PvP 1vs1")); }
    private static ResourceLocation texture(String id) { return ResourceLocation.fromNamespaceAndPath("neoworld","textures/gui/pvp/"+id+".png"); }
    @Override protected void init() {
        scale=Math.min((width-16)/950F,(height-16)/835F);
        left=Math.round((width-950*scale)/2); top=Math.round((height-835*scale)/2);
        rebuild(); if(data==null && pending==null) request(PvpGuiRequestPayload.Action.QUERY);
    }
    private int x(int source) { return left+Math.round((source-360)*scale); }
    private int y(int source) { return top+Math.round((source-65)*scale); }
    private int size(int source) { return Math.max(1,Math.round(source*scale)); }
    private NeoButton button(String label,int sourceX,int sourceY,int w,int h,Runnable action,boolean enabled,NeoButton.Style style) {
        var button=addRenderableWidget(new NeoButton(Button.builder(Component.literal(label),ignored -> action.run())
                .bounds(x(sourceX),y(sourceY),size(w),size(h))).style(style));
        button.active=enabled; return button;
    }
    private void rebuild() {
        clearWidgets();
        button("X",1248,123,49,49,this::onClose,true,NeoButton.Style.DESTRUCTIVE);
        boolean busy=pending!=null;
        if(page==Page.SELECT) {
            boolean free=data!=null && data.roomId()==0 && !data.queued();
            button("สร้างห้อง",452,654,349,82,() -> choose(PvpGuiRequestPayload.Action.CREATE),!busy && free && !data.arenas().isEmpty(),NeoButton.Style.PRIMARY);
            if(data!=null && data.queued()) button("ยกเลิกคิว",872,654,349,82,() -> request(PvpGuiRequestPayload.Action.UNQUEUE),!busy,NeoButton.Style.DESTRUCTIVE);
            else button("ค้นหาคู่ต่อสู้",872,654,349,82,() -> choose(PvpGuiRequestPayload.Action.QUEUE),!busy && free && data.ratingAvailable() && !data.arenas().isEmpty(),NeoButton.Style.PRIMARY);
            button("ตารางอันดับ",674,773,327,54,() -> { page=Page.TOP; request(PvpGuiRequestPayload.Action.TOP); },!busy && data!=null && data.ratingAvailable(),NeoButton.Style.STANDARD);
        } else if(page==Page.ARENA) {
            button("เปลี่ยนสนาม",480,490,290,65,this::nextArena,!busy && data!=null && !data.arenas().isEmpty(),NeoButton.Style.STANDARD);
            button(choosing==PvpGuiRequestPayload.Action.CREATE ? "สร้างห้อง" : "เข้าคิวจัดอันดับ",480,660,290,65,() -> request(choosing),!busy && !arena.isEmpty(),NeoButton.Style.PRIMARY);
            back();
        } else if(page==Page.ROOM) {
            boolean waiting=data!=null && data.state().equals("WAITING") && !data.ranked();
            button(data!=null && data.ready() ? "ยกเลิกพร้อม" : "พร้อม",470,653,310,65,
                    () -> request(data.ready() ? PvpGuiRequestPayload.Action.UNREADY : PvpGuiRequestPayload.Action.READY),!busy && waiting,NeoButton.Style.PRIMARY);
            button("เริ่มการแข่งขัน",890,530,310,65,() -> request(PvpGuiRequestPayload.Action.START),!busy && waiting && data.owner() && data.canStart(),NeoButton.Style.PRIMARY);
            button("ออกจากห้อง",890,653,310,65,() -> request(PvpGuiRequestPayload.Action.LEAVE),!busy && data!=null && data.roomId()>0,NeoButton.Style.DESTRUCTIVE);
        } else back();
    }
    private void back() { button("กลับ",674,773,327,54,() -> { page=Page.SELECT; rebuild(); },true,NeoButton.Style.STANDARD); }
    private void choose(PvpGuiRequestPayload.Action action) { choosing=action; page=Page.ARENA; status=""; rebuild(); }
    private void nextArena() { if(data==null || data.arenas().isEmpty()) return; int index=data.arenas().indexOf(arena); arena=data.arenas().get((index+1)%data.arenas().size()); }
    private void request(PvpGuiRequestPayload.Action action) {
        if(pending!=null) return;
        pending=UUID.randomUUID(); sentAt=age;
        pendingAction=action;
        if(action!=PvpGuiRequestPayload.Action.QUERY && action!=PvpGuiRequestPayload.Action.TOP) status="กำลังดำเนินการ...";
        PacketDistributor.sendToServer(new PvpGuiRequestPayload(pending,action,action==PvpGuiRequestPayload.Action.CREATE || action==PvpGuiRequestPayload.Action.QUEUE ? arena : ""));
        rebuild();
    }
    void receive(PvpGuiDataPayload payload) {
        if(!payload.request().equals(pending)) return;
        boolean joinedQueue=pendingAction==PvpGuiRequestPayload.Action.QUEUE && payload.queued();
        pending=null; data=payload; status=payload.message();
        if(joinedQueue) { onClose(); return; }
        if(!data.arenas().contains(arena)) arena=data.arenas().isEmpty() ? "" : data.arenas().getFirst();
        if(data.roomId()>0) {
            if(!data.state().equals("WAITING")) { onClose(); return; }
            page=Page.ROOM;
        } else if(page==Page.ROOM || data.queued()) page=Page.SELECT;
        rebuild();
    }
    @Override public void tick() {
        age++;
        if(pending!=null && age-sentAt>=100) { pending=null; status="การเชื่อมต่อล่าช้า กรุณาเปิดหน้าใหม่หรือลองทำรายการอีกครั้ง"; rebuild(); }
    }
    @Override public boolean isPauseScreen() { return false; }
    private void text(GuiGraphics g,String value,int center,int sourceY,int maxWidth,int color,float sourceHeight) {
        float zoom=sourceHeight/font.lineHeight;
        String trimmed=font.plainSubstrByWidth(value,(int)(maxWidth/zoom));
        g.pose().pushPose(); g.pose().translate(x(center),y(sourceY),0); g.pose().scale(scale*zoom,scale*zoom,1);
        g.drawString(font,trimmed,-font.width(trimmed)/2,0,color,false); g.pose().popPose();
    }
    private void icon(GuiGraphics g,ResourceLocation icon,int sourceX,int sourceY) {
        g.blit(icon,x(sourceX),y(sourceY),size(166),size(166),0,0,1254,1254,1254,1254);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g,mouseX,mouseY,partialTick);
        g.blit(FRAME,left,top,size(950),size(835),360,65,950,835,1672,941);
        text(g,"PvP 1vs1",835,97,370,0xFFE7ECEF,40);
        if(minecraft.player!=null) {
            PlayerFaceRenderer.draw(g,minecraft.player.getSkin().texture(),x(480),y(215),size(75));
            text(g,minecraft.player.getGameProfile().getName(),658,233,185,0xFFE7ECEF,30);
        }
        text(g,"คะแนน",897,215,145,0xFFE7ECEF,25);
        text(g,data==null || !data.ratingAvailable() ? "—" : String.format(java.util.Locale.ROOT,"%,d",data.rating()),897,252,145,0xFFF2C14E,34);
        text(g,"อันดับ",1130,215,145,0xFFE7ECEF,25);
        text(g,data==null || !data.ratingAvailable() ? "—" : data.position()==0 ? "ยังไม่มีอันดับ" : "#"+data.position(),1130,252,150,0xFFF2C14E,data!=null && data.position()==0 ? 20 : 34);
        if(page==Page.SELECT) {
            icon(g,SWORDS,545,346); icon(g,TROPHY,960,346);
            text(g,"สร้างห้อง",630,501,345,0xFFE7ECEF,34); text(g,"จัดอันดับ",1045,501,345,0xFFE7ECEF,34);
            text(g,"ท้าดวลกับเพื่อน",630,574,345,0xFFABB4BA,25); text(g,"ไม่คิดคะแนนจัดอันดับ",630,611,345,0xFFABB4BA,25);
            text(g,data!=null && data.queued() ? "กำลังค้นหาคู่ต่อสู้..." : "จับคู่ผู้เล่นที่คะแนนใกล้กัน",1045,574,345,0xFFABB4BA,25);
            text(g,"ชนะ 2 จาก 3 รอบ",1045,611,345,0xFFABB4BA,25);
        } else if(page==Page.ARENA) {
            text(g,"เลือกสนาม",630,380,340,0xFFE7ECEF,34); text(g,arena.isEmpty() ? "ยังไม่มีสนาม" : arena,630,437,340,0xFFF2C14E,27);
            text(g,choosing==PvpGuiRequestPayload.Action.CREATE ? "ห้องดวลส่วนตัว" : "Ranked 1vs1",1045,380,340,0xFFE7ECEF,34);
            text(g,choosing==PvpGuiRequestPayload.Action.CREATE ? "เชิญเพื่อนด้วยเลขห้อง" : "ระบบเลือกคู่ต่อสู้ให้",1045,447,340,0xFFABB4BA,25);
            text(g,choosing==PvpGuiRequestPayload.Action.CREATE ? "ไม่เพิ่มหรือลด Rating" : "ชนะ 2 รอบเพื่อชนะแมตช์",1045,492,340,0xFFABB4BA,25);
        } else if(page==Page.ROOM && data!=null) {
            text(g,"ห้อง #"+data.roomId(),630,375,340,0xFFE7ECEF,34);
            for(int i=0;i<data.members().size();i++) {
                var member=data.members().get(i);
                text(g,member.name()+(member.owner() ? " (หัวหน้า)" : ""),630,445+i*80,340,0xFFE7ECEF,25);
                text(g,member.ready() ? "พร้อม" : "ยังไม่พร้อม",630,476+i*80,340,member.ready() ? 0xFF2FB5A7 : 0xFFABB4BA,23);
            }
            text(g,"เชิญเพื่อนเข้าห้อง",1045,375,340,0xFFE7ECEF,30);
            text(g,"/neow pvp room join "+data.roomId(),1045,430,345,0xFFF2C14E,21);
            text(g,"พร้อมทั้งสองคนแล้วเริ่มได้",1045,477,340,0xFFABB4BA,23);
        } else if(page==Page.TOP && data!=null) {
            text(g,"ตารางอันดับ",630,367,340,0xFFE7ECEF,32);
            if(data.leaders().isEmpty()) text(g,"ยังไม่มีผล Ranked",630,435,340,0xFFABB4BA,25);
            for(int i=0;i<data.leaders().size();i++) {
                var leader=data.leaders().get(i); int column=i/5,row=i%5;
                text(g,"#"+(i+1)+" "+leader.name()+"   "+leader.rating(),column==0 ? 630 : 1045,425+row*52,345,i==0 ? 0xFFF2C14E : 0xFFE7ECEF,23);
            }
        }
        text(g,status.isEmpty() ? "ใช้อุปกรณ์และค่าสถานะจริง" : status,835,850,700,status.isEmpty() ? 0xFFABB4BA : 0xFFF2C14E,20);
        // Screen.render() draws the background again in this version, blurring the finished UI.
        for(var widget:renderables) widget.render(g,mouseX,mouseY,partialTick);
    }
}
