package com.ambition.neoworld.mixin.client;

import com.ambition.neoworld.client.gui.dialogue.DialogueNpcPreviewScope;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both native name and title share this method; UI plaques are rendered separately. */
@Mixin(value = RenderNPCInterface.class, remap = false)
public abstract class NpcPreviewNameTagMixin {
    @Inject(method = "renderLivingLabel", at = @At("HEAD"), cancellable = true)
    private void neoworld$hidePreviewLabels(EntityNPCInterface npc, PoseStack pose,
                                            MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (DialogueNpcPreviewScope.isRendering(npc)) ci.cancel();
    }
}
