package com.ambition.neoworld.client.render;

import com.ambition.neoworld.client.state.ClientFashionData;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.ambition.neoworld.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.BiConsumer;

/** One shared geometry, rendered by part without mutating the cached bones. */
public final class DuelistFashionRenderer extends GeoObjectRenderer<DuelistFashionRenderer.Preview> {
    private static final ResourceLocation GEO = id("geo/fashion/duelist.geo.json");
    private static final ResourceLocation TEXTURE = id("textures/entity/fashion/duelist_iron.png");
    private static final ResourceLocation ANIMATION = id("animations/fashion/duelist.animation.json");
    private final Preview preview = new Preview();

    public DuelistFashionRenderer() {
        super(new GeoModel<>() {
            @Override public ResourceLocation getModelResource(Preview value) { return GEO; }
            @Override public ResourceLocation getTextureResource(Preview value) { return TEXTURE; }
            @Override public ResourceLocation getAnimationResource(Preview value) { return ANIMATION; }
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("neoworld", path);
    }

    public static boolean equipped(AbstractClientPlayer player, FashionSlot slot, Item item) {
        return ClientFashionData.get(player.getId(), slot).is(item);
    }

    public void renderVanilla(PoseStack poses, MultiBufferSource buffers, int light,
                              AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model, float partialTick) {
        renderParts(poses, buffers, light, player, partialTick, (bone, stack) -> {
            ModelPart part = switch (bone) {
                case "Head" -> model.head;
                case "Body" -> model.body;
                case "Right Arm" -> model.rightArm;
                case "Left Arm" -> model.leftArm;
                case "Right leggings", "Right Boot" -> model.rightLeg;
                default -> model.leftLeg;
            };
            part.translateAndRotate(stack);
            stack.scale(-1, -1, 1);
            // Geometry coordinates use feet as origin; vanilla parts use their own pivots.
            switch (bone) {
                case "Head", "Body" -> stack.translate(0, -24.0 / 16, 0);
                case "Right Arm" -> stack.translate(-5.0 / 16, -22.0 / 16, 0);
                case "Left Arm" -> stack.translate(5.0 / 16, -22.0 / 16, 0);
                case "Right leggings", "Right Boot" -> stack.translate(-1.9 / 16, -12.0 / 16, 0);
                default -> stack.translate(1.9 / 16, -12.0 / 16, 0);
            }
        });
    }

    public void renderParts(PoseStack poses, MultiBufferSource buffers, int light,
                            AbstractClientPlayer player, float partialTick,
                            BiConsumer<String, PoseStack> transform) {
        if (player.isInvisible()) return;
        RenderType type = RenderType.entityCutoutNoCull(TEXTURE);
        this.animatable = preview;
        for (FashionSlot slot : new FashionSlot[]{FashionSlot.HEAD, FashionSlot.BODY, FashionSlot.LEGS, FashionSlot.FEET}) {
            Item expected = switch (slot) {
                case HEAD -> ModItems.DUELIST_IRON_HEAD.get();
                case BODY -> ModItems.DUELIST_IRON_BODY.get();
                case LEGS -> ModItems.DUELIST_IRON_LEGS.get();
                default -> ModItems.DUELIST_IRON_FEET.get();
            };
            if (!equipped(player, slot, expected)) continue;
            var baked = getGeoModel().getBakedModel(GEO);
            String[] bones = switch (slot) {
                case HEAD -> new String[]{"Head"};
                case BODY -> new String[]{"Body", "Right Arm", "Left Arm"};
                case LEGS -> new String[]{"Right leggings", "Left leggings"};
                default -> new String[]{"Right Boot", "Left Boot"};
            };
            for (String name : bones) {
                var bone = baked.getBone(name).orElseThrow(() -> new IllegalStateException("Duelist bone missing: " + name));
                poses.pushPose();
                try {
                    transform.accept(name, poses);
                    // Re-render existing bones: the supplied asset has no decorative animations.
                    renderRecursively(poses, preview, bone, type, buffers, buffers.getBuffer(type), true,
                            partialTick, light, OverlayTexture.NO_OVERLAY, -1);
                } finally {
                    poses.popPose();
                }
            }
        }
        doPostRenderCleanup();
    }

    public static final class Preview implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
        @Override public double getTick(Object object) { return 0; }
    }
}
