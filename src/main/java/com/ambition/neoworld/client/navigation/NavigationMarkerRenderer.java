package com.ambition.neoworld.client.navigation;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.navigation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid=NeoWorld.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NavigationMarkerRenderer implements BlockEntityRenderer<NavigationMarkerBlock.MarkerEntity> {
    public NavigationMarkerRenderer(BlockEntityRendererProvider.Context context) {}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(NavigationRegistry.MARKER_ENTITY.get(),NavigationMarkerRenderer::new);
    }
    @Override public void render(NavigationMarkerBlock.MarkerEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        var mc=Minecraft.getInstance();
        if(mc.player==null||!mc.player.isCreative()) return;
        pose.pushPose(); pose.translate(.1875,0,.1875); pose.scale(.625f,.625f,.625f);
        mc.getBlockRenderer().renderSingleBlock(Blocks.SEA_LANTERN.defaultBlockState(),pose,buffers,light,overlay);
        pose.popPose();
    }
}
