package com.ambition.neoworld.client.navigation;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.navigation.NavigationRibbonGeometry;
import com.ambition.neoworld.navigation.NavigationTrailView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/** Unlit, depth-tested white/cyan ribbon. Rendering never changes the quest or world. */
@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class NavigationTrailRenderer {
    private static final double[] PATH_WIDTH = {.22, .085, .027};
    private static final double[] RING_WIDTH = {.16, .065, .022};
    private static NavigationTrailView cached;
    private static List<List<NavigationRibbonGeometry.Edge>> pathLayers = List.of();
    private static List<List<NavigationRibbonGeometry.Edge>> ringLayers = List.of();

    private NavigationTrailRenderer() {}

    // Material creation stays lazy and on the client render thread.
    private static final class Material {
        static final RenderType TYPE = RenderType.create("neoworld_navigation_ribbon", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 65536, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    private static void clear() {
        cached = null; pathLayers = List.of(); ringLayers = List.of();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { ClientNavigationController.reset(); clear(); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive() || minecraft.player.isSpectator()) {
            clear(); return;
        }
        NavigationTrailView payload = ClientNavigationController.view();
        if (payload == null || !payload.dimension().equals(minecraft.level.dimension().location())) { cached = null; pathLayers = List.of(); ringLayers = List.of(); return; }
        if (!payload.equals(cached)) {
            cached = payload;
            pathLayers = layers(payload.path(), PATH_WIDTH, false);
            ringLayers = layers(payload.ring(), RING_WIDTH, true);
        }
        double seconds = (minecraft.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20.0;
        var buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer vertices = buffers.getBuffer(Material.TYPE);
        Matrix4f matrix = event.getPoseStack().last().pose();
        Vec3 camera = event.getCamera().getPosition();
        for (int layer = 0; layer < 3; layer++) {
            draw(vertices, matrix, camera, minecraft.player.position(), pathLayers.get(layer), layer, seconds, false);
            draw(vertices, matrix, camera, minecraft.player.position(), ringLayers.get(layer), layer, seconds, true);
        }
        // Flush our material only; do not disturb unrelated world-render buffers.
        buffers.endBatch(Material.TYPE);
    }

    private static List<List<NavigationRibbonGeometry.Edge>> layers(List<Vec3> points, double[] widths, boolean closed) {
        List<List<NavigationRibbonGeometry.Edge>> result = new ArrayList<>(3);
        List<Vec3> surfaces = NavigationRibbonGeometry.steps(points);
        for (double width : widths) result.add(NavigationRibbonGeometry.edges(surfaces, width, closed));
        return List.copyOf(result);
    }

    private static void draw(VertexConsumer vertices, Matrix4f matrix, Vec3 camera, Vec3 player,
            List<NavigationRibbonGeometry.Edge> edges, int layer, double seconds, boolean ring) {
        for (int i = 0; i + 1 < edges.size(); i++) {
            var a = edges.get(i); var b = edges.get(i + 1);
            if (b.distance() - a.distance() < .00001) continue;
            int alphaA = alpha(a, player, layer, seconds, ring);
            int alphaB = alpha(b, player, layer, seconds, ring);
            int red = layer == 2 ? 245 : 40;
            int green = layer == 2 ? 255 : 220;
            vertex(vertices, matrix, camera, a.left(), layer, red, green, alphaA);
            vertex(vertices, matrix, camera, a.right(), layer, red, green, alphaA);
            vertex(vertices, matrix, camera, b.right(), layer, red, green, alphaB);
            vertex(vertices, matrix, camera, b.left(), layer, red, green, alphaB);
        }
    }

    private static int alpha(NavigationRibbonGeometry.Edge edge, Vec3 player, int layer, double seconds, boolean ring) {
        Vec3 center = edge.left().add(edge.right()).scale(.5);
        double fade = Math.clamp((24 - center.distanceTo(player)) / 7, 0, 1);
        double pulse = ring ? .5 + .5 * Math.sin(seconds * Math.PI * 1.3) : NavigationRibbonGeometry.pulse(edge.distance(), seconds);
        double brightness = switch (layer) {
            case 0 -> 22 + 38 * pulse;
            case 1 -> 90 + 90 * pulse;
            default -> 185 + 70 * pulse;
        };
        return (int) Math.round(brightness * fade);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3 camera, Vec3 point,
            int layer, int red, int green, int alpha) {
        // Subtract doubles before conversion to float so distant worlds retain local precision.
        vertices.addVertex(matrix, (float) (point.x - camera.x), (float) (point.y - camera.y + layer * .002),
                (float) (point.z - camera.z)).setColor(red, green, 255, alpha);
    }
}
