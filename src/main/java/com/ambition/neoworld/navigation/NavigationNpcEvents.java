package com.ambition.neoworld.navigation;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import noppes.npcs.entity.EntityNPCInterface;

@EventBusSubscriber(modid=NeoWorld.MODID)
public final class NavigationNpcEvents {
    private NavigationNpcEvents() {}
    private static void observe(EntityNPCInterface npc) {
        if(!(npc.level() instanceof ServerLevel level)||level.getServer().overworld()==null||!npc.isAlive()) return;
        NavigationNpcDirectory.get(level.getServer()).observe(npc.getName().getString(),
                new NavigationSavedData.Target(level.dimension().location().toString(),npc.getX(),npc.getY(),npc.getZ(),3,npc.getUUID()));
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if(event.getEntity() instanceof EntityNPCInterface npc) observe(npc);
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if(event.getEntity() instanceof EntityNPCInterface npc && npc.tickCount%40==0) observe(npc);
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if(!(event.getEntity() instanceof EntityNPCInterface npc)||!(event.getLevel() instanceof ServerLevel level)||level.getServer().overworld()==null) return;
        var reason=npc.getRemovalReason();
        if(reason!=null&&reason.shouldDestroy()) NavigationNpcDirectory.get(level.getServer()).forget(npc.getUUID());
        else observe(npc);
    }
}
