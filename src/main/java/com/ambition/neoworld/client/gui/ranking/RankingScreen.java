package com.ambition.neoworld.client.gui.ranking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.ambition.neoworld.client.gui.common.NeoBitmapFonts;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.widget.NeoTitlePlaque;
import com.ambition.neoworld.client.gui.widget.NeoScrollbar;
import com.ambition.neoworld.network.ranking.RankingDataPayload;
import com.ambition.neoworld.network.ranking.RankingRequestPayload;
import com.ambition.neoworld.ranking.RankingTable.Category;
import com.ambition.neoworld.ranking.RankingTable.Entry;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Approved design A. Art, content and button hitboxes share one uniform source transform. */
public final class RankingScreen extends Screen {
    private static final int W=640,H=360,TEXT=0xFFE7ECEF,MUTED=0xFFABB4BA,GOLD=0xFFF2C14E;
    private static final ResourceLocation SHELL=ResourceLocation.fromNamespaceAndPath("neoworld","textures/gui/ranking/shell_full_body.png");
    private final java.util.Map<UUID,RankingPlayerPreview> models=new java.util.HashMap<>();
    private final Screen parent;
    private final List<NeoButton> navigationButtons=new ArrayList<>();
    private NeoButton refreshButton;
    private NeoScrollbar navigationScrollbar;
    private int navigationOffset;
    private RankingNavigation.Tab selectedTab=RankingNavigation.TABS.getFirst();
    private final NeoTitlePlaque heading=new NeoTitlePlaque(215,2,210,27,Component.literal("อันดับ"));
    private Category category=Category.PVP;
    private RankingDataPayload data;
    private UUID pending;
    private boolean started;
    private int left,top,age,sentAt,cooldown;
    private float scale;
    private String status="กำลังโหลดข้อมูล...";

