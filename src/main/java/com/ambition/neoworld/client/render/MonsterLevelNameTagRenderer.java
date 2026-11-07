package com.ambition.neoworld.client.render;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.experience.MonsterExperienceConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/** Adds a client-only prefix without renaming or making mobs persistent. */
@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class MonsterLevelNameTagRenderer {
    private MonsterLevelNameTagRenderer() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || event.canRender() == TriState.FALSE) return;
        var minecraft = Minecraft.getInstance();
        var viewer = minecraft.player;
        if (viewer == null || !Minecraft.renderNames() || !mob.isAlive() || mob.isInvisibleTo(viewer)
                || mob == minecraft.getCameraEntity()
                || minecraft.getEntityRenderDispatcher().distanceToSqr(mob) > 32 * 32) return;
        var team = mob.getTeam();
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
        int level = MonsterExperienceConfig.level(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(),
                mob instanceof Slime slime ? slime.getSize() : 1);
        if (level <= 0) return;
        float partialTick = event.getPartialTick();
        var attachment = mob.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, mob.getViewYRot(partialTick));
        if (attachment == null) return;
        // Match the height of the vanilla name tag above its attachment.
        var labelPosition = mob.getPosition(partialTick).add(attachment).add(0, 0.5, 0);
        var cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
        var sightLine = new ClipContext(cameraPosition, labelPosition, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, viewer);
        if (mob.level().clip(sightLine).getType() != HitResult.Type.MISS) {
            event.setCanRender(TriState.FALSE);
            return;
        }
        event.setContent(Component.empty()
                .append(Component.literal("[Lv. " + level + "] ").withStyle(ChatFormatting.GOLD))
                .append(event.getContent()));
        event.setCanRender(TriState.TRUE);
    }
}
