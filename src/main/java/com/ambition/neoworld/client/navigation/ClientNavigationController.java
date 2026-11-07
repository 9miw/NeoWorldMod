package com.ambition.neoworld.client.navigation;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.navigation.*;
import com.ambition.neoworld.network.navigation.NavigationGraphPayload;
import com.ambition.neoworld.network.navigation.NavigationGraphRequestPayload;
import com.ambition.neoworld.network.navigation.NavigationTargetPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;

/** Client owns route selection and dense geometry; server supplies only graph edits and quest destinations. */
@EventBusSubscriber(modid=NeoWorld.MODID,value=Dist.CLIENT)
public final class ClientNavigationController {
    private static final NavigationClientGraph GRAPH=new NavigationClientGraph();
    private static NavigationTargetPayload target=NavigationTargetPayload.EMPTY;
    private static NavigationTrailView view;
    private static List<NavigationGraph.Node> route=List.of();
    private static ClientLevel previousLevel;
    private static Vec3 previousPosition;
    private static boolean dirty=true;
    private static long nextRoute,nextView,lastRequest=now(),transferStarted;
    private ClientNavigationController() {}
    private static long now(){return System.nanoTime()/1_000_000;}
    public static NavigationTrailView view(){return view;}
    public static int trackedQuestId(){return target.questId();}

    public static void receiveGraph(NavigationGraphPayload page) {
        if(page.page()==0)transferStarted=now();
        try { if(GRAPH.accept(page))dirty=true; }
        catch(IllegalArgumentException exception) {
            NeoWorld.LOGGER.warn("Invalid navigation graph transfer; requesting fresh snapshot",exception);
            GRAPH.clear();view=null;route=List.of();dirty=true;lastRequest=now()-5000;
        }
    }
    public static void receiveTarget(NavigationTargetPayload next) {
        if(next.equals(target))return;
        target=next;view=null;route=List.of();dirty=true;
    }
    public static void reset() {
        GRAPH.clear();target=NavigationTargetPayload.EMPTY;view=null;route=List.of();
        previousLevel=null;previousPosition=null;dirty=true;nextRoute=0;nextView=0;lastRequest=now();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var minecraft=Minecraft.getInstance();var level=minecraft.level;var player=minecraft.player;
        if(level==null||player==null){view=null;return;}
        long clock=now();
        if((!GRAPH.ready()&&!GRAPH.transferring()||GRAPH.transferring()&&clock-transferStarted>15000)&&clock-lastRequest>=5000) {
            lastRequest=clock;PacketDistributor.sendToServer(NavigationGraphRequestPayload.INSTANCE);
        }
        if(previousLevel!=level){previousLevel=level;route=List.of();view=null;dirty=true;nextRoute=0;nextView=0;}
        var goal=target.goal();
        if(goal==null||!GRAPH.ready()||!goal.dimension().equals(level.dimension().location())||!player.isAlive()||player.isSpectator()) {
            view=null;route=List.of();dirty=true;return;
        }
        long tick=level.getGameTime();
        if(!dirty&&tick<nextView)return;
        nextView=tick+5;
        var origin=NavigationWalk.point("@player",goal.dimension().toString(),player.position());
        var destination=NavigationWalk.point("@target",goal.dimension().toString(),goal.position());
        boolean arrived=player.position().distanceToSqr(goal.position())<=goal.radius()*goal.radius()
                &&NavigationWalk.between(level,origin,destination);
        if(arrived) {route=List.of();dirty=false;previousPosition=player.position();nextRoute=tick;}
        else if(dirty||tick>=nextRoute||previousPosition==null||player.position().distanceToSqr(previousPosition)>=4) {
            route=NavigationPathfinder.find(GRAPH.graph(),origin,destination,
                    n->NavigationWalk.between(level,origin,n),
                    n->!NavigationWalk.loaded(level,n,destination)||NavigationWalk.between(level,n,destination),
                    (a,b)->Math.min(a.distance(origin),b.distance(origin))>32||!NavigationWalk.loaded(level,a,b)||NavigationWalk.between(level,a,b));
            dirty=false;nextRoute=tick+20;previousPosition=player.position();
        }
        if(!arrived&&route.isEmpty()) {
            view=null;
            return;
        }
        var localTarget=new NavigationSavedData.Target(goal.dimension().toString(),goal.position().x,goal.position().y,goal.position().z,goal.radius(),null);
        view=NavigationTrailBuilder.build(level,player.position(),route,localTarget);
    }
}
