package com.ambition.neoworld.client.gui.player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.common.NeoPopupScreen;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.client.gui.style.NeoGuiStyle;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.widget.NeoProgressBar;
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
import net.neoforged.neoforge.network.PacketDistributor;

/** Read-only server snapshot. No local changes to player stats or inventory. */
public final class PlayerProfileScreen extends Screen {
    private static final int W = 440, H = 324;
    private static final int CLOSE_GAP = 4, CLOSE_WIDTH = 20;
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/player/profile_frame.png");
    private static final int TEXTURE_W = 1462, TEXTURE_H = 1076;
    private static final int CONTENT_X = 16, CONTENT_Y = 198, CONTENT_W = 408, CONTENT_H = 96;
    private static final int VISIBLE_ROWS = 4;
    private PlayerProfilePayload data;
    private UUID pending;
    private int waiting, tab, scroll;
    private float scale;
    private int x, y;
    private NeoButton refresh;
    private boolean draggingScroll;
    private final NeoProgressBar expBar = new NeoProgressBar().color(0xFFE2B551).showPercentage(false);
    private final NeoProgressBar healthBar = new NeoProgressBar().color(0xFF20B99F).showPercentage(false);
    private final NeoProgressBar staminaBar = new NeoProgressBar().color(0xFF1EC9D5).showPercentage(false);

