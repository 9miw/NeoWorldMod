package com.ambition.neoworld.client.render;

import com.ambition.neoworld.registry.ModItems;
import com.ambition.neoworld.upgrade.UpgradeEffectStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Model-local emissive shells; no post-processing or dynamic world light. */
public final class UpgradeNeonAura {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("neoworld", "textures/effect/neon_white.png");
    private static final Map<BakedModel, List<Face>> CACHE = new WeakHashMap<>();
    private record Face(float[] xyz, float nx, float ny, float nz) {}
    private UpgradeNeonAura() {}

    public static void render(ItemStack stack, BakedModel model, int level, double time,
                               PoseStack poses, MultiBufferSource buffers) {
        if (level < 8 || level > 15 || model.isCustomRenderer()) return;
        if (!stack.is(ModItems.LUNAR_SWORD.get())) return;
        var style = UpgradeEffectStyle.forLevel(level);
        var faces = CACHE.computeIfAbsent(model, UpgradeNeonAura::faces);
        float strength = level <= 9 ? .65F : level <= 12 ? .8F : level <= 14 ? .95F : 1.15F;
        float pulse = (float) (.88 + .12 * Math.sin(time * (level == 15 ? .12 : .07)));
        var vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        var pose = poses.last();
        for (int layer = 0; layer < 3; layer++) {
            float spread = (layer == 0 ? .008F : layer == 1 ? .028F : .055F) * strength;
            float alpha = (layer == 0 ? .30F : layer == 1 ? .10F : .035F) * strength * pulse;
            for (Face face : faces) {
                float cx = 0, cy = 0;
                for (int vertex = 0; vertex < 4; vertex++) {
                    cx += face.xyz[vertex * 3] * .25F;
                    cy += face.xyz[vertex * 3 + 1] * .25F;
                }
                // Known blade regions of these two models, excluding grip and crossguard.
                if (cy < .65F) continue;
                for (int vertex = 0; vertex < 4; vertex++) {
                    float x = face.xyz[vertex * 3], y = face.xyz[vertex * 3 + 1], z = face.xyz[vertex * 3 + 2];
                    // Expand about the model's centerline, retaining the original blade silhouette.
                    float ox = x - .5F, oz = z - .5F;
                    float radial = (float) Math.sqrt(ox * ox + oz * oz);
                    if (radial > .0001F) { ox /= radial; oz /= radial; }
                    vertices.addVertex(pose.pose(), x + ox * spread + face.nx * .001F,
                                    y + face.ny * .001F,
                                    z + oz * spread + face.nz * .001F)
                            .setColor(style.red(), style.green(), style.blue(), alpha).setUv(.5F, .5F)
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                            .setNormal(pose, face.nx, face.ny, face.nz);
                }
            }
        }
    }

    private static List<Face> faces(BakedModel model) {
        List<Face> result = new ArrayList<>();
        for (int side = -1; side < 6; side++) {
            for (var quad : model.getQuads(null, side < 0 ? null : Direction.values()[side], RandomSource.create(42))) {
                if (quad.getSprite().contents().name().getPath().contains("animations/")) continue;
                int[] data = quad.getVertices();
                int stride = data.length / 4;
                if (stride < 3) continue;
                float[] xyz = new float[12];
                for (int index = 0; index < 4; index++) for (int axis = 0; axis < 3; axis++)
                    xyz[index * 3 + axis] = Float.intBitsToFloat(data[index * stride + axis]);
                var normal = quad.getDirection();
                result.add(new Face(xyz, normal.getStepX(), normal.getStepY(), normal.getStepZ()));
            }
        }
        return List.copyOf(result);
    }
}
