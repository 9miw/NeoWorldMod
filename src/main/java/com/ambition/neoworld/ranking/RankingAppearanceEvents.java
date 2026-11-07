package com.ambition.neoworld.ranking;

import java.time.Instant;
import java.util.Arrays;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid=NeoWorld.MODID)
public final class RankingAppearanceEvents {
    private RankingAppearanceEvents() { }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            captureFirstLogin(player,Instant.now());
            RankingService.invalidateAppearance(player.getServer(),player.getUUID());
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) RankingService.invalidateAppearance(player.getServer(),player.getUUID());
    }
    private static void captureFirstLogin(ServerPlayer player,Instant now) {
        var previous=player.getData(ModAttachments.RANKING_APPEARANCE);
        if(!previous.needsCapture(now)) return;
        var texture=player.getGameProfile().getProperties().get("textures").stream().findFirst().orElse(null);
        player.setData(ModAttachments.RANKING_APPEARANCE,new RankingAppearance(RankingAppearance.day(now),
                texture==null ? "" : texture.value(),texture==null || texture.signature()==null ? "" : texture.signature(),
                Arrays.stream(RankingAppearance.EQUIPMENT).map(player::getItemBySlot).toList(),
                player.getData(ModAttachments.FASHION_DATA).asMap()));
    }
}
