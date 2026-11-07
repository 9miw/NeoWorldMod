package com.ambition.neoworld.client.gui.player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.player.ProfileDetails;
import com.ambition.neoworld.client.gui.widget.NeoScrollbar;
import net.minecraft.util.FormattedCharSequence;
import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.common.NeoSystemIcons;
import com.ambition.neoworld.client.gui.common.NeoBitmapIcon;
import com.ambition.neoworld.client.gui.title.TitleScreenController;
import com.ambition.neoworld.network.player.PlayerActionPayload;
import com.ambition.neoworld.network.player.PlayerActionResultPayload;
import com.ambition.neoworld.network.PlayerProfilePayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Four-tab read-only server snapshot; the detached preview cannot modify live equipment. */
public final class PlayerProfileScreen extends Screen {
    private static final int W = 620, H = 350;


    private final PlayerProfilePayload data;
    private ProfileDetails.Pvp pvp;
    private UUID pending;
    private int waiting, tab;
    private int selectedGear, selectedSkill, skillScroll, detailScroll;
    private NeoScrollbar skillScrollbar, detailScrollbar;
    private ProfilePlayerPreview preview;
    private static final int SKILL_ROWS=6, DETAIL_ROWS=17;
    private float scale;
    private int x, y;

    private static final int EXP_COLOR=0xFFE2B551,HP_COLOR=0xFFEF4554,STAMINA_COLOR=0xFF49BC76;
    public PlayerProfileScreen(PlayerProfilePayload data) {
        super(Component.literal("โปรไฟล์ผู้เล่น")); this.data = data; this.pvp=data.details().pvp();
    }
    public static void receive(PlayerProfilePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        // Async ranking results update only their original still-open screen, including beneath a popup.
        if(!payload.details().pvp().loading()) {
            var current=NeoPopupScreen.findScreen(PlayerProfileScreen.class);
            if(current!=null && current.data.sameSnapshot(payload)) current.pvp=payload.details().pvp();
            return;
        }
        if (payload.requestId().equals(PlayerProfilePayload.COMMAND_REQUEST) && mc.player != null
                && payload.targetId().equals(mc.player.getUUID())) mc.setScreen(new PlayerProfileScreen(payload));
        else if (mc.screen instanceof PlayerActionScreen actions && actions.acceptsProfile(payload))
            mc.setScreen(new PlayerProfileScreen(payload));
    }
    public static boolean receiveError(PlayerActionResultPayload result) {
        PlayerProfileScreen screen = NeoPopupScreen.findScreen(PlayerProfileScreen.class);
        if (screen == null || !result.targetId().equals(screen.data.targetId()) || !result.requestId().equals(screen.pending)) return false;
        screen.pending = null; screen.rebuildWidgets();
        NeoPopupScreen.showAlert(Component.literal(result.success() ? "ปาร์ตี้" : "ทำรายการไม่ได้"), Component.literal(result.message()), () -> {});
        return true;
    }
    private boolean ownProfile() { return data.details().owner(); }
    @Override protected void init() {

        scale = Math.max(0.1F, Math.min(1.5F, Math.min((width - 20F) / (W + 24), (height - 16F) / H)));
        x = (width - Math.round((W + 24) * scale)) / 2; y = (height - Math.round(H * scale)) / 2;
        button("×", W + 3, 20, 20, 20, () -> onClose()).style(NeoButton.Style.DESTRUCTIVE);
        String[] tabs = {"ภาพรวม", "ค่าพลังต่อสู้", "อุปกรณ์และแฟชั่น", "สกิล"};
        int[] tabX={166,264,382,532},tabWidth={92,112,144,68};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            button(tabs[i], tabX[i], 48, tabWidth[i], 25, () -> { tab = index; rebuildWidgets(); })
                    .selected(tab == i);
        }
        if (tab == 0 && ownProfile()) {
            button("จัดการฉายา", 388, 275, 104, 26, () -> TitleScreenController.open());
            button("เปิดหน้าแฟชั่น", 496, 275, 104, 26, () -> {
                if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("neow fashion open");
            });
        } else if (tab == 0) {
            button("เชิญเข้าปาร์ตี้", 388, 275, 212, 26, () -> {
                if (pending != null) return;
                pending = UUID.randomUUID(); waiting = 0;
                PacketDistributor.sendToServer(new PlayerActionPayload(pending, data.targetId(), PlayerActionPayload.Action.INVITE));
                rebuildWidgets();
            }).active = pending == null;
        }
        skillScrollbar=null; detailScrollbar=null;
        if(tab==2) {
            for(int i=0;i<data.details().gear().size();i++) {
                final int index=i;
                int[] pos=gearPosition(i);
                addRenderableWidget(new NeoButton(Button.builder(Component.empty(),b -> {selectedGear=index;detailScroll=0;rebuildWidgets();})
                        .bounds(px(pos[0]),py(pos[1]),size(40),size(40))
                        .createNarration(ignored -> Component.literal(gearLabel(data.details().gear().get(index).slot()))))
                        .frameRenderer((g,b) -> { if(b.isFocused()) g.renderOutline(b.getX(),b.getY(),b.getWidth(),b.getHeight(),0xFF2FB5A7); }));
            }
        }
        if(tab==3 && !data.details().skills().isEmpty()) {
            selectedSkill=Math.clamp(selectedSkill,0,data.details().skills().size()-1);
            skillScroll=Math.clamp(skillScroll,0,Math.max(0,data.details().skills().size()-SKILL_ROWS));
            for(int i=0;i<SKILL_ROWS && skillScroll+i<data.details().skills().size();i++) {
                final int rowIndex=i;
                int sy=108+i*32;
                addRenderableWidget(new NeoButton(Button.builder(Component.empty(),b -> {selectedSkill=skillScroll+rowIndex;detailScroll=0;rebuildWidgets();})
                        .bounds(px(166),py(sy),size(164),size(30))
                        .createNarration(ignored -> Component.translatable(data.details().skills().get(skillScroll+rowIndex).key())))
                        .frameRenderer((g,b) -> { if(b.isFocused()) g.renderOutline(b.getX(),b.getY(),b.getWidth(),b.getHeight(),0xFF2FB5A7); }));
            }
            if(data.details().skills().size()>SKILL_ROWS) skillScrollbar=addRenderableWidget(new NeoScrollbar(px(332),py(108),size(6),size(192),Component.literal("เลื่อนรายการสกิล"))
                    .range(SKILL_ROWS,data.details().skills().size()).scrollStep(1).value(skillScroll).onChange(v -> skillScroll=(int)Math.round(v)));
        }
        if(tab==2 || tab==3) {
            int count=detailLines().size();
            detailScroll=Math.clamp(detailScroll,0,Math.max(0,count-DETAIL_ROWS));
            if(count>DETAIL_ROWS) detailScrollbar=addRenderableWidget(new NeoScrollbar(px(594),py(tab==3 ? 134 : 126),size(6),size(170),Component.literal("เลื่อนรายละเอียด"))
                    .range(DETAIL_ROWS,count).scrollStep(1).value(detailScroll).onChange(v -> detailScroll=(int)Math.round(v)));
        }
    }
    private NeoButton button(String label, int bx, int by, int bw, int bh, Runnable action) {
        // Keep NeoButton's vanilla input/focus/narration; paint feature art and a scaled label once.
        return addRenderableWidget(new NeoButton(Button.builder(Component.empty(), b -> action.run())
                .bounds(px(bx), py(by), size(bw), size(bh))
                .createNarration(supplier -> Component.literal(label))
                .tooltip(Tooltip.create(Component.literal(label))))
                .frameRenderer((g, b) -> renderButton(g, b, label)));
    }
    private void renderButton(GuiGraphics g, NeoButton b, String label) {
        int bw = Math.round(b.getWidth() / scale), bh = Math.round(b.getHeight() / scale);
        boolean navigation=label.equals("ภาพรวม") || label.equals("ค่าพลังต่อสู้") || label.equals("อุปกรณ์และแฟชั่น") || label.equals("สกิล") || label.equals("×");
        int state = !b.active ? 3 : b.isSelected() ? 2 : b.isHoveredOrFocused() ? 1 : 0;
        g.pose().pushPose(); g.pose().translate(b.getX(), b.getY(), 0); g.pose().scale(scale, scale, 1);
        if (b.getStyle() == NeoButton.Style.DESTRUCTIVE && b.isHoveredOrFocused()) g.setColor(1,0.4F,0.4F,1);
        ProfileSkin.frame(g,(navigation ? ProfileSkin.TABS : ProfileSkin.BUTTONS)[state],0,0,bw,bh,5);
        g.setColor(1,1,1,1);
        int left = 9, right = 9;
        if (label.equals("ภาพรวม")) { NeoSystemIcons.render(g, NeoSystemIcons.Icon.PROFILE, 12, (bh-12)/2, 12, b.active); left=31; }
        else if (label.equals("อุปกรณ์และแฟชั่น")) { NeoSystemIcons.render(g, NeoSystemIcons.Icon.FASHION, 12, (bh-12)/2, 12, b.active); left=31; }
        else if (label.equals("ค่าพลังต่อสู้")) { ProfileSkin.icon(g, ProfileSkin.SWORDS, 12, (bh-13)/2, 13); left=33; }
        else if (label.equals("สกิล")) { NeoSystemIcons.render(g, NeoSystemIcons.Icon.STATISTICS, 12, (bh-12)/2, 12, b.active); left=32; }
        else if (label.equals("จัดการฉายา")) { NeoSystemIcons.render(g, NeoSystemIcons.Icon.TITLES, 12, (bh-12)/2, 12, b.active); left=31; }
        else if (label.equals("เปิดหน้าแฟชั่น")) { NeoSystemIcons.render(g, NeoSystemIcons.Icon.FASHION, 12, (bh-12)/2, 12, b.active); left=31; }
        else if (label.equals("เชิญเข้าปาร์ตี้")) { NeoSystemIcons.render(g, NeoSystemIcons.Icon.PARTY, 12, (bh-12)/2, 12, b.active); left=31; }
        if (bw < 30) { left=4; right=4; }
        int available=Math.max(1,bw-left-right);
        float textScale=Math.min(1F,available/(float)Math.max(1,font.width(label)));
        int textWidth=Math.round(font.width(label)*textScale);
        fitText(g,label,left+(available-textWidth)/2,(int)((bh-font.lineHeight*textScale)/2),Math.min(available,font.width(label)),
                !b.active ? 0xFF777F85 : b.isSelected() || !navigation ? 0xFFE3BC67 : 0xFFDDE5E7);
        g.pose().popPose();
    }
    private int px(int n) { return x + Math.round(n * scale); }
    private int py(int n) { return y + Math.round(n * scale); }
    private int size(int n) { return Math.max(1, Math.round(n * scale)); }
    @Override public void tick() {
        if (pending != null && ++waiting >= 200) {
            pending = null; rebuildWidgets();
            NeoPopupScreen.showAlert(Component.literal("หมดเวลารอ"), Component.literal("ไม่ได้รับคำตอบจากเซิร์ฟเวอร์"), () -> {});
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        float mx = (mouseX - x) / scale, my = (mouseY - y) / scale;
        List<Component> tooltip = null;
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        RenderSystem.enableBlend();
        var shell=ProfileSkin.SHELL;
        g.blit(shell.texture(),0,0,W,H,0,0,shell.width(),shell.height(),shell.width(),shell.height());
        g.pose().pushPose(); g.pose().translate(W / 2F, 18, 0); g.pose().scale(1.35F, 1.35F, 1); g.drawCenteredString(font, title, 0, 0, 0xFFE3BC67); g.pose().popPose();
        ProfileSkin.frame(g,ProfileSkin.PORTRAIT,18,48,136,270,6);
        if(minecraft.level!=null) {
            if(preview==null || preview.level()!=minecraft.level) {
                var live=minecraft.level.getPlayerByUUID(data.targetId());
                preview=new ProfilePlayerPreview(minecraft.level,live==null ? new com.mojang.authlib.GameProfile(data.targetId(),data.name()) : live.getGameProfile(),data);
            }
            preview.tickCount=waiting;
            preview.render(g,mx,my);
        } else text(g,"ตัวละครไม่พร้อมใช้งาน",26,130,120,NeoGuiStyle.COLOR_TEXT_MUTED);
        panel(g,24,250,124,62);
        int nameColor=data.killerSin()>0 ? 0xFFFF5555 : NeoGuiStyle.COLOR_TEXT;
        g.drawCenteredString(font,font.plainSubstrByWidth(data.name(),108),86,258,nameColor);
        g.drawCenteredString(font,"Lv. "+data.level(),86,278,0xFFE3BC67);
        if(!data.details().title().isEmpty()) g.drawCenteredString(font,font.plainSubstrByWidth(data.details().title(),108),86,300,0xFF000000|data.details().titleColor());
        if(inside(mx,my,24,252,124,62)) tooltip=List.of(Component.literal(data.name()),Component.literal(data.details().title()));
        if(tab==0) {
            panel(g,166,80,434,90);heading(g,"สถานะตัวละคร",166,80,434);
            bar(g,"HP",number(data.health())+" / "+number(data.maxHealth()),ratio(data.health(),data.maxHealth()),180,106,HP_COLOR);
            bar(g,"Stamina",data.maxStamina()<0 ? "ไม่พร้อมใช้งาน" : number(data.stamina())+" / "+number(data.maxStamina()),ratio(data.stamina(),data.maxStamina()),180,128,data.maxStamina()<0 ? 0xFF596971 : STAMINA_COLOR);
            if(ownProfile()) {
                boolean max=data.level()>=com.ambition.neoworld.level.LevelFormula.MAX_LEVEL;
                bar(g,"EXP",max ? "MAX" : data.exp()+" / "+data.requiredExp(),max ? 1 : ratio(data.exp(),data.requiredExp()),180,150,EXP_COLOR);
            }
            var p=pvp;
            boolean ranked=p.available() && (long)p.wins()+p.losses()>0;
            String status=p.loading() ? " · กำลังโหลด…" : !p.available() ? " · ข้อมูลไม่พร้อม" : !ranked ? " · ยังไม่จัดอันดับ" : "";
            heading(g,"PvP จัดอันดับ"+status,166,176,434);
            String[] labels={"Rating","อันดับ","ชนะ","แพ้","อัตราชนะ"};
            NeoBitmapIcon[] icons={NeoBitmapIcon.ICONS_MEDAL_GOLD,NeoBitmapIcon.ICONS_MENU_STATISTICS,
                    NeoBitmapIcon.ICONS_YES,NeoBitmapIcon.ICONS_NO,NeoBitmapIcon.ICONS_CLOVER};
            String[] values={ranked ? ""+p.rating() : "—",ranked && p.position()>0 ? ""+p.position() : "—",
                    p.available() ? ""+p.wins() : "—",p.available() ? ""+p.losses() : "—",ranked ? number(p.winRate())+"%" : "—"};
            for(int i=0;i<5;i++) {
                int cx=166+i*87;
                ProfileSkin.frame(g,ProfileSkin.CARD,cx,204,82,62,5);
                g.drawCenteredString(font,labels[i],cx+41,212,NeoGuiStyle.COLOR_TEXT);
                catalogueIcon(g,icons[i],cx+33,226,16);
                g.drawCenteredString(font,values[i],cx+41,250,i==2 ? 0xFF77D69A : i==3 ? 0xFFED7780 : 0xFFE3BC67);
            }
            panel(g,166,275,210,26);
            g.drawString(font,NeoBitmapIcon.ICONS_FIRE.component(),178,283,0xFFFFFFFF,false);
            text(g,"ค่าบาป: "+data.killerSin()+(data.killerSin()>0 ? " · หัวแดง" : " · ปกติ"),194,284,168,data.killerSin()>0 ? 0xFFFF7777 : 0xFF77D69A);
        } else if(tab==1) {
            tooltip=group(g,"การโจมตี",166,80,mx,my,"attack_damage","attack_speed","armor_negation","impact","max_strikes");
            var right=group(g,"การป้องกัน",390,80,mx,my,"armor","armor_toughness","knockback_resistance","stun_armor");
            if(right!=null) tooltip=right;
            var lower=group(g,"การเคลื่อนไหวและความอึด",166,216,mx,my,"movement_speed","weight","stamina_regen");
            if(lower!=null) tooltip=lower;
            panel(g,390,216,210,88);heading(g,"การอ่านค่าพลัง",390,216,210);
            drawWrapped(g,"ค่าตามอุปกรณ์ ณ ตอนเปิดหน้า\nโบนัสคริติคอล/ดูดเลือดและเงื่อนไขอยู่ในแท็บสกิล\nบางสกิลอาจเปลี่ยนการฟื้น Stamina",400,246,188,5);
        } else if(tab==2) {
            heading(g,"ชุดเกราะ",166,80,266);
            heading(g,"แฟชั่นที่สวม",166,176,266);
            for(int i=0;i<data.details().gear().size();i++) {
                var gear=data.details().gear().get(i);int[] pos=gearPosition(i);
                var tip=item(g,gear.stack(),gearLabel(gear.slot()),pos[0],pos[1],mx,my);
                if(i==selectedGear) g.renderOutline(pos[0]-1,pos[1]-1,42,42,0xFFE3BC67);
                if(tip!=null) tooltip=tip;
            }
            panel(g,444,80,156,224);heading(g,"รายละเอียด",444,80,156);
            if(!data.details().gear().isEmpty()) {
                var item=data.details().gear().get(Math.clamp(selectedGear,0,data.details().gear().size()-1)).stack();
                if(!item.isEmpty()) g.renderItem(item,454,107);
                renderDetail(g,454,126,132);
            } else text(g,"ข้อมูลไม่พร้อม",454,116,130,NeoGuiStyle.COLOR_TEXT_MUTED);
        } else {
            heading(g,"สกิลที่สวม",166,80,172);heading(g,"รายละเอียดสกิล",348,80,252);
            if(data.details().skills().isEmpty()) text(g,"ไม่มีข้อมูลสกิลที่สวม",176,115,410,NeoGuiStyle.COLOR_TEXT_MUTED);
            for(int i=0;i<SKILL_ROWS && skillScroll+i<data.details().skills().size();i++) {
                int index=skillScroll+i,sy=108+i*32;var skill=data.details().skills().get(index);
                row(g,166,sy,164,30,index==selectedSkill || inside(mx,my,166,sy,164,30));
                skillIcon(g,skill,176,sy+7,16);
                fitText(g,Component.translatable(skill.key()).getString(),198,sy+6,120,skill.enabled() ? 0xFFE3BC67 : NeoGuiStyle.COLOR_TEXT_MUTED);
                text(g,skillSlot(skill.slot()),198,sy+18,120,NeoGuiStyle.COLOR_TEXT_MUTED);
            }
            panel(g,348,106,252,198);
            if(!data.details().skills().isEmpty()) {
                var selected=data.details().skills().get(selectedSkill);
                skillIcon(g,selected,358,110,14);
                fitText(g,Component.translatable(selected.key()).getString(),380,113,202,0xFFE3BC67);
                renderDetail(g,358,134,228);
            }
        }
        // Keep the footer above the outer bevel and outside the expanded right-hand details.
        fitText(g, tab == 1 ? "ค่าตามอุปกรณ์ ณ ตอนเปิด · ชี้เพื่อดูรายละเอียด" : "ข้อมูล ณ ตอนเปิดหน้า · PvP ใช้แคช", 166, 312, 210, NeoGuiStyle.COLOR_TEXT_MUTED);
        g.pose().popPose();
        for (var widget : renderables) widget.render(g, mouseX, mouseY, partialTick);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }
    private void heading(GuiGraphics g, String title, int hx, int hy, int width) {
        ProfileSkin.frame(g, ProfileSkin.SECTION, hx, hy, width, 23, 6);
        ProfileSkin.Sprite icon = title.equals("การโจมตี") ? ProfileSkin.SWORDS
                : title.equals("การป้องกัน") ? ProfileSkin.SHIELD
                : title.equals("การเคลื่อนไหวและความอึด") ? ProfileSkin.BOOT : null;
        int inset=12;
        if(icon!=null) { ProfileSkin.icon(g,icon,hx+12,hy+5,13); inset=33; }
        fitText(g,title,hx+inset,hy+7,width-inset-14,0xFFE3BC67);
    }
    private List<Component> group(GuiGraphics g, String name, int gx, int gy, float mx, float my, String... keys) {
        ProfileSkin.frame(g,ProfileSkin.PANEL,gx-2,gy-2,214,30+keys.length*20,5);
        heading(g, name, gx, gy, 210);
        List<Component> tooltip = null; int index = 0;
        for (String key : keys) {
            var found = data.stats().stream().filter(s -> s.key().endsWith("." + key)).findFirst();
            if (found.isEmpty()) continue;
            var stat = found.get(); int ry = gy + 26 + index++ * 20;
            boolean hover = inside(mx, my, gx, ry, 210, 19);
            row(g, gx, ry, 210, 19, hover);
            // The PNG end ornaments extend inward: reserve 18px at both ends.
            text(g, label(stat), gx + 18, ry + 5, 124, NeoGuiStyle.COLOR_TEXT);
            String value = statValue(stat);
            int valueWidth = Math.min(44, font.width(value));
            fitText(g, value, gx + 192 - valueWidth, ry + 5, valueWidth, 0xFFE3BC67);
            if (hover) tooltip = statTooltip(stat);
        }
        return tooltip;
    }
    private List<Component> item(GuiGraphics g, ItemStack stack, String name, int ix, int iy, float mx, float my) {
        row(g, ix, iy, 40, 40, inside(mx, my, ix, iy, 40, 40));
        if (!stack.isEmpty()) { g.renderItem(stack, ix + 12, iy + 10); g.renderItemDecorations(font, stack, ix + 12, iy + 10); }
        text(g, name, ix, iy + 42, 64, NeoGuiStyle.COLOR_TEXT_MUTED);
        return inside(mx, my, ix, iy, 40, 40) ? (stack.isEmpty() ? List.of(Component.literal("ไม่ได้สวมไอเทมในช่องนี้")) : Screen.getTooltipFromItem(minecraft, stack)) : null;
    }
    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        ProfileSkin.frame(g,ProfileSkin.PANEL,x,y,w,h,6);
    }
    private void row(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        ProfileSkin.frame(g,hover ? ProfileSkin.ROW_HOVER : ProfileSkin.ROW,x,y,w,h,3);
    }
    private void text(GuiGraphics g, String value, int x, int y, int maxWidth, int color) {
        g.drawString(font, font.plainSubstrByWidth(value, maxWidth), x, y, color, false);
    }
    private void bar(GuiGraphics g, String label, String value, float progress, int x, int y, int color) {
        ProfileSkin.frame(g,ProfileSkin.ROW,x-6,y-2,418,20,3);
        fitText(g,label,x+16,y+3,50,NeoGuiStyle.COLOR_TEXT);
        if(label.equals("HP")) g.blitSprite(ResourceLocation.withDefaultNamespace("hud/heart/full"),x,y+2,10,10);
        else if(label.equals("Stamina")) ProfileSkin.icon(g,ProfileSkin.BOOT,x,y+1,11);
        else catalogueIcon(g,NeoBitmapIcon.ICONS_MENU_ADVANCEMENTS,x,y+1,11);
        int valueWidth = Math.min(110, font.width(value));
        ProfileSkin.progress(g,x+70,y,208,14,progress,color);
        fitText(g, value, x + 406 - valueWidth, y + 3, valueWidth, 0xFFE3BC67);
    }
    private void catalogueIcon(GuiGraphics g,NeoBitmapIcon icon,int ix,int iy,int size) {
        var glyph=icon.component();int inkWidth=Math.max(1,font.width(glyph)-1);
        float factor=size/(float)Math.max(inkWidth,icon.height());
        g.pose().pushPose();
        g.pose().translate(ix+(size-inkWidth*factor)/2F,iy+(size-icon.height()*factor)/2F,0);
        g.pose().scale(factor,factor,1);g.drawString(font,glyph,0,icon.ascent()-7,0xFFFFFFFF,false);g.pose().popPose();
    }
    private void fitText(GuiGraphics g, String value, int tx, int ty, int available, int color) {
        float factor = Math.min(1F, available / (float)Math.max(1, font.width(value)));
        g.pose().pushPose(); g.pose().translate(tx, ty + (1 - factor) * 4, 0); g.pose().scale(factor, factor, 1);
        g.drawString(font, value, 0, 0, color, false); g.pose().popPose();
    }
    private static float ratio(double current, double max) { return max <= 0 ? 0 : (float) Math.clamp(current / max, 0, 1); }
    private static String number(double n) { return String.format(Locale.ROOT, "%.2f", n).replaceAll("\\.?0+$", ""); }
    private static boolean inside(float mx, float my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private String label(PlayerProfilePayload.Stat stat) {
        String translated = Component.translatable(stat.key()).getString();
        if (!minecraft.getLanguageManager().getSelected().equals("th_th") && !translated.equals(stat.key())) return translated;
        return switch (stat.key().substring(stat.key().lastIndexOf('.') + 1)) {
            case "attack_damage" -> "พลังโจมตี";
            case "attack_speed" -> "ความเร็วโจมตี";
            case "armor" -> "เกราะ";
            case "armor_toughness" -> "ความทนทานเกราะ";
            case "movement_speed" -> "ความเร็วเคลื่อนที่";
            case "knockback_resistance" -> "ต้านแรงกระเด็น";
            case "armor_negation" -> "เจาะเกราะ";
            case "impact" -> "Impact";
            case "weight" -> "น้ำหนัก";
            case "stun_armor" -> "ต้านสตัน";
            case "max_strikes" -> "เป้าหมายสูงสุด";
            case "stamina_regen" -> "ฟื้นฟู Stamina";
            default -> translated;
        };
    }
    private String statValue(PlayerProfilePayload.Stat stat) {
        if (stat.key().endsWith(".armor_negation")) return number(stat.value()) + "%";
        if (stat.key().endsWith(".movement_speed") || stat.key().endsWith(".knockback_resistance"))
            return String.format(Locale.ROOT, "%.2f", stat.value());
        return number(stat.value());
    }
    private List<Component> statTooltip(PlayerProfilePayload.Stat stat) {
        String help = stat.key().contains("armor_negation") ? "สัดส่วนความเสียหายที่ข้ามการลดด้วยเกราะ (%)"
                : stat.key().contains("impact") ? "แรงกระแทกที่ส่งผลต่อระยะเวลาสตัน"
                : stat.key().contains("weight") ? "น้ำหนักมีผลต่อความเร็วโจมตีและการใช้ Stamina"
                : stat.key().contains("movement_speed") ? "ค่า attribute ความเร็วเคลื่อนที่ ไม่ใช่เปอร์เซ็นต์"
                : stat.key().contains("attack_speed") ? "ความเร็วโจมตี หลังการปรับของ Epic Fight"
                : "ค่ารวม ณ ตอนอัปเดต ไม่ใช่ความเสียหายสุดท้ายของทุกท่าโจมตี";
        return List.of(Component.literal(label(stat)), Component.literal(help),
                Component.literal("พื้นฐาน: " + number(stat.base())), Component.literal("รวม: " + number(stat.value())),
                Component.literal(stat.weaponValue() ? "รวมอาวุธและการปรับของ Epic Fight" : "รวมอุปกรณ์และผลสถานะ"));
    }
    private static int[] gearPosition(int index) {
        return index<4 ? new int[]{178+index*62,112} : new int[]{186+(index-4)%3*74,204+(index-4)/3*54};
    }
    private static String gearLabel(String slot) {
        return switch(slot) {
            case "head","fashion_head" -> "หัว";case "chest" -> "อก";case "legs","fashion_legs" -> "ขา";
            case "feet","fashion_feet" -> "เท้า";case "fashion_face" -> "หน้า";case "fashion_back" -> "หลัง";case "fashion_body" -> "ตัว";default -> slot;
        };
    }
    private static String skillSlot(String slot) {
        if(slot.startsWith("passive")) return "Passive "+slot.substring(7);
        return switch(slot) {
            case "weapon_passive" -> "Passive ประจำอาวุธ";case "weapon_innate" -> "สกิลประจำอาวุธ";
            case "dodge" -> "หลบ";case "guard" -> "ป้องกัน";case "mover" -> "เคลื่อนที่";case "identity" -> "Identity";
            case "knockdown_wakeup" -> "ลุกหลังล้ม";default -> slot;
        };
    }
    private void skillIcon(GuiGraphics g,ProfileDetails.EquippedSkill skill,int ix,int iy,int size) {
        var texture=ResourceLocation.tryParse(skill.texture());
        if(texture!=null && minecraft.getResourceManager().getResource(texture).isPresent()) {
            RenderSystem.enableBlend();g.blit(texture,ix,iy,size,size,0,0,64,64,64,64);
        } else NeoSystemIcons.render(g,NeoSystemIcons.Icon.STATISTICS,ix,iy,size,skill.enabled());
    }
    private List<FormattedCharSequence> detailLines() {
        var out=new java.util.ArrayList<FormattedCharSequence>();
        if(tab==2 && !data.details().gear().isEmpty()) {
            var gear=data.details().gear().get(Math.clamp(selectedGear,0,data.details().gear().size()-1));
            out.addAll(font.split(Component.literal(gearLabel(gear.slot())),132));
            if(gear.stack().isEmpty()) out.addAll(font.split(Component.literal("ไม่ได้สวมไอเทมในช่องนี้"),132));
            else for(var line:Screen.getTooltipFromItem(minecraft,gear.stack())) out.addAll(font.split(line,132));
        } else if(tab==3 && !data.details().skills().isEmpty()) {
            var skill=data.details().skills().get(Math.clamp(selectedSkill,0,data.details().skills().size()-1));
            String mod=skill.id().substring(0,skill.id().indexOf(':')<0 ? skill.id().length() : skill.id().indexOf(':'));
            String source="มอด: "+mod+" · "+skillSlot(skill.slot())+(skill.enabled() ? "" : " · ปิดใช้งาน");
            out.addAll(font.split(Component.literal(source),228));
            if(!skill.bonuses().isEmpty()) {
                out.addAll(font.split(Component.literal("ค่าจากสกิล ณ ตอนเปิดหน้า").withColor(0xE3BC67),228));
                for(var line:skill.bonuses()) out.addAll(font.split(Component.literal(line),228));
            }
            String key=skill.key()+".tooltip",raw=Component.translatable(key).getString();
            if(skill.id().equals("wom:critical_knowledge")) raw="เพิ่มโอกาสคริติคอลและโบนัสความเสียหายเมื่อสวมสกิล\nFire Protection เพิ่มโอกาส · Blast Protection เพิ่มความเสียหาย\nไม่ใช่ค่าคริติคอลรวมของทุกท่าโจมตี";
            if(skill.id().equals("wom:vampirize")) raw="ฟื้น HP ตามความเสียหายจากการโจมตีที่สกิลได้รับ ไม่ใช่การฟื้น HP ต่อวินาที";
            if(raw.equals(key)) raw="มอดไม่ได้ให้คำอธิบายสกิลนี้ในชุดภาษาปัจจุบัน";
            boolean unresolved=raw.matches("(?s).*%(?:\\d+\\$)?[-+\\d.]*[sdif].*");
            raw=raw.replaceAll("%(?:\\d+\\$)?[-+\\d.]*[sdif]","[ตามอุปกรณ์/เงื่อนไข]");
            out.addAll(font.split(Component.literal("ความสามารถและเงื่อนไข").withColor(0xE3BC67),228));
            for(String line:raw.split("\\n",-1)) out.addAll(font.split(Component.literal(line.isEmpty() ? " " : line),228));
            if(unresolved) out.addAll(font.split(Component.literal("ค่าตัวเลขบางส่วนต้องดูจากหน้าสกิลของมอด ไม่ใช่ค่าถาวรของตัวละคร"),228));
        }
        return out;
    }
    private void renderDetail(GuiGraphics g,int dx,int dy,int width) {
        var lines=detailLines();
        for(int i=0;i<DETAIL_ROWS && detailScroll+i<lines.size();i++)
            g.drawString(font,lines.get(detailScroll+i),dx,dy+i*10,NeoGuiStyle.COLOR_TEXT,false);
    }
    private void drawWrapped(GuiGraphics g,String message,int dx,int dy,int width,int maxRows) {
        int count=0;
        for(var line:font.split(Component.literal(message),width)) {
            if(count>=maxRows) break;g.drawString(font,line,dx,dy+count++*10,NeoGuiStyle.COLOR_TEXT_MUTED,false);
        }
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy) {
        float lx=(float)((mx-x)/scale),ly=(float)((my-y)/scale);
        if(tab==3 && skillScrollbar!=null && inside(lx,ly,166,108,172,192)) return skillScrollbar.scroll(dy);
        if(detailScrollbar!=null && inside(lx,ly,tab==2 ? 444 : 348,106,tab==2 ? 156 : 252,198)) return detailScrollbar.scroll(dy);
        return super.mouseScrolled(mx,my,dx,dy);
    }
    @Override public boolean isPauseScreen() { return false; }
}
