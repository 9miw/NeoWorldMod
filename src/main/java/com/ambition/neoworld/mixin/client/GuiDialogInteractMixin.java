package com.ambition.neoworld.mixin.client;

import com.ambition.neoworld.client.gui.dialogue.NeoDialogueView;
import com.ambition.neoworld.client.gui.dialogue.DialogueTextPlayback;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.TextBlockClient;
import noppes.npcs.client.gui.player.GuiDialogInteract;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.data.Dialog;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Keep CustomNPCs packets/lifecycle and its wheel mode; skin only the normal response list. */
@Mixin(value = GuiDialogInteract.class, remap = false)
public abstract class GuiDialogInteractMixin extends GuiNPCInterface {
    @Shadow private Dialog dialog;
    @Shadow private int selected;
    @Shadow private List<TextBlockClient> lines;
    @Shadow private List<Integer> options;
    @Shadow private void handleDialogSelection() { throw new AssertionError(); }
    @Unique private NeoDialogueView neoworld$dialogueView;
    @Unique private TextBlockClient neoworld$npcSpeech;

    @Unique private boolean neoworld$listMode() {
        return dialog != null && !dialog.showWheel && !hasSubGui();
    }

    @Unique private NeoDialogueView neoworld$view() {
        if (neoworld$dialogueView == null) {
            neoworld$dialogueView = new NeoDialogueView(() ->
                    ((GuiDialogInteract) (Object) this).keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        }
        neoworld$dialogueView.prepare(width, height, dialog, neoworld$npcSpeech, options, npc);
        return neoworld$dialogueView;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void neoworld$resize(CallbackInfo ci) {
        if (neoworld$dialogueView != null) neoworld$dialogueView.invalidate();
    }

    @Inject(method = "appendDialog", at = @At("RETURN"))
    private void neoworld$append(Dialog next, CallbackInfo ci) {
        // appendDialog adds the NPC block; handleDialogSelection adds player blocks later.
        neoworld$npcSpeech = lines.isEmpty() ? null : lines.getLast();
        if (!next.showWheel) selected = 0;
        if (neoworld$dialogueView != null) neoworld$dialogueView.invalidate();
    }

    @Inject(method = "handleDialogSelection", at = @At("HEAD"))
    private void neoworld$clearSpeech(CallbackInfo ci) {
        if (!neoworld$listMode()) return;
        neoworld$npcSpeech = null;
        if (neoworld$dialogueView != null) neoworld$dialogueView.clearForSelection();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void neoworld$render(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick, CallbackInfo ci) {
        if (!neoworld$listMode()) return;
        NeoDialogueView view = neoworld$view();
        selected = options.isEmpty() ? 0 : Math.clamp(selected, 0, options.size() - 1);
        if (view.mouseMoved(mouseX, mouseY)) {
            int hovered = view.hovered(mouseX, mouseY);
            if (hovered >= 0) selected = hovered;
        }
        renderBackground(graphics, mouseX, mouseY, partialTick);
        view.render(graphics, mouseX, mouseY, partialTick, dialog, options, selected, npc);
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void neoworld$click(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!neoworld$listMode()) return;
        NeoDialogueView view = neoworld$view();
        if (view.clickClose(x, y, button)) { cir.setReturnValue(true); return; }
        if (view.clickScrollbar(x, y, button)) { cir.setReturnValue(true); return; }
        int clicked = view.hovered(x, y);
        if (button == 0 && clicked >= 0) {
            selected = clicked;
            handleDialogSelection();
        }
        // Do not let the original Y-only selection submit clicks outside a response rectangle.
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void neoworld$key(int key, int scan, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!neoworld$listMode()) return;
        NeoDialogueView view = neoworld$view();
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (enter || key == GLFW.GLFW_KEY_SPACE) {
            DialogueTextPlayback.Action action = view.pressAdvance(key, enter);
            if (action == DialogueTextPlayback.Action.SELECT) {
                selected = options.isEmpty() ? 0 : Math.clamp(selected, 0, options.size() - 1);
                handleDialogSelection();
            }
            cir.setReturnValue(true);
            return;
        }
        boolean up = key == GLFW.GLFW_KEY_UP || minecraft.options.keyUp.matches(key, scan);
        boolean down = key == GLFW.GLFW_KEY_DOWN || minecraft.options.keyDown.matches(key, scan);
        if (up || down) {
            if (view.canChoose() && !options.isEmpty()) {
                selected = Math.clamp(selected + (up ? -1 : 1), 0, options.size() - 1);
                neoworld$view().reveal(selected);
            }
            cir.setReturnValue(true);
        }
        // Escape/inventory key keep the original close, packet and quest-completion path.
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (neoworld$dialogueView != null) neoworld$dialogueView.releaseAdvance(key);
        return super.keyReleased(key, scan, modifiers);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (neoworld$listMode()) return neoworld$view().scroll(x, y, vertical);
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (neoworld$listMode()) return neoworld$view().drag(x, y, button, dx, dy);
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (neoworld$listMode()) return neoworld$view().release(x, y, button);
        return super.mouseReleased(x, y, button);
    }
}
