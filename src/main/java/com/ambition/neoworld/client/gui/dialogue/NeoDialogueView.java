package com.ambition.neoworld.client.gui.dialogue;

import com.ambition.neoworld.client.gui.common.NeoBitmapFonts;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.widget.NeoScrollbar;
import com.ambition.neoworld.client.gui.widget.NeoTitlePlaque;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import noppes.npcs.client.TextBlockClient;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.util.NoppesStringUtils;

import java.util.ArrayList;
import java.util.List;

/** Visual skin only. Original CustomNPCs code continues to own selection and server packets. */
public final class NeoDialogueView {
    private static final int NPC_PREVIEW_SCALE = 40;
    private static final ResourceLocation FRAME = texture("frame");
    private static final ResourceLocation PORTRAIT = texture("portrait");
    private static final ResourceLocation RESPONSE = texture("response");
    private static final ResourceLocation DIAMOND = texture("diamond");
    private final NeoTitlePlaque speaker = new NeoTitlePlaque(0, 0, 1, 1, Component.empty())
            .textColor(0xFFFFD875);
    private final NeoButton close;
    private DialogueLayout layout;
    private Dialog lastDialog;
    private int lastWidth = -1, lastHeight = -1;
    private TextBlockClient lastSpeech;
    private final DialogueTextPlayback playback = new DialogueTextPlayback();
    private List<Integer> lastOptions = List.of();
    private record HistoryLine(FormattedCharSequence text, int color, int codePoints, boolean newline) {}
    private final List<HistoryLine> history = new ArrayList<>();
    private final List<List<FormattedCharSequence>> responseLines = new ArrayList<>();
    private int historyScroll;
    private int maxHistoryScroll;
    private final NeoScrollbar historyBar = new NeoScrollbar(0, 0, 4, 20, Component.literal("คำพูด NPC"))
            .onChange(value -> historyScroll = (int) value);
    private final NeoScrollbar choicesBar = new NeoScrollbar(0, 0, 4, 20, Component.literal("ตัวเลือกบทสนทนา"))
            .onChange(value -> choiceScroll((int) value));
    private NeoScrollbar draggingBar;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;