    public RankingScreen(Screen parent) { super(Component.literal("อันดับ")); this.parent=parent; }
    public static void open() { open(null); }
    public static void open(Screen parent) { Minecraft.getInstance().setScreen(new RankingScreen(parent)); }
    public static void receive(RankingDataPayload response) {
        if(Minecraft.getInstance().screen instanceof RankingScreen screen) screen.update(response);
    }
    @Override protected void init() {
        scale=Math.max(0.1F,Math.min((width-8F)/W,(height-8F)/H));
        left=Math.round((width-W*scale)/2); top=Math.round((height-H*scale)/2);
        navigationButtons.clear();
        button(Component.literal("×"),582,25,18,18,this::onClose).style(NeoButton.Style.DESTRUCTIVE)
                .setTooltip(Tooltip.create(Component.literal("ปิดหน้าจัดอันดับ")));
        for(var tab:RankingNavigation.TABS) {
            var button=button(tab.category()==null ? Component.literal(tab.title()) : label(tab.category()),43,62,102,28,() -> select(tab));
            if(tab.category()==null) button.setTooltip(Tooltip.create(Component.literal("ยังไม่พร้อมใช้งาน")));
            navigationButtons.add(button);
        }
        refreshButton=button(Component.literal("รีเฟรช"),47,301,104,23,this::request);
        navigationScrollbar=addRenderableWidget(new NeoScrollbar(x(150),y(62),size(5),size(164),Component.literal("เลื่อนหมวดอันดับ"))
                .range(RankingNavigation.VISIBLE_ROWS,RankingNavigation.TABS.size()).scrollStep(1).value(navigationOffset)
                .onChange(value -> { navigationOffset=RankingNavigation.clampOffset((int)Math.round(value)); enableButtons(); }));
        enableButtons();
        if(!started) { started=true; request(); }
    }
    private NeoButton button(Component label,int x,int y,int w,int h,Runnable action) {
        return addRenderableWidget(new NeoButton(Button.builder(label,b -> action.run())
                .bounds(x(x),y(y),size(w),size(h))).uniformTextScale(scale).textScrolling(false));
    }
    private static Component label(Category tab) {
        return switch(tab) {
            case PVP -> NeoBitmapFonts.text("\uE010 อันดับ PvP");
            case LEVEL -> NeoBitmapFonts.text("\uE00B อันดับ Lv.");
            case SILVER -> NeoBitmapFonts.text("\uE046 อันดับเงิน");
        };
    }
    private void select(RankingNavigation.Tab next) {
        if(next.equals(selectedTab) || (next.category()!=null && cooldown>0)) return;
        selectedTab=next; data=null; pending=null; models.clear();
        if(next.category()==null) { status="ยังไม่พร้อมใช้งาน"; enableButtons(); }
        else { category=next.category(); request(); }
    }
    private void request() {
        if(selectedTab.category()==null || cooldown>0 || minecraft==null || minecraft.getConnection()==null) return;
        pending=UUID.randomUUID(); sentAt=age; cooldown=12; data=null;
        status="กำลังโหลดข้อมูล..."; models.clear(); enableButtons();
        PacketDistributor.sendToServer(new RankingRequestPayload(pending,category));
    }
    private void update(RankingDataPayload response) {
        if(!response.request().equals(pending) || response.category()!=category) return;
        pending=null;
        if(!response.error().isEmpty()) { data=null; status=response.error(); }
        else { data=response; status=response.view().leaders().isEmpty() ? "ยังไม่มีผู้เล่นในหมวดนี้" : ""; }
        enableButtons();
    }
    private void enableButtons() {
        for(int i=0;i<navigationButtons.size();i++) {
            var button=navigationButtons.get(i); var tab=RankingNavigation.TABS.get(i);
            button.visible=RankingNavigation.visible(i,navigationOffset);
            button.setY(y(62+(i-navigationOffset)*34));
            button.active=tab.category()==null || cooldown==0;
            button.selected(tab.equals(selectedTab)).style(tab.equals(selectedTab) ? NeoButton.Style.PRIMARY : NeoButton.Style.STANDARD);
        }
        if(refreshButton!=null) refreshButton.active=selectedTab.category()!=null && cooldown==0 && pending==null;
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical) {
        if(navigationScrollbar!=null && mouseX>=x(40) && mouseX<x(157) && mouseY>=y(60) && mouseY<y(230))
            return navigationScrollbar.scroll(vertical!=0 ? vertical : horizontal);
        return super.mouseScrolled(mouseX,mouseY,horizontal,vertical);
    }
    @Override public void tick() {
        age++;
        if(cooldown>0 && --cooldown==0) enableButtons();
        if(pending!=null && age-sentAt>=200) {
            pending=null; status="หมดเวลารอข้อมูล กดรีเฟรชเพื่อลองใหม่"; enableButtons();
        }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { models.clear(); minecraft.setScreen(parent); }
    @Override public void removed() { models.clear(); }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        renderBackground(g,mouseX,mouseY,partialTick);
        g.pose().pushPose();
        g.pose().translate(left,top,0); g.pose().scale(scale,scale,1);
        RenderSystem.enableBlend(); g.setColor(1,1,1,1);
        if(selectedTab.category()==null || category==Category.LEVEL) RankingLevelTable.backdrop(g);
        else g.blit(SHELL,0,0,W,H,0,0,1673,940,1673,940);
        heading.render(g,font);
        var leaders=data==null ? List.<Entry>of() : data.view().leaders();
        Entry own=data==null ? null : data.view().personal();
        if(selectedTab.category()==null) {
            text(g,selectedTab.title(),185,113,385,TEXT);
            text(g,"ยังไม่พร้อมใช้งาน",185,145,385,GOLD);
            text(g,"หมวดนี้ยังไม่ได้เชื่อมข้อมูลอันดับ",185,165,385,MUTED);
            text(g,"ยังไม่มีข้อมูลอันดับสำหรับหมวดนี้",185,320,385,MUTED);
        } else if(category==Category.LEVEL) {
            RankingLevelTable.render(g,font,leaders,own,data==null ? 0 : data.view().position(),status);
        } else {
            text(g,category==Category.PVP ? "คะแนน" : category==Category.LEVEL ? "เลเวล" : "เหรียญเงิน",398,RankingLayout.TABLE_HEADER,105,TEXT);
            text(g,"อันดับ",168,RankingLayout.TABLE_HEADER,35,MUTED); text(g,"ผู้เล่น",221,RankingLayout.TABLE_HEADER,161,MUTED);
            text(g,category==Category.PVP ? "ชนะ / แพ้" : category==Category.LEVEL ? "EXP รวม" : "",510,RankingLayout.TABLE_HEADER,78,MUTED);
            podium(g,leaders,1,228,0xFFCBD7E5); podium(g,leaders,0,358,GOLD); podium(g,leaders,2,495,0xFFD99D77);
            text(g,"รูปลักษณ์ที่บันทึกประจำวัน",200,209,372,MUTED);
            for(int i=3;i<leaders.size();i++) {
                Entry entry=leaders.get(i); int rowY=RankingLayout.ROW_FIRST+(i-3)*RankingLayout.ROW_STEP;
                if(i%2==1) g.fill(166,rowY-1,589,rowY+9,0x193D7774);
                text(g,""+(i+1),168,rowY,35,TEXT); face(g,entry,210,rowY,8);
                text(g,entry.name(),223,rowY,160,TEXT);
                text(g,value(entry),393,rowY,112,GOLD); text(g,detail(entry),510,rowY,78,MUTED);
            }
            if(!status.isEmpty()) text(g,status,175,263,404,MUTED);
            text(g,"อันดับของคุณ",169,RankingLayout.PERSONAL_Y,83,TEXT);
            text(g,data==null ? "—" : data.view().position()==0 ? "ยังไม่มีอันดับ" : "#"+data.view().position(),254,RankingLayout.PERSONAL_Y,74,GOLD);
            if(own!=null) {
                face(g,own,331,RankingLayout.PERSONAL_Y-2,12); text(g,own.name(),349,RankingLayout.PERSONAL_Y,105,TEXT); text(g,value(own),464,RankingLayout.PERSONAL_Y,116,GOLD);
            }
        }
        text(g,"เงิน: เฉพาะเหรียญเงิน",42,245,116,MUTED);
        text(g,"Lv. / EXP ปัจจุบัน",42,258,116,MUTED);
        g.pose().popPose();
        for(var widget:renderables) widget.render(g,mouseX,mouseY,partialTick);
        // Full values stay available when a large balance/name is fitted into a compact row.
        if(data!=null) {
            float mx=(mouseX-left)/scale,my=(mouseY-top)/scale;
            Entry hovered=null;
            if(category==Category.LEVEL) {
                int index=RankingLayout.levelRowAt(my);
                if(mx>=166 && mx<590 && index>=0 && index<leaders.size()) hovered=leaders.get(index);
            } else {
                if(mx>=166 && mx<590 && my>=RankingLayout.ROW_FIRST-1 && my<RankingLayout.ROW_FIRST+7*RankingLayout.ROW_STEP-1) {
                    int index=3+(int)((my-(RankingLayout.ROW_FIRST-1))/RankingLayout.ROW_STEP);
                    if(index<leaders.size()) hovered=leaders.get(index);
                }
                for(int i=0;i<Math.min(3,leaders.size());i++) {
                    var region=RankingLayout.MODELS.get(i);
                    if(mx>=region.x() && mx<region.x()+region.width() && my>=region.y() && my<203) hovered=leaders.get(i);
                }
            }
            if(mx>=162 && mx<592 && my>=311 && my<332) hovered=own;
            if(hovered!=null) {
                final var selected=hovered;
                var snapshot=data.previews().stream().filter(p -> p.id().equals(selected.id())).findFirst();
                String date=snapshot.filter(p -> p.appearance().day()>=0)
                        .map(p -> " • บันทึก "+java.time.LocalDate.ofEpochDay(p.appearance().day())).orElse("");
                g.renderTooltip(font,Component.literal(hovered.name()+" • "+value(hovered)+unit()+
                        (detail(hovered).isEmpty() ? "" : " • "+detail(hovered))+date),mouseX,mouseY);
            }
        }
    }
    private void podium(GuiGraphics g,List<Entry> leaders,int index,int center,int color) {
        text(g,""+(index+1),center-13,index==0 ? 43 : 49,26,color);
        if(index>=leaders.size()) { text(g,"ยังไม่มีผู้เล่น",center-45,115,90,MUTED); return; }
        Entry entry=leaders.get(index);
        var appearance=data.previews().stream().filter(p -> p.id().equals(entry.id())).map(RankingDataPayload.Preview::appearance).findFirst().orElse(null);
        if(appearance!=null && appearance.day()>=0 && minecraft.level!=null) {
            var model=models.computeIfAbsent(entry.id(),id -> new RankingPlayerPreview(minecraft.level,entry,appearance));
            model.renderPreview(g,RankingLayout.MODELS.get(index),age);
            if(minecraft.getConnection()!=null && minecraft.getConnection().getPlayerInfo(entry.id())==null)
                text(g,"ออฟไลน์",center+13,66,35,MUTED);
        } else text(g,"ยังไม่มีภาพประจำวัน",center-47,118,94,MUTED);
        text(g,entry.name(),center-49,RankingLayout.NAME_Y,98,TEXT);
        text(g,value(entry)+unit(),center-49,183,98,color); text(g,detail(entry),center-49,194,98,MUTED);
    }
    private void face(GuiGraphics g,Entry entry,int x,int y,int size) {
        var info=minecraft.getConnection()==null ? null : minecraft.getConnection().getPlayerInfo(entry.id());
        var skin=info==null ? minecraft.getSkinManager().getInsecureSkin(new GameProfile(entry.id(),entry.name())) : info.getSkin();
        PlayerFaceRenderer.draw(g,skin.texture(),x,y,size);
    }
    private String value(Entry e) {
        return switch(category) { case PVP -> number(e.rating()); case LEVEL -> "Lv. "+e.level(); case SILVER -> number(e.silver()); };
    }
    private String detail(Entry e) {
        return switch(category) { case PVP -> number(e.wins())+" / "+number(e.losses()); case LEVEL -> number(e.totalExp())+" EXP"; case SILVER -> ""; };
    }
    private String unit() { return category==Category.PVP ? " คะแนน" : category==Category.SILVER ? " เหรียญ" : ""; }
    private static String number(long number) { return String.format(Locale.US,"%,d",number); }
    private void text(GuiGraphics g,String value,int x,int y,int width,int color) {
        float fit=Math.min(1F,width/(float)Math.max(1,font.width(value)));
        g.pose().pushPose();
        g.pose().translate(x+(width-font.width(value)*fit)/2F,y,0); g.pose().scale(fit,fit,1);
        g.drawString(font,value,0,0,color,false); g.pose().popPose();
    }
    private int x(int value) { return left+Math.round(value*scale); }
    private int y(int value) { return top+Math.round(value*scale); }
    private int size(int value) { return Math.max(1,Math.round(value*scale)); }
}
