package com.ambition.neoworld.mixin.client;

import com.ambition.neoworld.client.render.UpgradeWeaponOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class UpgradeItemRenderMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", shift = At.Shift.BEFORE))
    private void neoworld$upgradeOverlay(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                         PoseStack poses, MultiBufferSource buffers, int light, int overlay,
                                         BakedModel model, CallbackInfo ci) {
        UpgradeWeaponOverlay.render(stack, context, poses, buffers, model);
    }
}
