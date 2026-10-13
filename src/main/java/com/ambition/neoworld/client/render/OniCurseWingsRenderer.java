package com.ambition.neoworld.client.render;

import com.ambition.neoworld.client.state.ClientFashionData;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.ambition.neoworld.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.model.armature.HumanoidArmature;

public final class OniCurseWingsRenderer {

    public static boolean isEquipped(AbstractClientPlayer player) {
        return ClientFashionData.get(player.getId(), FashionSlot.BACK).is(ModItems.ONI_CURSE_WINGS.get());
    }

    public void renderVanilla(PoseStack poses, MultiBufferSource buffers, int light,
                              AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model, float partialTick) {
        if (player.isInvisible()) return;
        ItemStack stack = ClientFashionData.get(player.getId(), FashionSlot.BACK);
        if (!stack.is(ModItems.ONI_CURSE_WINGS.get())) return;

        poses.pushPose();
        model.body.translateAndRotate(poses);
        poses.translate(0.0D, -0.25D, 0.0D);
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(player, stack, ItemDisplayContext.HEAD, false, poses, buffers,
                player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        poses.popPose();
    }

    public void renderEpicFight(PoseStack poses, MultiBufferSource buffers, int light,
                                AbstractClientPlayer player, HumanoidArmature armature,
                                OpenMatrix4f[] joints, float partialTick) {
        if (player.isInvisible()) return;
        ItemStack stack = ClientFashionData.get(player.getId(), FashionSlot.BACK);
        if (!stack.is(ModItems.ONI_CURSE_WINGS.get())) return;

        poses.pushPose();
        try {
            MathUtils.mulStack(poses, new OpenMatrix4f(joints[armature.chest.getId()]));
            poses.scale(0.5f, 0.5f, 0.5f);
            poses.translate(0, 0.85, 0);
            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            itemRenderer.renderStatic(player, stack, ItemDisplayContext.HEAD, false, poses, buffers,
                    player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        } finally {
            poses.popPose();
        }
    }
}
