package com.ambition.neoworld.client.render;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModItems;
import com.ambition.neoworld.upgrade.UpgradeData;
import com.ambition.neoworld.upgrade.UpgradeEffectStyle;
import com.ambition.neoworld.upgrade.UpgradeConfig;
import com.ambition.neoworld.upgrade.UpgradeService;
import com.ambition.neoworld.upgrade.UpgradeOrbitMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import java.util.Map;
import java.util.WeakHashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;

/** Local model coordinates: follows both vanilla and Epic Fight item transforms without world-space guesses. */
public final class UpgradeWeaponOverlay {
    private static final ResourceLocation EDGE = texture("effect/example_sword_edge");
    private static final ResourceLocation HALO = texture("effect/example_sword_halo");
    private static final ResourceLocation SPARK = texture("particle/upgrade_spark");
    private static final ResourceLocation RUNE = texture("particle/upgrade_rune");
    private static final Map<BakedModel, Bounds> MODEL_BOUNDS = new WeakHashMap<>();
    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float x(float t) { return minX + (maxX - minX) * t; }
        float y(float t) { return minY + (maxY - minY) * t; }
        float z(float t) { return minZ + (maxZ - minZ) * t; }
    }
    private static final Bounds DEFAULT_BOUNDS = new Bounds(0, 0, 0, 1, 1, 1);
    private record Blade(UpgradeOrbitMath.Point start, UpgradeOrbitMath.Point end, double radius) {}
    private static Blade blade(Bounds bounds) {
        float dx = bounds.maxX - bounds.minX, dy = bounds.maxY - bounds.minY, dz = bounds.maxZ - bounds.minZ;
        float x = bounds.x(.5F), y = bounds.y(.5F), z = bounds.z(.5F);
        if (dy > dx * 1.35 && dy > dz * 1.35)
            return new Blade(new UpgradeOrbitMath.Point(x, bounds.y(.3F), z), new UpgradeOrbitMath.Point(x, bounds.y(.96F), z), Math.max(.07, Math.max(dx, dz) * .5 + .035));
        if (dx > dy * 1.35 && dx > dz * 1.35)
            return new Blade(new UpgradeOrbitMath.Point(bounds.x(.3F), y, z), new UpgradeOrbitMath.Point(bounds.x(.96F), y, z), Math.max(.07, Math.max(dy, dz) * .5 + .035));
        if (dz > dx * 1.35 && dz > dy * 1.35)
            return new Blade(new UpgradeOrbitMath.Point(x, y, bounds.z(.3F)), new UpgradeOrbitMath.Point(x, y, bounds.z(.96F)), Math.max(.07, Math.max(dx, dy) * .5 + .035));
        // Flat generated sword sprites run from lower-left hilt to upper-right tip.
        return new Blade(new UpgradeOrbitMath.Point(bounds.x(.4F), bounds.y(.4F), z),
                new UpgradeOrbitMath.Point(bounds.x(.95F), bounds.y(.95F), z), Math.max(.08, Math.min(dx, dy) * .12));
    }
    private UpgradeWeaponOverlay() {}
    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/" + path + ".png");
    }

    public static void render(ItemStack stack, ItemDisplayContext context, PoseStack poses,
                               MultiBufferSource buffers, BakedModel model) {
        if (context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND && context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) return;
        int level = UpgradeData.level(stack);
        var style = UpgradeEffectStyle.forLevel(level);
        if (style.interval() == 0 || stack.getItem() instanceof ArmorItem || !UpgradeConfig.SPEC.isLoaded()
                || !UpgradeService.eligible(stack)) return;
        boolean exampleSword = stack.is(ModItems.EXAMPLE_ITEMSWORD.get());
        Bounds bounds = exampleSword || model.isCustomRenderer() ? DEFAULT_BOUNDS
                : MODEL_BOUNDS.computeIfAbsent(model, UpgradeWeaponOverlay::measureBounds);
        Blade blade = blade(bounds);
        var mc = Minecraft.getInstance();
        double time = mc.level == null ? 0 : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        float pulse = (float) (0.85 + 0.15 * Math.sin(time * 0.13));
        if (exampleSword && !model.isCustomRenderer() && level >= 4) {
            // Blade-only masks, two faces of the generated diamond-sword model (7.5/16..8.5/16 deep).
            float intensity = level == 15 ? 0.95F : level >= 10 ? 0.8F : 0.6F;
            plane(poses, buffers, EDGE, 0, 0, 1, 1, 0.535F, style, intensity * pulse);
            plane(poses, buffers, EDGE, 0, 0, 1, 1, 0.465F, style, intensity * pulse);
            plane(poses, buffers, HALO, 0, 0, 1, 1, 0.537F, style, intensity * 0.4F * pulse);
            plane(poses, buffers, HALO, 0, 0, 1, 1, 0.463F, style, intensity * 0.4F * pulse);
        }
        int sparks = level <= 3 ? 2 : level <= 6 ? 3 : level <= 9 ? 4 : level <= 12 ? 5 : level <= 14 ? 6 : 8;
        for (int index = 0; index < sparks; index++) {
            double along = (index + .5) / sparks;
            double phase = time * .11 + along * Math.PI * 3;
            var point = UpgradeOrbitMath.position(blade.start, blade.end, along, phase, blade.radius);
            if (level >= 4) orbitTrace(poses, buffers, blade, along, phase, level, style);
            float alpha = level == 15 ? (float) (.9 + .1 * (.5 + .5 * Math.sin(phase * 2)))
                    : (float) (.45 + .4 * (.5 + .5 * Math.sin(phase * 2)));
            float radius = level == 15 ? 0.035F : 0.024F;
            sprite(poses, buffers, SPARK, (float) point.x(), (float) point.y(), (float) point.z(),
                    radius, style, alpha);
        }
        if (style.runes()) {
            int runes = level == 15 ? 5 : level >= 10 ? 3 : 2;
            for (int index = 0; index < runes; index++) {
                double along = (index + .5) / runes;
                double phase = time * .065 + along * Math.PI * 3;
                var point = UpgradeOrbitMath.position(blade.start, blade.end, along, phase, blade.radius * 1.2);
                float radius = level == 15 ? 0.09F : 0.07F;
                billboard(poses, buffers, RUNE, point, radius, style,
                        level == 15 ? (float) (.95 + .05 * Math.sin(phase)) : (float) (.8 + .15 * Math.sin(phase)));
            }
        }
    }

    private static void billboard(PoseStack poses, MultiBufferSource buffers, ResourceLocation texture,
                                   UpgradeOrbitMath.Point point, float radius, UpgradeEffectStyle style, float alpha) {
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poses.last().pose());
        if (Math.abs(view.determinant()) < 1e-8F) return;
        Matrix4f inverse = view.invert();
        Vector3f right = inverse.transformDirection(new Vector3f(1, 0, 0)).normalize().mul(radius);
        Vector3f up = inverse.transformDirection(new Vector3f(0, 1, 0)).normalize().mul(radius);
        var vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        var pose = poses.last();
        for (int index = 0; index < 4; index++) {
            float sx = index == 0 || index == 3 ? -1 : 1, sy = index < 2 ? -1 : 1;
            vertex(vertices, pose, (float) point.x() + sx * right.x + sy * up.x,
                    (float) point.y() + sx * right.y + sy * up.y,
                    (float) point.z() + sx * right.z + sy * up.z,
                    sx < 0 ? 0 : 1, sy < 0 ? 1 : 0, style, alpha);
        }
    }

    private static void orbitTrace(PoseStack poses, MultiBufferSource buffers, Blade blade, double along,
                                    double phase, int level, UpgradeEffectStyle style) {
        int segments = level >= 13 ? 8 : 5;
        var previous = UpgradeOrbitMath.position(blade.start, blade.end, along, phase, blade.radius);
        for (int segment = 1; segment <= segments; segment++) {
            double tailAlong = Math.max(0, along - segment * .012);
            var next = UpgradeOrbitMath.position(blade.start, blade.end, tailAlong, phase - segment * .15, blade.radius);
            float fade = 1 - segment / (float) (segments + 1);
            ribbon(poses, buffers, previous, next, level == 15 ? .014F : level >= 13 ? .010F : .007F,
                    style, fade * (level == 15 ? .95F : .55F));
            ribbon(poses, buffers, previous, next, .0025F, new UpgradeEffectStyle(0xFFFFFF, 0, false, false), fade * .85F);
            previous = next;
        }
    }

    private static void ribbon(PoseStack poses, MultiBufferSource buffers, UpgradeOrbitMath.Point start,
                                 UpgradeOrbitMath.Point end, float width, UpgradeEffectStyle style, float alpha) {
        Vector3f tangent = new Vector3f((float) (end.x() - start.x()), (float) (end.y() - start.y()), (float) (end.z() - start.z()));
        if (tangent.lengthSquared() < 1e-10F) return;
        tangent.normalize();
        Vector3f side = tangent.cross(Math.abs(tangent.z) < .9 ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0), new Vector3f()).normalize();
        var vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(SPARK));
        var pose = poses.last();
        for (int plane = 0; plane < 2; plane++) {
            Vector3f offset = plane == 0 ? new Vector3f(side) : tangent.cross(side, new Vector3f()).normalize();
            offset.mul(width);
            for (int index = 0; index < 4; index++) {
                var point = index < 2 ? start : end;
                float sign = index == 0 || index == 3 ? -1 : 1;
                vertex(vertices, pose, (float) point.x() + sign * offset.x, (float) point.y() + sign * offset.y,
                        (float) point.z() + sign * offset.z, .5F, .5F, style, alpha);
            }
        }
    }

    private static Bounds measureBounds(BakedModel model) {
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int side = -1; side < 6; side++) {
            Direction direction = side < 0 ? null : Direction.values()[side];
            for (var quad : model.getQuads(null, direction, RandomSource.create(42))) {
                int[] vertices = quad.getVertices();
                int stride = vertices.length / 4;
                if (stride < 3) continue;
                for (int index = 0; index < 4; index++) {
                    float x = Float.intBitsToFloat(vertices[index * stride]);
                    float y = Float.intBitsToFloat(vertices[index * stride + 1]);
                    float z = Float.intBitsToFloat(vertices[index * stride + 2]);
                    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) continue;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                }
            }
        }
        if (!Float.isFinite(minX)) return DEFAULT_BOUNDS;
        // Keep effects outside paper-thin generated models, visible from either side.
        float middleZ = (minZ + maxZ) / 2;
        return new Bounds(minX, minY, Math.min(minZ, middleZ - .08F), maxX, maxY, Math.max(maxZ, middleZ + .08F));
    }

    private static void sprite(PoseStack poses, MultiBufferSource buffers, ResourceLocation texture,
                               float x, float y, float z, float radius, UpgradeEffectStyle style, float alpha) {
        plane(poses, buffers, texture, x - radius, y - radius, x + radius, y + radius, z, style, alpha);
        // Crossed local planes keep sparks/runes visible when the weapon turns edge-on to the camera.
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        var pose = poses.last();
        vertex(vertices, pose, x, y - radius, z - radius, 0, 1, style, alpha);
        vertex(vertices, pose, x, y - radius, z + radius, 1, 1, style, alpha);
        vertex(vertices, pose, x, y + radius, z + radius, 1, 0, style, alpha);
        vertex(vertices, pose, x, y + radius, z - radius, 0, 0, style, alpha);
    }

    private static void plane(PoseStack poses, MultiBufferSource buffers, ResourceLocation texture,
                               float x0, float y0, float x1, float y1, float z,
                               UpgradeEffectStyle style, float alpha) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        var pose = poses.last();
        vertex(vertices, pose, x0, y0, z, 0, 1, style, alpha);
        vertex(vertices, pose, x1, y0, z, 1, 1, style, alpha);
        vertex(vertices, pose, x1, y1, z, 1, 0, style, alpha);
        vertex(vertices, pose, x0, y1, z, 0, 0, style, alpha);
    }
    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
                                float u, float v, UpgradeEffectStyle style, float alpha) {
        vertices.addVertex(pose.pose(), x, y, z).setColor(style.red(), style.green(), style.blue(), alpha)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0, 0, 1);
    }
}
