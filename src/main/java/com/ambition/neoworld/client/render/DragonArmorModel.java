package com.ambition.neoworld.client.render;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Renders the Blender triangles on vanilla body parts without losing atlas UVs. */
public final class DragonArmorModel extends HumanoidModel<LivingEntity> {
    private record Face(String bone, float[][] vertices) {}
    private final List<Face> faces = new ArrayList<>();

    public DragonArmorModel(String part) {
        super(root());
        var location = ResourceLocation.fromNamespaceAndPath("neoworld", "models/armor/dragon_" + part + ".json");
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(location)) {
            for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                var face = entry.getAsJsonObject();
                var vertices = face.getAsJsonArray("vertices");
                float[][] points = new float[3][8];
                for (int i=0;i<3;i++) for (int j=0;j<8;j++) points[i][j]=vertices.get(i).getAsJsonArray().get(j).getAsFloat();
                faces.add(new Face(face.get("bone").getAsString(),points));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load dragon armor " + location, e);
        }
    }

    private static ModelPart root() {
        var mesh = new MeshDefinition();
        for (String name : List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"))
            mesh.getRoot().addOrReplaceChild(name, CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh,160,32).bakeRoot();
    }

    @Override
    public void renderToBuffer(PoseStack poses, VertexConsumer buffer, int light, int overlay, int color) {
        for (Face face : faces) {
            ModelPart part;
            float x=0,y=0;
            switch(face.bone()) {
                case "Head" -> part=head;
                case "Arm_R", "Hand_R" -> {part=rightArm;x=-5f/16;y=2f/16;}
                case "Arm_L", "Hand_L" -> {part=leftArm;x=5f/16;y=2f/16;}
                case "Thigh_R", "Leg_R" -> {part=rightLeg;x=-1.9f/16;y=.75f;}
                case "Thigh_L", "Leg_L" -> {part=leftLeg;x=1.9f/16;y=.75f;}
                default -> part=body;
            }
            if (!part.visible) continue;
            poses.pushPose();
            part.translateAndRotate(poses);
            var pose=poses.last();
            // Minecraft's entity buffer consumes quads; repeat triangle's last vertex.
            for (int i : new int[]{0,1,2,2}) {
                float[] v=face.vertices()[i];
                buffer.addVertex(pose,v[0]-x,v[1]-y,v[2]).setColor(color).setUv(v[3],v[4])
                    .setOverlay(overlay).setLight(light).setNormal(pose,v[5],v[6],v[7]);
            }
            poses.popPose();
        }
    }
}