    public PlayerProfileScreen(PlayerProfilePayload data) {
        super(Component.literal("โปรไฟล์ผู้เล่น"));
        this.data = data;
    }
    public static void receive(PlayerProfilePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.requestId().equals(PlayerProfilePayload.COMMAND_REQUEST) && mc.player != null
                && payload.targetId().equals(mc.player.getUUID())) {
            mc.setScreen(new PlayerProfileScreen(payload));
            return;
        }
        if (mc.screen instanceof PlayerActionScreen actions && actions.acceptsProfile(payload)) {
            mc.setScreen(new PlayerProfileScreen(payload));
        } else if (mc.screen instanceof PlayerProfileScreen profile && payload.targetId().equals(profile.data.targetId())
                && payload.requestId().equals(profile.pending)) {
            profile.data = payload; profile.pending = null; profile.waiting = 0; profile.rebuildWidgets();
        }
    }
    public static boolean receiveError(PlayerActionResultPayload result) {
        if (!(Minecraft.getInstance().screen instanceof PlayerProfileScreen screen)
                || !result.targetId().equals(screen.data.targetId()) || !result.requestId().equals(screen.pending)) return false;
        screen.pending = null; screen.rebuildWidgets();
        NeoPopupScreen.showAlert(Component.literal("อัปเดตโปรไฟล์ไม่ได้"), Component.literal(result.message()), () -> {});
        return true;
    }
    @Override protected void init() {
        scroll = Math.clamp(scroll, 0, maxScroll());
        draggingScroll = false;
        scale = Math.min(1.25F, Math.min((width - 16F) / (W + 2 * (CLOSE_GAP + CLOSE_WIDTH)), (height - 16F) / H));
        scale = Math.max(0.1F, scale);
        x = (width - Math.round(W * scale)) / 2; y = (height - Math.round(H * scale)) / 2;
        addRenderableWidget(new NeoButton(Button.builder(Component.literal("×"), b -> onClose())
                .bounds(px(W + CLOSE_GAP), py(24), size(CLOSE_WIDTH), size(19)).tooltip(Tooltip.create(Component.literal("ปิดโปรไฟล์"))))
                .style(NeoButton.Style.DESTRUCTIVE));
        boolean ownProfile = minecraft.player != null && data.targetId().equals(minecraft.player.getUUID());
        NeoButton titleButton = addRenderableWidget(new NeoButton(Button.builder(Component.literal("ฉายา"), b -> TitleScreenController.open())
                .bounds(px(366), py(48), size(58), size(19))
                .tooltip(Tooltip.create(Component.literal(ownProfile ? "จัดการฉายาของคุณ" : "จัดการได้เฉพาะฉายาของตัวเอง"))))
                .style(NeoButton.Style.PRIMARY));
        titleButton.active = ownProfile;
        String[] labels = {"ภาพรวม", "ค่าพลังต่อสู้", "ผลสถานะ"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            addRenderableWidget(new NeoButton(Button.builder(Component.literal(labels[i]), b -> {
                tab = index; scroll = 0; rebuildWidgets();
            }).bounds(px(16 + i * 138), py(168), size(132), size(22)))
                    .style(tab == i ? NeoButton.Style.PRIMARY : NeoButton.Style.STANDARD).selected(tab == i));
        }
        refresh = addRenderableWidget(new NeoButton(Button.builder(NeoGuiIcons.label(NeoGuiIcons.REFRESH, "อัปเดต"), b -> request())
                .bounds(px(300), py(298), size(124), size(19))
                .tooltip(Tooltip.create(Component.literal("ดึงค่าล่าสุดจากเซิร์ฟเวอร์")))).style(NeoButton.Style.PRIMARY));
        refresh.active = pending == null;
    }
    private int px(int n) { return x + Math.round(n * scale); }
    private int py(int n) { return y + Math.round(n * scale); }
    private int size(int n) { return Math.max(1, Math.round(n * scale)); }
    private void request() {
        if (pending != null) return;
        pending = UUID.randomUUID(); waiting = 0; refresh.active = false;
        PacketDistributor.sendToServer(new PlayerActionPayload(pending, data.targetId(), PlayerActionPayload.Action.PROFILE));
    }
    @Override public void tick() {
        if (pending != null && ++waiting >= 200) {
            pending = null; refresh.active = true;
            NeoPopupScreen.showAlert(Component.literal("หมดเวลารอ"), Component.literal("ไม่ได้รับคำตอบจากเซิร์ฟเวอร์ กดอัปเดตเพื่อลองอีกครั้ง"), () -> {});
        }
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        float mx = (mouseX - x) / scale, my = (mouseY - y) / scale;
        List<Component> tooltip = null;
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        RenderSystem.enableBlend();
        g.blit(FRAME, 0, 0, W, H, 0, 0, TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);
        g.drawCenteredString(font, title, W / 2, 18, NeoGuiStyle.COLOR_TEXT);
        panel(g, 16, 44, 112, 112);
        var player = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(data.targetId());
        if (player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, 20, 48, 124, 152, 44, 0.0625F, mx, my, player);
        } else {
            text(g, "ตัวละครอยู่นอกระยะ", 22, 96, 100, NeoGuiStyle.COLOR_TEXT_MUTED);
        }
        g.pose().pushPose(); g.pose().translate(140, 48, 0); g.pose().scale(1.4F, 1.4F, 1);
        text(g, data.name(), 0, 0, 154, NeoGuiStyle.COLOR_TEXT);
        g.pose().popPose();
        text(g, "Lv. ", 140, 64, 104, NeoGuiStyle.COLOR_TEXT_MUTED);
        text(g, Integer.toString(data.level()), 140 + font.width("Lv. "), 64, 48, NeoGuiStyle.COLOR_GOLD);
        g.fill(140, 77, 424, 78, NeoGuiStyle.COLOR_FRAME);
        boolean max = data.level() >= com.ambition.neoworld.level.LevelFormula.MAX_LEVEL;
        bar(g, "EXP", max ? "MAX" : data.exp() + " / " + data.requiredExp(),
                max ? 1 : ratio(data.exp(), data.requiredExp()), 140, 83, expBar);
        bar(g, "HP", number(data.health()) + " / " + number(data.maxHealth()), ratio(data.health(), data.maxHealth()), 140, 103, healthBar);
        staminaBar.enabled(data.maxStamina() >= 0);
        bar(g, "Stamina", data.maxStamina() < 0 ? "ไม่พร้อมใช้งาน" : number(data.stamina()) + " / " + number(data.maxStamina()),
                ratio(data.stamina(), data.maxStamina()), 140, 123, staminaBar);
        text(g, "อาวุธ:", 140, 146, 42, NeoGuiStyle.COLOR_TEXT_MUTED);
        text(g, data.weapon().isEmpty() ? "มือเปล่า" : Component.translatable(data.weapon()).getString(), 174, 146, 248, NeoGuiStyle.COLOR_ACCENT);
        if (tab == 0) {
            for (int i = 0; i < data.stats().size(); i++) {
                if (i >= 12) break;
                var stat = data.stats().get(i); int sx = CONTENT_X + i % 2 * 208, sy = CONTENT_Y + i / 2 * 16;
                boolean hover = inside(mx, my, sx, sy, 200, 16);
                row(g, sx, sy, 200, 16, hover);
                String value = statValue(stat);
                int valueWidth = Math.min(78, font.width(value));
                int labelWidth = Math.max(12, 182 - valueWidth);
                text(g, label(stat), sx + 7, sy + 4, labelWidth, NeoGuiStyle.COLOR_TEXT);
                fitText(g, value, sx + 194 - valueWidth, sy + 4, valueWidth, NeoGuiStyle.COLOR_GOLD);
                if (hover) tooltip = statTooltip(stat);
            }
        } else if (tab == 1) {
            for (int i = 0; i < VISIBLE_ROWS && scroll + i < data.stats().size(); i++) {
                var stat = data.stats().get(scroll + i); int sy = CONTENT_Y + i * 24;
                boolean hover = inside(mx, my, CONTENT_X, sy, 398, 23);
                row(g, CONTENT_X, sy, 398, 23, hover);
                text(g, label(stat) + "  " + statValue(stat), 23, sy + 2, 380, NeoGuiStyle.COLOR_GOLD);
                text(g, "พื้นฐาน " + number(stat.base()) + "  |  " + (stat.weaponValue() ? "รวมอาวุธ/การปรับของ Epic Fight" : "รวมอุปกรณ์และผลสถานะ"), 23, sy + 12, 380, NeoGuiStyle.COLOR_TEXT_MUTED);
                if (hover) tooltip = statTooltip(stat);
            }
        } else {
            if (data.effects().isEmpty()) text(g, "ไม่มีผลสถานะที่ใช้งาน", 23, 208, 380, NeoGuiStyle.COLOR_TEXT_MUTED);
            for (int i = 0; i < VISIBLE_ROWS && scroll + i < data.effects().size(); i++) {
                var effect = data.effects().get(scroll + i); int sy = CONTENT_Y + i * 24;
                row(g, CONTENT_X, sy, 398, 23, inside(mx, my, CONTENT_X, sy, 398, 23));
                String duration = effect.ticks() < 0 ? "ถาวร" : (effect.ticks() / 20 / 60) + ":" + String.format(Locale.ROOT, "%02d", effect.ticks() / 20 % 60);
                String name = Component.translatable(effect.key()).getString();
                text(g, name + " " + (effect.amplifier() + 1), 23, sy + 7, 286, NeoGuiStyle.COLOR_TEXT);
                text(g, duration, 332, sy + 7, 72, NeoGuiStyle.COLOR_GOLD);
                if (inside(mx, my, CONTENT_X, sy, 398, 23)) tooltip = List.of(Component.literal(name + " " + (effect.amplifier() + 1)), Component.literal("เวลาที่เหลือ ณ ตอนอัปเดต: " + duration));
            }
        }
        if (maxScroll() > 0) {
            g.fill(418, CONTENT_Y, 424, CONTENT_Y + CONTENT_H, NeoGuiStyle.COLOR_PANEL_DARK);
            int thumb = Math.max(12, CONTENT_H * VISIBLE_ROWS / totalRows());
            int top = CONTENT_Y + Math.round((CONTENT_H - thumb) * scroll / (float)maxScroll());
            g.fill(419, top, 423, top + thumb, draggingScroll ? NeoGuiStyle.COLOR_ACCENT : NeoGuiStyle.COLOR_FRAME_LIGHT);
        }
        g.fill(16, 296, 424, 297, NeoGuiStyle.COLOR_FRAME);
        text(g, "ข้อมูล ณ ตอนอัปเดตจากเซิร์ฟเวอร์", 16, 305, 274, NeoGuiStyle.COLOR_TEXT_MUTED);
        g.pose().popPose();
        for (var widget : renderables) widget.render(g, mouseX, mouseY, partialTick);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }
    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF090D10);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, NeoGuiStyle.COLOR_FRAME_LIGHT);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, NeoGuiStyle.COLOR_FRAME);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, NeoGuiStyle.COLOR_PANEL_DARK);
        for (int cx : new int[] {x + 1, x + w - 4}) {
            for (int cy : new int[] {y + 1, y + h - 4}) {
                g.fill(cx, cy, cx + 3, cy + 3, 0xFFABB4BA);
                g.fill(cx + 1, cy + 1, cx + 2, cy + 2, 0xFF31393E);
            }
        }
    }
    private void row(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        g.fill(x, y, x + w, y + h, hover ? 0xFF283A3E : 0xD012171A);
        g.fill(x, y, x + w, y + 1, hover ? NeoGuiStyle.COLOR_ACCENT : NeoGuiStyle.COLOR_FRAME);
        g.fill(x, y, x + 1, y + h, NeoGuiStyle.COLOR_FRAME);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF30383D);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF090D10);
    }
    private void text(GuiGraphics g, String value, int x, int y, int maxWidth, int color) {
        g.drawString(font, font.plainSubstrByWidth(value, maxWidth), x, y, color, false);
    }
    private void bar(GuiGraphics g, String label, String value, float progress, int x, int y, NeoProgressBar bar) {
        text(g, label, x, y + 3, 44, NeoGuiStyle.COLOR_TEXT);
        int valueWidth = Math.min(110, font.width(value));
        // Fixed track width keeps all three bars aligned when numeric labels change.
        bar.bounds(x + 46, y, 120, 14).progress(progress).loading(pending != null).render(g, font);
        fitText(g, value, x + 284 - valueWidth, y + 3, valueWidth, NeoGuiStyle.COLOR_GOLD);
        g.fill(x, y + 17, x + 284, y + 18, 0xFF30383D);
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
    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (tab == 0 || dy == 0 || !inside((float)((mx - x) / scale), (float)((my - y) / scale), CONTENT_X, CONTENT_Y, CONTENT_W, CONTENT_H)) return super.mouseScrolled(mx, my, dx, dy);
        scroll = Math.clamp(scroll + (dy < 0 ? 1 : -1), 0, maxScroll()); return true;
    }
    private int totalRows() { return tab == 1 ? data.stats().size() : tab == 2 ? data.effects().size() : 0; }
    private int maxScroll() { return Math.max(0, totalRows() - VISIBLE_ROWS); }
    private void scrollTo(double mouseY) {
        int thumb = Math.max(12, CONTENT_H * VISIBLE_ROWS / totalRows());
        double pos = (mouseY - y) / scale - CONTENT_Y - thumb / 2.0;
        scroll = Math.clamp((int)Math.round(pos * maxScroll() / (CONTENT_H - thumb)), 0, maxScroll());
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && maxScroll() > 0 && inside((float)((mx - x) / scale), (float)((my - y) / scale), 418, CONTENT_Y, 6, CONTENT_H)) {
            draggingScroll = true; scrollTo(my); return true;
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && draggingScroll && maxScroll() > 0) { scrollTo(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingScroll) { draggingScroll = false; return true; }
        return super.mouseReleased(mx, my, button);
    }
    @Override public boolean isPauseScreen() { return false; }
}