    public NeoDialogueView(Runnable closeAction) {
        close = new NeoButton(Button.builder(Component.literal("×"), b -> closeAction.run())
                .bounds(0, 0, 13, 13)).style(NeoButton.Style.DESTRUCTIVE)
                .frameRenderer((g, b) -> {
                    int color = b.isHoveredOrFocused() ? 0xFFB76969 : 0xFF899A9F;
                    g.fill(b.getX(), b.getY(), b.getRight(), b.getBottom(), color);
                    g.fill(b.getX() + 1, b.getY() + 1, b.getRight() - 1, b.getBottom() - 1, 0xFF141D24);
                });
        close.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("ปิดบทสนทนา")));
    }

    public void invalidate() { lastWidth = -1; }

    public void clearForSelection() {
        lastSpeech = null;
        history.clear();
        historyScroll = maxHistoryScroll = 0;
        historyBar.visible = false;
        choicesBar.active = false;
        draggingBar = null;
        playback.waitForNpc();
        invalidate();
    }

    public boolean canChoose() { return playback.canChoose(System.nanoTime()); }

    public DialogueTextPlayback.Action pressAdvance(int key, boolean selectsChoice) {
        return playback.pressAdvance(key, selectsChoice, System.nanoTime());
    }

    public void releaseAdvance(int key) { playback.releaseAdvance(key); }

    public void prepare(int width, int height, Dialog dialog, TextBlockClient speech,
                        List<Integer> options, EntityNPCInterface npc) {
        if (layout != null && width == lastWidth && height == lastHeight && dialog == lastDialog
                && speech == lastSpeech && options.equals(lastOptions)) return;
        boolean newSpeech = speech != lastSpeech;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        lastWidth = width; lastHeight = height; lastDialog = dialog; lastSpeech = speech;
        lastOptions = List.copyOf(options);
        // The original zero-option dialogue still supports advancing with the mouse.
        int[] heights = new int[Math.max(1, options.size())];
        java.util.Arrays.fill(heights, font.lineHeight + 6);
        layout = DialogueLayout.create(width, height, font.lineHeight, heights, dialog.hideNPC, 0);
        responseLines.clear();
        int labelWidth = Math.max(10, layout.choices().width()
                - DialogueOptionIcons.TEXT_INSET - DialogueOptionIcons.RIGHT_INSET);
        if (options.isEmpty()) responseLines.add(font.split(Component.literal("ดำเนินต่อ"), labelWidth));
        for (int i = 0; i < options.size(); i++) {
            var option = dialog.options.get(options.get(i));
            String title = option == null ? "" : NoppesStringUtils.formatText(option.title, mc.player, npc);
            List<FormattedCharSequence> wrapped = font.split(Component.literal(title),
                    labelWidth);
            responseLines.add(wrapped);
            heights[i] = Math.max(font.lineHeight + 6, wrapped.size() * (font.lineHeight + 2) + 6);
        }
        layout = DialogueLayout.create(width, height, font.lineHeight, heights, dialog.hideNPC, 0);
        history.clear();
        StringBuilder plainText = new StringBuilder();
        // Only this explicitly captured NPC block is displayed. The mixed CustomNPCs
        // history may contain a player reply while the next server response is in flight.
        if (speech != null) {
            for (int i = 0; i < speech.lines.size(); i++) {
                int speechWidth = Math.max(10, (int) ((layout.text().width() - 7) / DialogueLayout.SPEECH_SCALE));
                List<FormattedCharSequence> wrapped = font.split(speech.lines.get(i), speechWidth);
                if (wrapped.isEmpty()) wrapped = List.of(FormattedCharSequence.EMPTY);
                for (int j = 0; j < wrapped.size(); j++) {
                    FormattedCharSequence sequence = wrapped.get(j);
                    StringBuilder lineText = new StringBuilder();
                    sequence.accept((index, style, codePoint) -> { lineText.appendCodePoint(codePoint); return true; });
                    boolean newline = j == wrapped.size() - 1 && i < speech.lines.size() - 1;
                    history.add(new HistoryLine(sequence, speech.color,
                            lineText.codePointCount(0, lineText.length()), newline));
                    plainText.append(lineText);
                    if (newline) plainText.append('\n');
                }
            }
        }
        if (speech == null) playback.waitForNpc();
        else if (newSpeech) playback.show(plainText.toString(), System.nanoTime());
        else playback.reflow(plainText.toString());
        historyScroll = maxHistoryScroll = 0;
        close.setX(layout.close().x()); close.setY(layout.close().y());
        close.setWidth(layout.close().width()); close.setHeight(layout.close().height());
        close.active = !dialog.disableEsc;
        close.visible = !dialog.disableEsc;
        configureBar(historyBar, layout.text(), maxHistoryScroll, historyScroll);
        configureBar(choicesBar, layout.choices(), layout.maxScroll(), layout.scroll());
        draggingBar = null;
    }

    public int hovered(double x, double y) { return layout == null || !canChoose() ? -1 : layout.choiceAt(x, y); }

    public boolean mouseMoved(double x, double y) {
        boolean moved = x != lastMouseX || y != lastMouseY;
        lastMouseX = x; lastMouseY = y;
        return moved;
    }

    public boolean clickClose(double x, double y, int button) {
        return close.visible && close.active && close.mouseClicked(x, y, button);
    }

    public boolean clickScrollbar(double x, double y, int button) {
        for (NeoScrollbar bar : new NeoScrollbar[] {historyBar, choicesBar}) {
            if (bar.visible && bar.mouseClicked(x, y, button)) {
                draggingBar = bar;
                return true;
            }
        }
        return false;
    }

    public boolean drag(double x, double y, int button, double dx, double dy) {
        return draggingBar != null && draggingBar.mouseDragged(x, y, button, dx, dy);
    }

    public boolean release(double x, double y, int button) {
        if (draggingBar == null) return false;
        draggingBar.mouseReleased(x, y, button);
        draggingBar = null;
        return true;
    }

    public void reveal(int index) {
        if (layout == null || !canChoose()) return;
        choiceScroll(layout.scrollToReveal(index));
        choicesBar.value(layout.scroll());
    }

    public boolean scroll(double x, double y, double delta) {
        if (layout == null) return false;
        int step = (int) Math.round(delta * (Minecraft.getInstance().font.lineHeight + 2) * 3);
        if (layout.text().contains(x, y)) {
            int speechStep = (int) Math.round(delta
                    * DialogueLayout.speechLineHeight(Minecraft.getInstance().font.lineHeight) * 3);
            historyScroll = Math.clamp(historyScroll - speechStep, 0, maxHistoryScroll);
            historyBar.value(historyScroll);
            return true;
        }
        if (layout.choices().contains(x, y) && canChoose()) {
            choiceScroll(layout.scroll() - step);
            choicesBar.value(layout.scroll());
            return true;
        }
        return false;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick,
                       Dialog dialog, List<Integer> options, int selected, EntityNPCInterface npc) {
        if (layout == null) return;
        Font font = Minecraft.getInstance().font;
        choicesBar.active = canChoose();
        var p = layout.panel();
        // Backing is separate from the transparent frame; no dynamic content is baked into assets.
        g.fill(p.x() + 8, p.y() + 5, p.right() - 8, p.bottom() - 5, 0xEA101820);
        frame(g, FRAME, p, 1514, 408, 64, 10, true);
        ornament(g, p.x(), p.y() + p.height() / 2);
        ornament(g, p.right(), p.y() + p.height() / 2);
        if (!dialog.hideNPC && npc != null) {
            var portrait = layout.portrait();
            g.fill(portrait.x() + 5, portrait.y() + 5, portrait.right() - 5, portrait.bottom() - 5, 0xAA07323A);
            frame(g, PORTRAIT, portrait, 390, 464, 64, 8, true);
            int labelHeight = 17;
            DialogueNpcPreviewScope.render(npc, () -> {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g,
                        portrait.x() + 7, portrait.y() + 9, portrait.right() - 7,
                        portrait.bottom() - labelHeight - 5, NPC_PREVIEW_SCALE,
                        0.0625F, mouseX, mouseY, npc);
                g.flush();
            });
            new NeoTitlePlaque(portrait.x() + 2, portrait.bottom() - 22,
                    portrait.width() - 4, 18, npc.getDisplayName()).textColor(0xFFFFD875).render(g, font);
        }
        String npcTitle = npc == null || npc.display == null ? null : npc.display.getTitle();
        if (npcTitle != null && !npcTitle.isBlank()) {
            int titleWidth = Math.min(150, layout.text().width() - 24);
            speaker.bounds(layout.choices().x(), p.y() - 5, titleWidth, 21)
                    .title(NeoBitmapFonts.text(npcTitle)).render(g, font);
        }
        renderHistory(g, font);
        int divider = layout.choices().y() - 4;
        g.fill(layout.choices().x(), divider, layout.choices().right(), divider + 1, 0xFF49606A);
        ornament(g, layout.choices().x() + layout.choices().width() / 2, divider);
        renderResponses(g, font, dialog, options, selected);
        if (historyBar.visible) historyBar.render(g, mouseX, mouseY, partialTick);
        if (choicesBar.visible) choicesBar.render(g, mouseX, mouseY, partialTick);
        String hint = playback.isWaiting() ? "รอคำตอบ…" : !canChoose() ? "Enter / Space แสดงทั้งหมด"
                : options.isEmpty() ? "Enter ดำเนินต่อ" : "↑ ↓ เลือก  |  Enter ยืนยัน";
        g.drawString(font, hint, layout.choices().x() + 3, layout.footerY(), 0xB5C3C8, false);
        if (!dialog.disableEsc) {
            String escape = "Esc ปิด";
            g.drawString(font, escape, layout.choices().right() - font.width(escape),
                    layout.footerY(), 0xB5C3C8, false);
            close.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderHistory(GuiGraphics g, Font font) {
        var box = layout.text();
        var clip = layout.textClip();
        int lineHeight = DialogueLayout.speechLineHeight(font.lineHeight);
        int visible = playback.visibleCodePoints(System.nanoTime());
        int remaining = visible;
        int visibleRows = 0;
        for (HistoryLine line : history) {
            if (remaining <= 0) break;
            visibleRows++;
            remaining -= line.codePoints() + (line.newline() ? 1 : 0);
        }
        int nextMax = Math.max(0, visibleRows * lineHeight - box.height());
        if (nextMax != maxHistoryScroll) {
            boolean following = historyScroll >= maxHistoryScroll;
            maxHistoryScroll = nextMax;
            historyScroll = following ? nextMax : Math.min(historyScroll, nextMax);
            configureBar(historyBar, box, maxHistoryScroll, historyScroll);
        }
        g.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        remaining = visible;
        for (int i = 0; i < history.size() && remaining > 0; i++) {
            HistoryLine line = history.get(i);
            int limit = Math.min(remaining, line.codePoints());
            int y = box.y() + i * lineHeight - historyScroll;
            if (limit > 0 && layout.speechLineVisible(y, lineHeight)) {
                FormattedCharSequence prefix = sink -> {
                    int[] count = {0};
                    return line.text().accept((index, style, cp) -> count[0]++ < limit && sink.accept(index, style, cp));
                };
                g.pose().pushPose();
                try {
                    g.pose().translate(box.x(), y, 0);
                    g.pose().scale(DialogueLayout.SPEECH_SCALE, DialogueLayout.SPEECH_SCALE, 1F);
                    g.drawString(font, prefix, 0, 0, line.color(), false);
                } finally { g.pose().popPose(); }
            }
            remaining -= line.codePoints() + (line.newline() ? 1 : 0);
        }
        g.disableScissor();
    }

    private void renderResponses(GuiGraphics g, Font font, Dialog dialog, List<Integer> options, int selected) {
        var box = layout.choices();
        if (box.height() == 0) return;
        g.enableScissor(box.x(), box.y(), box.right(), box.bottom());
        for (int i = 0; i < Math.max(1, options.size()); i++) {
            var row = layout.row(i);
            if (row.bottom() <= box.y() || row.y() >= box.bottom()) continue;
            boolean ready = canChoose();
            boolean active = ready && i == selected;
            g.fill(row.x() + 3, row.y() + 2, row.right() - 3, row.bottom() - 2,
                    active ? 0xD5125159 : 0xA510171E);
            g.setColor(active ? 0.55F : 1F, 1F, 1F, ready ? 1F : 0.45F);
            frame(g, RESPONSE, row, 946, 98, 40, 5, false);
            g.setColor(1F, 1F, 1F, 1F);
            var option = options.isEmpty() ? null : dialog.options.get(options.get(i));
            int color = !ready ? 0x697B82 : option == null ? 0xE7ECEF : option.optionColor;
            DialogueOptionKind kind = option != null ? DialogueOptionIcons.kindFor(option)
                    : options.isEmpty() ? DialogueOptionKind.DIALOGUE : DialogueOptionKind.UNKNOWN;
            DialogueOptionIcons.render(g, kind, row.x() + 5, row.y() + 3, 10, ready, active);
            List<FormattedCharSequence> lines = responseLines.get(i);
            for (int j = 0; j < lines.size(); j++)
                g.drawString(font, lines.get(j), row.x() + DialogueOptionIcons.TEXT_INSET,
                        row.y() + 3 + j * (font.lineHeight + 2), color, false);
        }
        g.disableScissor();
    }

    private void choiceScroll(int value) {
        if (layout == null) return;
        layout = DialogueLayout.create(lastWidth, lastHeight, Minecraft.getInstance().font.lineHeight,
                layout.rowHeights(), lastDialog.hideNPC, value);
    }

    private static void configureBar(NeoScrollbar bar, DialogueLayout.Box box, int max, int value) {
        bar.setX(box.right() - 5); bar.setY(box.y()); bar.setHeight(Math.max(4, box.height()));
        bar.visible = max > 0 && box.height() > 0;
        bar.active = bar.visible;
        bar.range(box.height(), box.height() + max).value(value);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/dialogue/" + name + ".png");
    }

    private static void ornament(GuiGraphics g, int x, int y) {
        RenderSystem.enableBlend();
        g.blit(DIAMOND, x - 6, y - 6, 12, 12, 0, 0, 88, 88, 88, 88);
    }

    /** Mid-edge samples deliberately exclude source diamonds; ornaments are drawn separately. */
    private static void frame(GuiGraphics g, ResourceLocation texture, DialogueLayout.Box box,
                              int sourceWidth, int sourceHeight, int sourceCap, int corner, boolean emptyCenter) {
        RenderSystem.enableBlend();
        corner = Math.min(corner, Math.min(box.width(), box.height()) / 2);
        int[] widths = {corner, box.width() - corner * 2, corner};
        int[] heights = {corner, box.height() - corner * 2, corner};
        int y = box.y();
        for (int row = 0; row < 3; row++) {
            int x = box.x();
            for (int col = 0; col < 3; col++) {
                if (!(emptyCenter && row == 1 && col == 1) && widths[col] > 0 && heights[row] > 0) {
                    int u = col == 0 ? 0 : col == 2 ? sourceWidth - sourceCap : sourceCap;
                    int v = row == 0 ? 0 : row == 2 ? sourceHeight - sourceCap : sourceCap + 8;
                    int sw = col == 1 ? sourceWidth - sourceCap * 2 : sourceCap;
                    int sh = row == 1 ? 1 : sourceCap;
                    g.blit(texture, x, y, widths[col], heights[row], u, v, sw, sh, sourceWidth, sourceHeight);
                }
                x += widths[col];
            }
            y += heights[row];
        }
    }
}
