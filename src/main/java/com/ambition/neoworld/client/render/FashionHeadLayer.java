package com.ambition.neoworld.client.render;

import com.ambition.neoworld.client.ClientFashionData;
import com.ambition.neoworld.data.fashion.FashionSlot;
import com.ambition.neoworld.item.fashion.FashionRenderItem;
import com.ambition.neoworld.item.fashion.FashionRenderMode;
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

public class FashionHeadLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public FashionHeadLayer(PlayerRenderer renderer) {
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

        ItemStack stack = ClientFashionData.get(player.getId(), FashionSlot.HEAD);
        if (stack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        getParentModel().head.translateAndRotate(poseStack);
        poseStack.translate(0.0D, -0.25D, 0.0D);
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        if (stack.getItem() instanceof FashionRenderItem fashionItem
                && fashionItem.fashionRenderMode() == FashionRenderMode.GECKOLIB_MODEL) {
            // GeckoLib fashion items are identified here; until a concrete Geo renderer is registered
            // for the item, vanilla item rendering remains the safe fallback.
        }
        itemRenderer.renderStatic(player, stack, ItemDisplayContext.HEAD, false, poseStack, buffer,
                player.level(), packedLight, OverlayTexture.NO_OVERLAY, player.getId());
        poseStack.popPose();
    }
}
