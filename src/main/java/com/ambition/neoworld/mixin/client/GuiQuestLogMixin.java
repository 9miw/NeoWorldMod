package com.ambition.neoworld.mixin.client;

import com.ambition.neoworld.client.gui.integration.QuestLogTracking;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.player.GuiQuestLog;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiQuestLog.class, remap = false)
public abstract class GuiQuestLogMixin extends GuiNPCInterface {
    @Unique private NeoButton neoworld$trackButton;

    // Custom NPCs calls init() directly for category, quest and page changes; ScreenEvent alone misses these.
    @Inject(method = "init", at = @At("RETURN"))
    private void neoworld$addTrackingButton(CallbackInfo ci) {
        if (neoworld$trackButton != null) removeWidget(neoworld$trackButton);
        neoworld$trackButton = QuestLogTracking.createButton((GuiQuestLog) (Object) this);
        addRenderableWidget(neoworld$trackButton);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void neoworld$updateTrackingButton(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        if (neoworld$trackButton != null)
            QuestLogTracking.update((GuiQuestLog) (Object) this, neoworld$trackButton);
    }
}
