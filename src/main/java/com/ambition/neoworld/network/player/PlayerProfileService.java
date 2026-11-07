package com.ambition.neoworld.network.player;

import com.ambition.neoworld.network.PlayerProfilePayload;
import com.ambition.neoworld.pvp.ranked.PvpRankingCache;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Gameplay snapshot on server thread; database work stays in the existing asynchronous ranking cache. */
public final class PlayerProfileService {
    private PlayerProfileService() {}
    public static void send(PlayerActionPayload request,ServerPlayer viewer,ServerPlayer subject) {
        var server=viewer.getServer();
        var snapshot=PlayerProfileSnapshot.capture(request,subject).forViewer(viewer==subject);
        PacketDistributor.sendToPlayer(viewer,snapshot.withDetails(snapshot.details().pvp(ProfileDetails.Pvp.LOADING)));
        PvpRankingCache.player(subject.getUUID(),subject.getGameProfile().getName()).whenComplete((view,error) -> server.execute(() -> {
            if(server.getPlayerList().getPlayer(viewer.getUUID())!=viewer) return;
            if(server.getPlayerList().getPlayer(subject.getUUID())!=subject || !viewer.isAlive() || !subject.isAlive()
                    || viewer.isSpectator() || subject.isSpectator() || viewer.serverLevel()!=subject.serverLevel()
                    || (viewer!=subject && viewer.distanceToSqr(subject)>25)) {
                PacketDistributor.sendToPlayer(viewer,snapshot.withDetails(snapshot.details().pvp(ProfileDetails.Pvp.UNAVAILABLE)));return;
            }
            var pvp=error==null && view!=null ? new ProfileDetails.Pvp(true,view.player().rating(),view.position(),view.player().wins(),view.player().losses())
                    : ProfileDetails.Pvp.UNAVAILABLE;
            PacketDistributor.sendToPlayer(viewer,snapshot.withDetails(snapshot.details().pvp(pvp)));
        }));
    }
}
