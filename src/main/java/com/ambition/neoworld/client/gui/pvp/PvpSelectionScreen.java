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
    private enum Page { SELECT, ARENA, ROOM, TOP, INVITE }
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
    private int invitePage;
    private final java.util.Map<UUID,net.minecraft.client.player.RemotePlayer> previews=new java.util.HashMap<>();
    private static final ResourceLocation ROOM_FRAME=texture("private_room_frame");
    private NeoButton fightButton;
    private final PvpArenaImages arenaImages=new PvpArenaImages();
    private com.ambition.neoworld.client.gui.widget.NeoScrollbar arenaScrollbar;
    private double arenaScroll;
    private static final ResourceLocation ARENA_FRAME=texture("create_room_frame");
    public PvpSelectionScreen() { super(Component.literal("PvP 1vs1")); }
    private static ResourceLocation texture(String id) { return ResourceLocation.fromNamespaceAndPath("neoworld","textures/gui/pvp/"+id+".png"); }
    @Override protected void init() {
        arenaImages.clear();
        rebuild(); if(data==null && pending==null) request(PvpGuiRequestPayload.Action.QUERY);
    }
    private int x(int source) { return left+Math.round((source-(page==Page.ARENA ? 220 : page==Page.ROOM ? 300 : 360))*scale); }
    private int y(int source) { return top+Math.round((source-(page==Page.ROOM || page==Page.ARENA ? 0 : 65))*scale); }
    private int size(int source) { return Math.max(1,Math.round(source*scale)); }
    private NeoButton button(String label,int sourceX,int sourceY,int w,int h,Runnable action,boolean enabled,NeoButton.Style style) {
        var button=addRenderableWidget(new NeoButton(Button.builder(Component.literal(label),ignored -> action.run())
                .bounds(x(sourceX),y(sourceY),size(w),size(h))).style(style));
        button.active=enabled; return button;
    }
    private void rebuild() {
        if(arenaScrollbar!=null) arenaScroll=arenaScrollbar.getValue()/scale;
        arenaScrollbar=null;
        int logicalWidth=page==Page.ARENA ? 1240 : page==Page.ROOM ? 1080 : 950,logicalHeight=page==Page.ROOM || page==Page.ARENA ? 920 : 835;
        scale=Math.min((width-16)/(float)logicalWidth,(height-16)/(float)logicalHeight);
        left=Math.round((width-logicalWidth*scale)/2); top=Math.round((height-logicalHeight*scale)/2);
        clearWidgets();
        button("X",page==Page.ARENA ? 1367 : page==Page.ROOM ? 1288 : 1248,page==Page.ROOM || page==Page.ARENA ? 78 : 123,49,49,this::onClose,true,NeoButton.Style.DESTRUCTIVE);
        boolean busy=pending!=null;
        if(page==Page.SELECT) {
            boolean free=data!=null && data.roomId()==0 && !data.queued();
            button("สร้างห้อง",452,654,349,82,() -> choose(PvpGuiRequestPayload.Action.CREATE),!busy && free && !data.arenas().isEmpty(),NeoButton.Style.PRIMARY);
            if(data!=null && data.queued()) button("ยกเลิกคิว",872,654,349,82,() -> request(PvpGuiRequestPayload.Action.UNQUEUE),!busy,NeoButton.Style.DESTRUCTIVE);
            else button("ค้นหาคู่ต่อสู้",872,654,349,82,() -> request(PvpGuiRequestPayload.Action.QUEUE),!busy && free && data.ratingAvailable() && !data.arenas().isEmpty(),NeoButton.Style.PRIMARY);
            button("ตารางอันดับ",674,773,327,54,() -> { page=Page.TOP; request(PvpGuiRequestPayload.Action.TOP); },!busy && data!=null && data.ratingAvailable(),NeoButton.Style.STANDARD);
        } else if(page==Page.ARENA) {
            arenaScrollbar=new com.ambition.neoworld.client.gui.widget.NeoScrollbar(x(768),y(200),size(28),size(555),Component.literal("เลื่อนรายการสนาม"));
            var list=addRenderableWidget(new PvpArenaList(x(310),y(200),size(446),size(555),size(180),size(5),
                    data==null ? java.util.List.of() : data.arenas(),arenaScrollbar,() -> arena,id -> { arena=id; status=""; },arenaImages));
            list.active=!busy;
            arenaScrollbar.value(arenaScroll*scale); arenaScrollbar.active=!busy;
            addRenderableWidget(arenaScrollbar);
            button("← กลับ",447,814,322,74,() -> { page=Page.SELECT; rebuild(); },!busy,NeoButton.Style.STANDARD);
            button(choosing==PvpGuiRequestPayload.Action.CREATE ? "สร้างห้อง" : "เข้าคิวจัดอันดับ",788,814,436,74,() -> request(choosing),!busy && data!=null && data.arenas().contains(arena),NeoButton.Style.PRIMARY);
        } else if(page==Page.ROOM) {
            boolean waiting=data!=null && data.state().equals("WAITING") && !data.ranked();
            button("เชิญผู้เล่น",618,666,438,90,() -> { invitePage=0; page=Page.INVITE; rebuild(); },!busy && waiting && data.owner() && data.members().size()<2,NeoButton.Style.STANDARD)
                    .setMessage(com.ambition.neoworld.client.gui.common.NeoGuiIcons.label(com.ambition.neoworld.client.gui.common.NeoGuiIcons.INVITE,"เชิญผู้เล่น"));
            fightButton=button("ต่อสู้",498,782,420,72,() -> request(PvpGuiRequestPayload.Action.START),!busy && waiting && data.owner() && data.canStart(),NeoButton.Style.PRIMARY);
            button("ออกจากห้อง",946,790,228,62,() -> request(PvpGuiRequestPayload.Action.LEAVE),!busy && data!=null && data.roomId()>0,NeoButton.Style.DESTRUCTIVE);
        } else if(page==Page.INVITE) {
            var players=inviteCandidates();
            int pages=Math.max(1,(players.size()+5)/6); invitePage=Math.min(invitePage,pages-1);
            for(int i=invitePage*6;i<Math.min(players.size(),invitePage*6+6);i++) {
                var player=players.get(i);
                button(player.getProfile().getName(),480,390+(i%6)*48,710,40,() -> {
                    page=Page.ROOM; request(PvpGuiRequestPayload.Action.INVITE,player.getProfile().getId().toString());
                },!busy,NeoButton.Style.STANDARD);
            }
            button("ก่อนหน้า",480,700,230,45,() -> { invitePage--; rebuild(); },!busy && invitePage>0,NeoButton.Style.STANDARD);
            button("ถัดไป",960,700,230,45,() -> { invitePage++; rebuild(); },!busy && invitePage+1<pages,NeoButton.Style.STANDARD);
            button("กลับห้อง",674,773,327,54,() -> { page=Page.ROOM; rebuild(); },!busy,NeoButton.Style.STANDARD);
        } else back();
    }
    private java.util.List<net.minecraft.client.multiplayer.PlayerInfo> inviteCandidates() {
        if(minecraft.getConnection()==null) return java.util.List.of();
        return minecraft.getConnection().getOnlinePlayers().stream().filter(p -> !p.getProfile().getId().equals(minecraft.player.getUUID()))
                .sorted(java.util.Comparator.comparing(p -> p.getProfile().getName(),String.CASE_INSENSITIVE_ORDER)).toList();
    }
    private void back() { button("กลับ",674,773,327,54,() -> { page=Page.SELECT; rebuild(); },true,NeoButton.Style.STANDARD); }
    private void choose(PvpGuiRequestPayload.Action action) { choosing=action; arenaScroll=0; arenaScrollbar=null; page=Page.ARENA; status=""; rebuild(); }
    private void request(PvpGuiRequestPayload.Action action) {
        request(action,action==PvpGuiRequestPayload.Action.CREATE ? arena : "");
    }
    private void request(PvpGuiRequestPayload.Action action,String argument) {
        if(pending!=null) return;
        pending=UUID.randomUUID(); sentAt=age;
        pendingAction=action;
        if(action!=PvpGuiRequestPayload.Action.QUERY && action!=PvpGuiRequestPayload.Action.TOP) status="กำลังดำเนินการ...";
        PacketDistributor.sendToServer(new PvpGuiRequestPayload(pending,action,argument));
        rebuild();
    }
    void receive(PvpGuiDataPayload payload) {
        boolean push=payload.request().equals(new UUID(0,0));
        if(push ? data==null || data.roomId()==0 : !payload.request().equals(pending)) return;
        boolean joinedQueue=!push && pendingAction==PvpGuiRequestPayload.Action.QUEUE && payload.queued();
        if(!push) pending=null;
        data=payload; status=payload.message(); previews.clear();
        if(joinedQueue) { onClose(); return; }
        if(!data.arenas().contains(arena)) arena=data.arenas().isEmpty() ? "" : data.arenas().getFirst();
        if(data.roomId()>0) {
            if(!data.state().equals("WAITING")) { onClose(); return; }
            if(page!=Page.INVITE || data.members().size()>=2) page=Page.ROOM;
        } else if(page==Page.ROOM || page==Page.INVITE || data.queued()) page=Page.SELECT;
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
    private void renderRoom(GuiGraphics g,float partialTick) {
        g.blit(ROOM_FRAME,left,top,size(1080),size(920),300,0,1080,920,1672,941);
        text(g,"ห้อง PvP 1vs1",835,47,420,0xFFE7ECEF,42);
        text(g,"ห้อง #"+data.roomId(),540,143,250,0xFFF2C14E,29);
        text(g,"สนาม: "+data.roomArena(),840,143,290,0xFFE7ECEF,27);
        text(g,"ผู้เล่น "+data.members().size()+"/2",1140,143,250,0xFFF2C14E,29);
        for(int i=0;i<2;i++) {
            int center=i==0 ? 579 : 1094;
            if(i>=data.members().size()) {
                text(g,"รอผู้เล่น",center,217,290,0xFFABB4BA,29);
                text(g,"เชิญคู่ต่อสู้เข้าห้อง",center,395,290,0xFFABB4BA,25);
                text(g,"คะแนน   —",center,557,280,0xFFABB4BA,27);
                text(g,"อันดับ   —",center,604,280,0xFFABB4BA,27);
                continue;
            }
            var member=data.members().get(i); var preview=member.preview();
            text(g,member.name(),center,216,290,0xFFE7ECEF,32);
            text(g,member.owner() ? "หัวหน้าห้อง" : "ผู้เล่น",center+(member.owner() ? 16 : 0),256,250,member.owner() ? 0xFFF2C14E : 0xFFABB4BA,23);
            if(member.owner()) {
                g.pose().pushPose(); g.pose().translate(x(center-87),y(252),0); g.pose().scale(scale*2,scale*2,1);
                g.drawString(font,com.ambition.neoworld.client.gui.common.NeoGuiIcons.icon(com.ambition.neoworld.client.gui.common.NeoGuiIcons.LEADER),0,0,0xFFF2C14E,false);
                g.pose().popPose();
            }
            if(preview!=null && minecraft.level!=null) {
                var entity=previews.computeIfAbsent(preview.id(),id -> {
                    var copy=new net.minecraft.client.player.RemotePlayer(minecraft.level,new com.mojang.authlib.GameProfile(id,preview.name()));
                    var slots=new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                            net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.HEAD};
                    for(int slot=0;slot<slots.length;slot++) copy.setItemSlot(slots[slot],preview.equipment().get(slot).copy());
                    return copy;
                });
                PvpPlayerPreview.render(g,entity,x(center-150),y(285),x(center+150),y(532),size(112),age+partialTick);
            }
            text(g,"คะแนน",center-90,557,110,0xFFE7ECEF,26);
            text(g,preview==null || preview.rating()<0 ? "—" : String.format(java.util.Locale.ROOT,"%,d",preview.rating()),center+87,557,120,0xFFF2C14E,29);
            text(g,"อันดับ",center-90,604,110,0xFFE7ECEF,26);
            String rank=preview==null || preview.position()<0 ? "—" : preview.position()==0 ? "ยังไม่มีอันดับ" : "#"+preview.position();
            text(g,rank,center+75,604,150,0xFFF2C14E,rank.equals("ยังไม่มีอันดับ") ? 20 : 29);
        }
        text(g,"VS",835,376,155,0xFFE7ECEF,77);
        text(g,status.isEmpty() ? "กดต่อสู้เพื่อให้ทั้งสองฝ่ายยืนยันภายใน 30 วินาที" : status,835,871,900,status.isEmpty() ? 0xFFABB4BA : 0xFFF2C14E,22);
    }
    private void renderArena(GuiGraphics g) {
        boolean privateRoom=choosing==PvpGuiRequestPayload.Action.CREATE;
        g.blit(ARENA_FRAME,left,top,size(1240),size(920),220,0,1240,920,1672,941);
        text(g,privateRoom ? "สร้างห้อง PvP" : "เลือกสนามจัดอันดับ",835,47,480,0xFFE7ECEF,42);
        text(g,"เลือกสนาม",550,146,430,0xFFE7ECEF,30);
        text(g,"ตัวอย่างสนามที่เลือก",1095,146,470,0xFFE7ECEF,30);
        if(!arena.isEmpty()) {
            var image=arenaImages.get(arena);
            PvpArenaImages.draw(g,image,x(844),y(202),size(503),size(253));
            if(image.illustrative()) text(g,"ภาพประกอบสนาม",1095,421,400,0xFFFFFFFF,20);
        } else text(g,"ยังไม่มีสนาม",1095,310,450,0xFFABB4BA,30);
        text(g,arena.isEmpty() ? "—" : arena,1095,481,450,0xFFE7ECEF,33);
        text(g,"รูปแบบ:",930,541,170,0xFFE7ECEF,27); text(g,"1vs1",1260,541,160,0xFFF2C14E,28);
        text(g,"ประเภท:",930,588,170,0xFFE7ECEF,27); text(g,privateRoom ? "ห้องส่วนตัว" : "จัดอันดับ",1230,588,220,0xFFF2C14E,27);
        text(g,"ผู้เล่นสูงสุด:",956,636,220,0xFFE7ECEF,27); text(g,"2 คน",1260,636,160,0xFFF2C14E,28);
        text(g,privateRoom ? "ไม่เพิ่มหรือลดคะแนนจัดอันดับ" : "ชนะ 2 จาก 3 ยกเพื่อชนะแมตช์",1095,694,470,0xFFE7ECEF,25);
        text(g,status.isEmpty() ? privateRoom ? "สร้างห้องแล้วเชิญผู้เล่นเข้าร่วมได้" : "ค้นหาคู่ต่อสู้ที่คะแนนใกล้เคียงกัน" : status,
                835,776,1050,status.isEmpty() ? 0xFFABB4BA : 0xFFF2C14E,24);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g,mouseX,mouseY,partialTick);
        if(page==Page.ARENA) {
            renderArena(g);
            for(var widget:renderables) widget.render(g,mouseX,mouseY,partialTick);
            float shade=pending==null && data!=null && data.arenas().contains(arena) ? 1F : 0.4F;
            g.setColor(shade,shade,shade,1);
            g.blit(SWORDS,x(885),y(826),size(48),size(48),0,0,1254,1254,1254,1254);
            g.setColor(1,1,1,1);
            return;
        }
        if(page==Page.ROOM && data!=null) {
            renderRoom(g,partialTick);
            for(var widget:renderables) widget.render(g,mouseX,mouseY,partialTick);
            float shade=fightButton!=null && fightButton.active ? 1F : 0.4F;
            g.setColor(shade,shade,shade,1);
            g.blit(SWORDS,x(596),y(792),size(52),size(52),0,0,1254,1254,1254,1254);
            g.setColor(1,1,1,1);
            return;
        }
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
        } else if(page==Page.INVITE) {
            text(g,"เชิญผู้เล่นออนไลน์",835,335,700,0xFFE7ECEF,30);
            if(inviteCandidates().isEmpty()) text(g,"ไม่มีผู้เล่นอื่นออนไลน์",835,450,700,0xFFABB4BA,27);
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
