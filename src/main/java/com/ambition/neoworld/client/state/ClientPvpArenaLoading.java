package com.ambition.neoworld.client.state;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.pvp.PvpArenaLoadPayload;
import com.ambition.neoworld.network.pvp.PvpArenaReadyPayload;
import com.ambition.neoworld.pvp.PvpDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Waits for received spawn chunks and a locally compiled/rendered spawn view, not the entire map. */
@EventBusSubscriber(modid=NeoWorld.MODID,value=Dist.CLIENT)
public final class ClientPvpArenaLoading {
    private static PvpArenaLoadPayload pending;
    private static int age,stable;
    private static boolean rendered;
    private ClientPvpArenaLoading() {}
    public static void receive(PvpArenaLoadPayload payload) {
        if(!payload.active()) {
            if(pending!=null && pending.token().equals(payload.token())) reset();
            return;
        }
        pending=payload; age=0; stable=0; rendered=false;
    }
    private static boolean ready(Minecraft mc) {
        if(pending==null || mc.player==null || mc.level==null || mc.screen instanceof ReceivingLevelScreen
                || !mc.level.dimension().equals(PvpDimensions.LEVEL)) return false;
        var spawn=pending.spawn();
        if(mc.player.distanceToSqr(spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5)>1) return false;
        int cx=spawn.getX()>>4,cz=spawn.getZ()>>4;
        // ClientLevel.hasChunk always returns true; query the actual received chunk cache.
        for(int x=cx-1;x<=cx+1;x++) for(int z=cz-1;z<=cz+1;z++) if(!mc.level.getChunkSource().hasChunk(x,z)) return false;
        return mc.levelRenderer.isSectionCompiled(spawn.below()) && mc.levelRenderer.isSectionCompiled(spawn.above());
    }
    @SubscribeEvent public static void rendered(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL && ready(Minecraft.getInstance())) rendered=true;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(pending==null) return;
        if(++age>1300) { reset(); return; }
        if(!ready(Minecraft.getInstance())) { stable=0; rendered=false; return; }
        if(++stable<5 || !rendered) return;
        var token=pending.token(); reset();
        PacketDistributor.sendToServer(new PvpArenaReadyPayload(token));
    }
    public static void reset() { pending=null; age=0; stable=0; rendered=false; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
}
