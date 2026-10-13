package com.ambition.neoworld.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EntityType;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.client.renderer.patched.entity.PPlayerRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;
import yesman.epicfight.model.armature.HumanoidArmature;

public final class EpicFightFashionLayer extends PatchedLayer<AbstractClientPlayer,
        AbstractClientPlayerPatch<AbstractClientPlayer>, PlayerModel<AbstractClientPlayer>,
        RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> {
    private final DuelistFashionRenderer renderer = new DuelistFashionRenderer();
    private final OniCurseWingsRenderer oniWingsRenderer = new OniCurseWingsRenderer();

    public static void register() {
        EpicFightClientEventHooks.Registry.MODIFY_PATCHED_ENTITY.registerEvent(event -> {
            if (event.get(EntityType.PLAYER) instanceof PPlayerRenderer playerRenderer) {
                playerRenderer.addPatchedLayerAlways(FashionLayer.class, new EpicFightFashionLayer());
            }
        }, "neoworld:duelist_fashion");
    }


    @Override
    protected void renderLayer(AbstractClientPlayerPatch<AbstractClientPlayer> patch, AbstractClientPlayer player,
                               RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> original,
                               PoseStack poses, MultiBufferSource buffers, int light, OpenMatrix4f[] joints,
                               float bob, float headYaw, float headPitch, float partialTick) {
        if (!(patch.getArmature() instanceof HumanoidArmature armature)) return;
        renderer.renderParts(poses, buffers, light, player, partialTick, (bone, stack) -> {
            var joint = switch (bone) {
                case "Head" -> armature.head;
                case "Body" -> armature.chest;
                case "Right Arm" -> armature.armR;
                case "Left Arm" -> armature.armL;
                case "Right leggings" -> armature.thighR;
                case "Left leggings" -> armature.thighL;
                case "Right Boot" -> armature.legR;
                default -> armature.legL;
            };
            // Same bind-pose correction as Epic Fight's SkinnedMesh; never mutate shared pose matrices.
            MathUtils.mulStack(stack, new OpenMatrix4f(joints[joint.getId()]).mulBack(joint.getToOrigin()));
        });
        if (player.isInvisible()) return;
        var itemRenderer = net.minecraft.client.Minecraft.getInstance().getItemRenderer();

        // Head slot
        var hat = com.ambition.neoworld.client.state.ClientFashionData.get(player.getId(),
                com.ambition.neoworld.fashion.data.FashionSlot.HEAD);
        if (!hat.isEmpty() && !hat.is(com.ambition.neoworld.registry.ModItems.DUELIST_IRON_HEAD.get())) {
            poses.pushPose();
            try {
                MathUtils.mulStack(poses, new OpenMatrix4f(joints[armature.head.getId()]));
                poses.scale(-1, -1, 1);
                poses.translate(0, -0.25, 0);
                itemRenderer.renderStatic(player, hat,
                        net.minecraft.world.item.ItemDisplayContext.HEAD, false, poses, buffers, player.level(),
                        light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, player.getId());
            } finally {
                poses.popPose();
            }
        }

        // Face slot
        var face = com.ambition.neoworld.client.state.ClientFashionData.get(player.getId(),
                com.ambition.neoworld.fashion.data.FashionSlot.FACE);
        if (!face.isEmpty()) {
            poses.pushPose();
            try {
                MathUtils.mulStack(poses, new OpenMatrix4f(joints[armature.head.getId()]));
                poses.scale(-1, -1, 1);
                poses.translate(0, -0.25, 0);
                itemRenderer.renderStatic(player, face,
                        net.minecraft.world.item.ItemDisplayContext.HEAD, false, poses, buffers, player.level(),
                        light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, player.getId());
            } finally {
                poses.popPose();
            }
        }

        // Back slot (specialized renderer)
        oniWingsRenderer.renderEpicFight(poses, buffers, light, player, armature, joints, partialTick);

        // Back slot (generic fallback for other items)
        var back = com.ambition.neoworld.client.state.ClientFashionData.get(player.getId(),
                com.ambition.neoworld.fashion.data.FashionSlot.BACK);
        if (!back.isEmpty() && !back.is(com.ambition.neoworld.registry.ModItems.ONI_CURSE_WINGS.get())) {
            poses.pushPose();
            try {
                MathUtils.mulStack(poses, new OpenMatrix4f(joints[armature.chest.getId()]));
                poses.scale(-1, -1, 1);
                poses.translate(0, -0.25, 0);
                itemRenderer.renderStatic(player, back,
                        net.minecraft.world.item.ItemDisplayContext.HEAD, false, poses, buffers, player.level(),
                        light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, player.getId());
            } finally {
                poses.popPose();
            }
        }
    }
}
