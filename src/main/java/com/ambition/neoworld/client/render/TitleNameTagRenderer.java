package com.ambition.neoworld.client.render;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.ClientTitleData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class TitleNameTagRenderer {
    private TitleNameTagRenderer() {}
    @SubscribeEvent public static void render(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.canRender() == TriState.FALSE) return;
        var minecraft = Minecraft.getInstance();
        var viewer = minecraft.player;
        if (viewer == null || !Minecraft.renderNames() || player == minecraft.getCameraEntity()
                || player.isInvisibleTo(viewer) || player.isDiscrete() || player.isVehicle()
                || minecraft.getEntityRenderDispatcher().distanceToSqr(player) > 4096) return;
        var team = player.getTeam();
        if (team != null) {
            var viewerTeam = viewer.getTeam();
            boolean visible = switch (team.getNameTagVisibility()) {
                case ALWAYS -> true;
                case NEVER -> false;
                case HIDE_FOR_OTHER_TEAMS -> viewerTeam == null || team.isAlliedTo(viewerTeam);
                case HIDE_FOR_OWN_TEAM -> viewerTeam == null || !team.isAlliedTo(viewerTeam);
            };
            if (!visible) return;
        }
        var title = ClientTitleData.title(player.getUUID()).orElse(null);
        if (title == null) return;
        var text = Component.literal(title.name());
        int color = 0xFF000000 | title.color();
        var pose = event.getPoseStack();
        pose.pushPose();
        try {
            var attachment = player.getAttachments().getNullable(net.minecraft.world.entity.EntityAttachment.NAME_TAG,
                    0, player.getViewYRot(event.getPartialTick()));
            if (attachment == null) return;
            pose.translate(attachment.x, attachment.y + 0.85, attachment.z);
            pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(0.025F, -0.025F, 0.025F);
            float x = -minecraft.font.width(text) / 2.0F;
            int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255) << 24;
            minecraft.font.drawInBatch(text, x, 0, color, false, pose.last().pose(),
                    event.getMultiBufferSource(), Font.DisplayMode.NORMAL, background, event.getPackedLight());
        } finally { pose.popPose(); }
    }
}
