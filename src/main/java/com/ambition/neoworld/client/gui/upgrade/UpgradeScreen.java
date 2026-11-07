package com.ambition.neoworld.client.gui.upgrade;

import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.state.ClientEconomyData;
import com.ambition.neoworld.network.upgrade.UpgradeSubmitPayload;
import com.ambition.neoworld.registry.ModItems;
import com.ambition.neoworld.upgrade.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Locale;

/** Variant A decoration around vanilla slots and container input handling. */
public final class UpgradeScreen extends AbstractContainerScreen<UpgradeMenu> {
    private static final int AMBER = 0xFFD18A;
    private NeoButton confirm;
    private long pendingUntil;
    private int submittedSequence, effectSources;
    private long effectStarted, revealAt;
    private int effectOutcome = -1, effectLevel;
    private boolean awaitingResult, resultSoundPlayed;
    private String effectMessage = "";
    private static final long WINDUP_MS = 1000, RESULT_MS = 850;

    public UpgradeScreen(UpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = UpgradeMenu.WIDTH;
        imageHeight = UpgradeMenu.HEIGHT;
    }
    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/upgrade/navy_copper/" + name + ".png");
    }

    @Override protected void init() {
        super.init();
        confirm = addRenderableWidget(new NeoButton(Button.builder(Component.literal("ตีบวก"), ignored -> submit())
                .bounds(leftPos + 18, topPos + 208, 144, 16)).style(NeoButton.Style.PRIMARY)
                .frameRenderer(this::buttonFrame));
        confirm.setTooltip(Tooltip.create(Component.literal("ใช้ Silver หิน ใบป้องกันและหินเพิ่มโอกาสที่ใส่ไว้ทุกครั้ง รวมเมื่อสำเร็จ")));
        addRenderableWidget(com.ambition.neoworld.client.gui.widget.NeoCloseButton.create(
                leftPos + 334, topPos + 5, 16, 16, Component.literal("X"),
                Component.literal("ปิดและคืนไอเทม"), this::onClose).textScrolling(false)
                .frameRenderer((g, button) -> sliced(g, "close_backing", button.getX(), button.getY(),
                        button.getWidth(), button.getHeight(), 177, 180, 18, 3)));
    }

    private int level() { return UpgradeData.level(menu.getSlot(0).getItem()); }
    private int target() { return Math.min(15, level() + 1); }
    private boolean validEquipment() {
        return UpgradeConfig.SPEC.isLoaded() && UpgradeService.eligible(menu.getSlot(0).getItem()) && level() >= 0 && level() < 15;
    }
    private boolean ready() {
        if (!validEquipment() || !menu.getCarried().isEmpty()) return false;
        int target = target();
        ItemStack stones = menu.getSlot(1).getItem();
        var required = target <= 6 ? ModItems.UPGRADE_STONE.get() : ModItems.ADVANCED_UPGRADE_STONE.get();
        return stones.is(required) && stones.getCount() >= UpgradeConfig.stoneCost(target)
                && ClientEconomyData.getSilver() >= UpgradeConfig.silverCost(target);
    }
    private void submit() {
        if (!ready() || !confirm.active) return;
        int requestRevision = menu.revision();
        effectSources = 0;
        for (int i = 1; i < 4; i++) if (menu.getSlot(i).hasItem()) effectSources |= 1 << i;
        effectStarted = net.minecraft.Util.getMillis();
        pendingUntil = effectStarted + 5000;
        submittedSequence = menu.resultSequence();
        awaitingResult = true;
        revealAt = 0;
        effectOutcome = -1;
        resultSoundPlayed = false;
        effectMessage = "กำลังตีบวก…";
        sound(net.minecraft.sounds.SoundEvents.ANVIL_USE, .45F, 1.3F);
        confirm.active = false;
        PacketDistributor.sendToServer(new UpgradeSubmitPayload(menu.containerId, menu.session, requestRevision));
    }
    @Override protected void containerTick() {
        super.containerTick();
        long now = net.minecraft.Util.getMillis();
        if (awaitingResult && menu.resultSequence() != submittedSequence) {
            awaitingResult = false;
            effectOutcome = menu.resultCode() == 1 ? menu.resultOutcome() : -1;
            effectLevel = menu.resultLevel();
            effectMessage = menu.resultCode() != 1 ? "ตรวจของและ Silver" : switch (effectOutcome) {
                case 0 -> "สำเร็จ +" + effectLevel;
                case 1 -> "พลาด: คงระดับ +" + effectLevel;
                case 2 -> "ลดระดับเหลือ +" + effectLevel;
                case 3 -> "กลับ +0";
                case 4 -> "ป้องกันระดับแล้ว +" + effectLevel;
                default -> "ตรวจของและ Silver";
            };
            revealAt = Math.max(effectStarted + WINDUP_MS, now);
        } else if (awaitingResult && now >= pendingUntil) {
            awaitingResult = false;
            effectOutcome = -1;
            effectMessage = "ยังไม่ได้รับผล ลองตรวจไอเทม";
            revealAt = now;
        }
        if (revealAt != 0 && now >= revealAt && now - revealAt < RESULT_MS && !resultSoundPlayed) {
            resultSoundPlayed = true;
            sound(effectOutcome == 0 ? net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP
                    : effectOutcome == 4 ? net.minecraft.sounds.SoundEvents.SHIELD_BLOCK
                    : net.minecraft.sounds.SoundEvents.ANVIL_LAND, .45F, effectOutcome == 0 ? 1.25F : .75F);
        }
        confirm.active = !effectBusy() && ready();
        confirm.setMessage(Component.literal(effectBusy() ? "กำลังตีบวก…"
                : validEquipment() ? "ตีบวก +" + target() : level() >= 15 ? "สูงสุดแล้ว" : "ตีบวก"));
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        sliced(g, "outer_frame", leftPos, topPos, imageWidth, imageHeight, 354, 199, 80, 10);
        panel(g, 8, 28, 166, 109);
        panel(g, 178, 28, 174, 109);
        panel(g, 8, 139, 166, 89);
        panel(g, 178, 139, 174, 89);
        sliced(g, "title_plaque", leftPos + 102, topPos + 3, 156, 22, 399, 130, 36, 6);
        icon(g, "icon_hammer", 108, 8, 12, 12, 205, 198);
        icon(g, "icon_anvil", 241, 8, 13, 11, 221, 151);
        section(g, "icon_anvil", 12, 30, 158, 221, 151);
        section(g, "icon_swords", 182, 30, 166, 35, 35);
        section(g, "icon_coins", 12, 140, 158, 33, 37);
        section(g, "icon_chest", 182, 140, 166, 39, 36);
        icon(g, "icon_shield", 187, 116, 10, 12, 165, 198);
        for (int y : new int[]{55, 73, 91, 109})
            g.fill(leftPos + 186, topPos + y, leftPos + 344, topPos + y + 1, 0x556D6558);
        for (int x : new int[]{242, 279, 315})
            g.fill(leftPos + x, topPos + 44, leftPos + x + 1, topPos + 110, 0x556D6558);
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            String name = i < 4 ? "equipment_slot" : "inventory_slot";
            // Native 16px items remain aligned with vanilla 16px slot hitboxes.
            sliced(g, name, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18,
                    i < 4 ? 216 : 213, i < 4 ? 203 : 202, 18, 2);
        }
        double chance = validEquipment() ? Math.min(100, UpgradeConfig.chances(target()).success()
                + UpgradeRules.combinedBonus(UpgradeConfig.failureBonus(UpgradeData.failures(menu.getSlot(0).getItem())), !menu.getSlot(3).getItem().isEmpty())) : 0;
        sliced(g, "progress_track", leftPos + 18, topPos + 193, 144, 12, 355, 67, 16, 3);
        int fill = (int) Math.round(138 * chance / 100);
        if (fill > 0) g.blit(texture("progress_fill"), leftPos + 21, topPos + 196, fill, 6,
                2, 2, Math.max(1, 309 * fill / 138), 34, 313, 38);
        g.drawString(font, "โอกาสสำเร็จ", leftPos + 23, topPos + 195, 0xFFF1D4, false);
        String chanceText = String.format(Locale.ROOT, "%.0f%%", chance);
        g.drawString(font, chanceText, leftPos + 157 - font.width(chanceText), topPos + 195, AMBER, false);
    }
    private void buttonFrame(GuiGraphics g, NeoButton button) {
        long window = minecraft.getWindow().getWindow();
        boolean pressed = (effectStarted != 0 && net.minecraft.Util.getMillis() - effectStarted < 150) || button.isSelected()
                || (button.isHovered() && org.lwjgl.glfw.GLFW.glfwGetMouseButton(window,
                    org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS)
                || (button.isFocused() && (com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,
                    org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE)));
        String state = pressed ? "pressed" : !button.active ? "disabled" : button.isHoveredOrFocused() ? "hover" : "normal";
        sliced(g, "button_" + state, button.getX(), button.getY(), button.getWidth(), button.getHeight(), 348, 128, 48, 6);
    }
    private void section(GuiGraphics g, String icon, int x, int y, int width, int sw, int sh) {
        sliced(g, "section_header", leftPos + x, topPos + y, width, 13, 354, 90, 60, 8);
        icon(g, icon, x + 5, y + 2, 10, 9, sw, sh);
    }
    private void icon(GuiGraphics g, String name, int x, int y, int w, int h, int sw, int sh) {
        g.blit(texture(name), leftPos + x, topPos + y, w, h, 0, 0, sw, sh, sw, sh);
    }
    private void panel(GuiGraphics g, int x, int y, int width, int height) {
        sliced(g, "panel_frame", leftPos + x, topPos + y, width, height, 315, 198, 48, 6);
    }
    /** Keep ornament scale identical on both axes; repeat straight strips instead of stretching them. */
    private void sliced(GuiGraphics g, String name, int x, int y, int w, int h,
                        int sw, int sh, int edge, int corner) {
        int ex = Math.min(edge, (sw - 5) / 2), ey = Math.min(edge, (sh - 5) / 2);
        double scale = Math.min(corner / (double) edge,
                Math.min(w / (double) (sw - 4), h / (double) (sh - 4)));
        int cx = Math.max(1, (int) Math.round(ex * scale));
        int cy = Math.max(1, (int) Math.round(ey * scale));
        int[] sx = {2, 2 + ex, sw - 2 - ex, sw - 2};
        int[] sy = {2, 2 + ey, sh - 2 - ey, sh - 2};
        int[] dx = {0, cx, w - cx, w}, dy = {0, cy, h - cy, h};
        ResourceLocation resource = texture(name);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            int sourceW = sx[col + 1] - sx[col], sourceH = sy[row + 1] - sy[row];
            int destW = dx[col + 1] - dx[col], destH = dy[row + 1] - dy[row];
            if (destW <= 0 || destH <= 0) continue;
            if (row != 1 && col != 1) {
                g.blit(resource, x + dx[col], y + dy[row], destW, destH,
                        sx[col], sy[row], sourceW, sourceH, sw, sh);
                continue;
            }
            // Repeat a small square patch for the backing, keeping its grain at a stable size.
            boolean center = row == 1 && col == 1;
            if (center) { sourceW = Math.min(64, sourceW); sourceH = Math.min(64, sourceH); }
            int tileW = col == 1 ? Math.max(1, (int) Math.round(sourceW * (center ? .5 : scale))) : destW;
            int tileH = row == 1 ? Math.max(1, (int) Math.round(sourceH * (center ? .5 : scale))) : destH;
            for (int yy = 0; yy < destH; yy += tileH) for (int xx = 0; xx < destW; xx += tileW) {
                int tw = Math.min(tileW, destW - xx), th = Math.min(tileH, destH - yy);
                g.blit(resource, x + dx[col] + xx, y + dy[row] + yy, tw, th,
                        sx[col], sy[row], Math.max(1, sourceW * tw / tileW),
                        Math.max(1, sourceH * th / tileH), sw, sh);
            }
        }
    }
    private void centered(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }
    private void text(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        g.drawString(font, font.plainSubstrByWidth(text, maxWidth), x, y, color, false);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        centered(g, "ตีบวกอุปกรณ์", 180, 10, AMBER);
        text(g, "อุปกรณ์และวัตถุดิบ", 30, 32, 136, AMBER);
        text(g, "ค่าสถานะหลังตีบวก", 200, 32, 143, AMBER);
        text(g, "กระเป๋าไอเทม", 200, 142, 143, AMBER);
        ItemStack stack = menu.getSlot(0).getItem();
        text(g, stack.isEmpty() ? "วางอุปกรณ์" : stack.getHoverName().getString(), 63, 51, 100, 0xFFF1D4);
        text(g, stack.isEmpty() ? "" : "+" + level() + " → +" + target(), 63, 67, 100, AMBER);
        centered(g, validEquipment() ? "หิน ×" + UpgradeConfig.stoneCost(target()) : "หิน", 38, 114, AMBER);
        centered(g, "ใบป้องกัน", 90, 114, AMBER);
        centered(g, "เพิ่มโอกาส", 142, 114, AMBER);
        text(g, "ค่าใช้จ่าย", 30, 142, 130, AMBER);
        text(g, validEquipment() ? String.format(Locale.US, "ใช้ %,d Silver", UpgradeConfig.silverCost(target())) : "เลือกอุปกรณ์ก่อน", 18, 157, 144, AMBER);
        text(g, "สถานะ", 187, 45, 52, 0xD6C9B3);
        text(g, "ก่อน", 248, 45, 29, 0xD6C9B3);
        text(g, "หลัง", 285, 45, 30, AMBER);
        text(g, "เพิ่ม", 321, 45, 26, 0x72ECA1);
        if (validEquipment()) {
            int current = level();
            if (stack.getItem() instanceof ArmorItem armor) {
                double per = UpgradeConfig.ARMOR_PER_LEVEL.get(), base = originalArmor(stack, armor);
                row(g, "Armor", base + per * current, base + per * (current + 1), 59);
                per = UpgradeConfig.STUN_ARMOR_PER_LEVEL.get();
                row(g, "Stun +", per * current, per * (current + 1), 77);
            } else {
                double base = UpgradeAttributeHandler.originalWeaponDamage(stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY));
                double per = base * UpgradeConfig.DAMAGE_PERCENT_PER_LEVEL.get() / 100;
                row(g, "โจมตี", base + per * current, base + per * (current + 1), 59);
                row(g, "โบนัสตีบวก", per * current, per * (current + 1), 77);
                per = base > 0 ? UpgradeConfig.IMPACT_PER_LEVEL.get() : 0;
                row(g, "Impact +", per * current, per * (current + 1), 95);
            }
        } else centered(g, "เลือกอุปกรณ์", 265, 76, 0x999999);
        text(g, menu.getSlot(2).getItem().isEmpty() ? "พลาดอาจลด/กลับ +0 ของไม่หาย" : "ป้องกันแล้ว: พลาดคงระดับ", 200, 118, 144, AMBER);
        text(g, String.format(Locale.US, "ยอดเงิน %,d Silver", ClientEconomyData.getSilver()), 18, 169, 144, AMBER);
        text(g, "แต้มพลาด " + UpgradeData.failures(stack), 18, 181, 144, 0xD6C9B3);
        if (!effectMessage.isEmpty())
            text(g, revealAt == 0 || net.minecraft.Util.getMillis() < revealAt ? "กำลังตีบวก…" : effectMessage,
                    18, 125, 144, effectOutcome == 2 || effectOutcome == 3 ? 0xFF7766 : AMBER);
    }
    private void sound(net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(event, pitch, volume));
    }
    private boolean effectBusy() {
        return awaitingResult || (revealAt != 0 && net.minecraft.Util.getMillis() < revealAt + RESULT_MS);
    }
    @Override protected void slotClicked(net.minecraft.world.inventory.Slot slot, int slotId, int mouseButton,
                                         net.minecraft.world.inventory.ClickType type) {
        if (!effectBusy()) super.slotClicked(slot, slotId, mouseButton, type);
    }
    /** Screen-space sparks; no persistent particles, resources or gameplay mutations. */
    private void renderForgeEffect(GuiGraphics g) {
        if (!effectBusy()) return;
        long now = net.minecraft.Util.getMillis();
        boolean result = revealAt != 0 && now >= revealAt;
        double t = (now - effectStarted) / 1000.0;
        double progress = result ? Math.clamp((now - revealAt) / (double) RESULT_MS, 0, 1) : 0;
        int cx = leftPos + menu.getSlot(0).x + 8, cy = topPos + menu.getSlot(0).y + 8;
        int rgb = result && (effectOutcome == 2 || effectOutcome == 3) ? 0xFF4935 : 0xFFC16A;
        float alpha = (float) (result ? 1 - progress : .55 + .35 * Math.sin(t * 12));
        int color = ((int) (190 * alpha) << 24) | rgb;
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.enableScissor(leftPos + 11, topPos + 44, leftPos + 171, topPos + 136);
        int radius = result ? 10 + (int) (progress * 16) : 11;
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12 + t * 2.5;
            int x = cx + (int) (Math.cos(angle) * radius), y = cy + (int) (Math.sin(angle) * radius);
            g.fill(x, y, x + 1, y + 1, color);
        }
        if (!result) {
            for (int source = 1; source < 4; source++) {
                var slot = menu.getSlot(source);
                if ((effectSources & (1 << source)) == 0) continue;
                for (int i = 0; i < 4; i++) {
                    double f = (t * 1.5 + i * .23) % 1;
                    int x = (int) ((leftPos + slot.x + 8) * (1 - f) + cx * f);
                    int y = (int) ((topPos + slot.y + 8) * (1 - f) + cy * f - Math.sin(f * Math.PI) * 10);
                    g.fill(x, y, x + 2, y + 2, color);
                }
            }
        } else if (effectOutcome == 0) {
            g.fill(cx - 8, cy - 8, cx + 8, cy + 8, ((int) (100 * (1 - progress)) << 24) | 0xFFE5A6);
            for (int i = 0; i < 18; i++) {
                double angle = i * Math.PI * 2 / 18;
                int x = cx + (int) (Math.cos(angle) * progress * 32);
                int y = cy + (int) (Math.sin(angle) * progress * 25 + progress * progress * 12);
                g.fill(x, y, x + 2, y + 2, color);
            }
        } else if (effectOutcome == 4) {
            icon(g, "icon_shield", menu.getSlot(0).x - 1, menu.getSlot(0).y - 3, 20, 24, 165, 198);
        } else if (effectOutcome == 2 || effectOutcome == 3) {
            for (int i = 0; i < 16; i++) {
                int x = cx - 3 + (i % 4 < 2 ? -2 : 2), y = cy - 8 + i;
                g.fill(x, y, x + 2, y + 1, color);
            }
        } else if (effectOutcome == 1) {
            for (int i = 0; i < 6; i++) {
                int x = cx - 7 + i * 3, y = cy - 2 - (int) (progress * (12 + i));
                g.fill(x, y, x + 3, y + 3, ((int) (90 * (1 - progress)) << 24) | 0xA4A0A0);
            }
        }
        g.disableScissor();
        g.flush();
        g.pose().popPose();
    }

    private void row(GuiGraphics g, String name, double before, double after, int y) {
        text(g, name, 187, y, 53, 0xFFFFFF);
        text(g, number(before), 248, y, 30, 0xFFFFFF);
        text(g, number(after), 285, y, 30, AMBER);
        text(g, "+" + number(after - before), 321, y, 25, 0x72ECA1);
    }
    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static double originalArmor(ItemStack stack, ArmorItem armor) {
        var value = new net.minecraft.world.entity.ai.attributes.AttributeInstance(
                net.minecraft.world.entity.ai.attributes.Attributes.ARMOR, ignored -> {});
        value.setBaseValue(0);
        for (var entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (entry.attribute().equals(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
                    && entry.slot().test(armor.getEquipmentSlot())) value.addOrReplacePermanentModifier(entry.modifier());
        }
        return value.getValue();
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBg(g, partialTick, mouseX, mouseY);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(), 0x9A000000);
        super.render(g, mouseX, mouseY, partialTick);
        renderForgeEffect(g);
        renderTooltip(g, mouseX, mouseY);
        if (mouseX >= leftPos + 16 && mouseX < leftPos + 164 && mouseY >= topPos + 155 && mouseY < topPos + 190) {
            g.renderTooltip(font, Component.literal(String.format(Locale.US, "ยอดเงิน %,d Silver • รวมผลแต้มพลาดและหินเพิ่มโอกาสแล้ว", ClientEconomyData.getSilver())), mouseX, mouseY);
        }
    }
}
