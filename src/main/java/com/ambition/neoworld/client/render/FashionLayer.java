package com.ambition.neoworld.client.render;

import com.ambition.neoworld.client.state.ClientFashionData;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class FashionLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final DuelistFashionRenderer duelistRenderer = new DuelistFashionRenderer();
    private final OniCurseWingsRenderer oniWingsRenderer = new OniCurseWingsRenderer();

    public FashionLayer(PlayerRenderer renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player.isInvisible()) {
            return;
        }

        // Render specialized set / item renderers
        duelistRenderer.renderVanilla(poseStack, buffer, packedLight, player, getParentModel(), partialTick);
        oniWingsRenderer.renderVanilla(poseStack, buffer, packedLight, player, getParentModel(), partialTick);

        ItemRenderer itemRenderer = minecraft.getItemRenderer();

        // Head slot (generic fallback)
        ItemStack headStack = ClientFashionData.get(player.getId(), FashionSlot.HEAD);
        if (!headStack.isEmpty() && !DuelistFashionRenderer.equipped(player, FashionSlot.HEAD,
                com.ambition.neoworld.registry.ModItems.DUELIST_IRON_HEAD.get())) {
            poseStack.pushPose();
            getParentModel().head.translateAndRotate(poseStack);
            poseStack.translate(0.0D, -0.25D, 0.0D);
            itemRenderer.renderStatic(player, headStack, ItemDisplayContext.HEAD, false, poseStack, buffer,
                    player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId());
            poseStack.popPose();
        }

        // Face slot (generic fallback)
        ItemStack faceStack = ClientFashionData.get(player.getId(), FashionSlot.FACE);
        if (!faceStack.isEmpty()) {
            poseStack.pushPose();
            getParentModel().head.translateAndRotate(poseStack);
            poseStack.translate(0.0D, -0.25D, 0.0D);
            itemRenderer.renderStatic(player, faceStack, ItemDisplayContext.HEAD, false, poseStack, buffer,
                    player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId());
            poseStack.popPose();
        }

        // Back slot (generic fallback for non-oni-wings items)
        ItemStack backStack = ClientFashionData.get(player.getId(), FashionSlot.BACK);
        if (!backStack.isEmpty() && !backStack.is(com.ambition.neoworld.registry.ModItems.ONI_CURSE_WINGS.get())) {
            poseStack.pushPose();
            getParentModel().body.translateAndRotate(poseStack);
            poseStack.translate(0.0D, -0.25D, 0.0D);
            itemRenderer.renderStatic(player, backStack, ItemDisplayContext.HEAD, false, poseStack, buffer,
                    player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId());
            poseStack.popPose();
        }
    }
}
