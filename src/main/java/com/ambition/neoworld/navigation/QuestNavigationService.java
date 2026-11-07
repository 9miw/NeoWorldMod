package com.ambition.neoworld.navigation;

import com.ambition.neoworld.network.navigation.NavigationTargetPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Server-owned sessions; only the session's player receives its trail packets. */
public final class QuestNavigationService {
    private static final Map<MinecraftServer,Map<UUID,Session>> SESSIONS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,QuestAutoTrackingQueue> ACCEPTED=new IdentityHashMap<>();
    private static final DustParticleOptions DEBUG=new DustParticleOptions(new Vector3f(1f,.65f,.1f),.7f);
    private QuestNavigationService() {}
    private static final class Session {
        final int quest;
        long nextNotice;
        boolean missingObjectiveNotified;
        QuestNavigationState.Phase phase=QuestNavigationState.Phase.NONE;
        String completerNpc="";
        NavigationTargetPayload lastSent=NavigationTargetPayload.EMPTY;
        Session(int quest) { this.quest=quest; }
    }
    public static void track(ServerPlayer player,int quest) {
        var binding=NavigationSavedData.get(player.getServer()).binding(quest);
        var snapshot=CustomNpcQuestBridge.snapshot(player,quest);
        if(snapshot.phase()==QuestNavigationState.Phase.NONE)
            throw new IllegalArgumentException("คุณยังไม่ได้รับเควสต์นี้ หรือส่งเควสต์แล้ว");
        if(snapshot.phase()==QuestNavigationState.Phase.OBJECTIVE && (binding==null||binding.objective()==null)
                && CustomNpcQuestBridge.pendingLocations(player,quest).isEmpty())
            throw new IllegalArgumentException("เควสต์นี้ยังไม่ได้ตั้งจุดทำภารกิจ");
        stop(player);
        SESSIONS.computeIfAbsent(player.getServer(),s->new HashMap<>()).put(player.getUUID(),new Session(quest));
    }
    public static void stop(ServerPlayer player) {
        var accepted=ACCEPTED.get(player.getServer());
        if(accepted!=null) accepted.cancel(player.getUUID());
        var sessions=SESSIONS.get(player.getServer());
        if(sessions!=null) {
            var session=sessions.remove(player.getUUID());
            if(session!=null) publish(player,session,NavigationTargetPayload.EMPTY);
        }
    }
    public static void clear(MinecraftServer server) { SESSIONS.remove(server); ACCEPTED.remove(server); }
    public static void questAccepted(ServerPlayer player,int quest) {
        ACCEPTED.computeIfAbsent(player.getServer(),server->new QuestAutoTrackingQueue()).accepted(player.getUUID(),quest);
    }
    private static void trackAcceptedQuests(MinecraftServer server) {
        var pending=ACCEPTED.get(server);
        if(pending==null) return;
        for(var acceptance:pending.drain().entrySet()) {
            var player=server.getPlayerList().getPlayer(acceptance.getKey());
            if(player==null) continue;
            int quest=acceptance.getValue();
            if(CustomNpcQuestBridge.phase(player,quest)==QuestNavigationState.Phase.NONE) continue;
            // Newest acceptance owns the session even if its objective has not been configured yet.
            // The existing tick handler reports the missing destination and waits for configuration.
            stop(player);
            SESSIONS.computeIfAbsent(server,ignored->new HashMap<>()).put(player.getUUID(),new Session(quest));
            player.sendSystemMessage(Component.literal("ติดตามเควสต์ที่รับล่าสุดอัตโนมัติ: ID "+quest));
        }
    }
    public static void invalidate(ServerPlayer player) {
        var sessions=SESSIONS.get(player.getServer()); if(sessions==null) return;
        var session=sessions.get(player.getUUID());
        if(session!=null) hide(player,session);
    }
    public static void tick(MinecraftServer server) {
        trackAcceptedQuests(server);
        var sessions=SESSIONS.get(server); if(sessions==null||server.getTickCount()%10!=0) return;
        var data=NavigationSavedData.get(server);
        for(var entry:new ArrayList<>(sessions.entrySet())) {
            var player=server.getPlayerList().getPlayer(entry.getKey()); var session=entry.getValue();
            if(player==null) { sessions.remove(entry.getKey()); continue; }
            if(!player.isAlive()||player.isSpectator()) { hide(player,session); continue; }
            long now=server.getTickCount();
            if(now%20==0 || session.phase==QuestNavigationState.Phase.NONE) {
                var snapshot=CustomNpcQuestBridge.snapshot(player,session.quest);
                var phase=snapshot.phase();
                session.completerNpc=snapshot.completerNpc();
                if(phase==QuestNavigationState.Phase.NONE) {
                    publish(player,session,NavigationTargetPayload.EMPTY); sessions.remove(entry.getKey()); player.sendSystemMessage(Component.literal("หยุดนำทาง: เควสต์สิ้นสุดหรือถูกยกเลิก")); continue;
                }
                session.phase=phase;
            }
            var binding=data.binding(session.quest);
            var target=QuestNavigationState.destination(session.phase,binding,
                    () -> automaticLocation(player,session.quest),()->{
                var selected=NavigationNpcDirectory.get(server).select(session.completerNpc,
                        player.level().dimension().location().toString(),player.getX(),player.getY(),player.getZ());
                if(selected==null) {
                    notice(player,session,now,"ยังไม่มีตำแหน่ง NPC ที่รู้จักในมิตินี้ กรุณาให้พื้นที่ NPC โหลดก่อน"); return null;
                }
                if(selected.fallback()) notice(player,session,now,"ไม่พบ NPC ชื่อ "+session.completerNpc+" ใช้ "+selected.name()+" ที่ใกล้ที่สุดเป็นจุดสำรอง ซึ่งอาจรับส่งเควสต์นี้ไม่ได้");
                return selected.target();
            });
            if(target==null) {
                hide(player,session);
                if(session.phase==QuestNavigationState.Phase.OBJECTIVE && !session.missingObjectiveNotified) {
                    player.sendSystemMessage(Component.literal("เควสต์นี้ยังไม่ได้ตั้งจุดทำภารกิจ"));
                    session.missingObjectiveNotified=true;
                }
                continue;
            }
            var goal=new NavigationTargetPayload.Goal(ResourceLocation.parse(target.dimension()),
                    new Vec3(target.x(),target.y(),target.z()),target.radius());
            publish(player,session,new NavigationTargetPayload(session.quest,
                    session.phase==QuestNavigationState.Phase.TURN_IN,goal));
            if(!player.level().dimension().location().toString().equals(target.dimension()))
                notice(player,session,now,"เป้าหมายอยู่ในมิติ "+target.dimension());
        }
    }
    private static NavigationSavedData.Target automaticLocation(ServerPlayer player,int quest) {
        var pending=CustomNpcQuestBridge.pendingLocations(player,quest);
        if(pending.isEmpty()) return null;
        var directory=NavigationLocationDirectory.get(player.getServer());
        directory.refresh(player.getServer(),pending);
        return directory.select(pending,player.level().dimension().location().toString(),
                player.getX(),player.getY(),player.getZ());
    }
    private static void notice(ServerPlayer player,Session session,long now,String text) {
        if(now>=session.nextNotice) { player.sendSystemMessage(Component.literal(text)); session.nextNotice=now+200; }
    }
    private static void hide(ServerPlayer player,Session session) {
        publish(player,session,new NavigationTargetPayload(session.quest,
                session.phase==QuestNavigationState.Phase.TURN_IN,null));
    }
    private static void publish(ServerPlayer player,Session session,NavigationTargetPayload payload) {
        if(!payload.equals(session.lastSent)) {
            PacketDistributor.sendToPlayer(player,payload);
            session.lastSent=payload;
        }
    }
    private static void draw(ServerPlayer player,List<NavigationGraph.Node> route,DustParticleOptions particle,double range) {
        var level=player.serverLevel(); List<NavigationGraph.Node> nearby=new ArrayList<>();
        for(var node:route) {
            if(nearby.size()>1 && node.distanceSquared(player.getX(),player.getY(),player.getZ())>range*range) { nearby.add(node); break; }
            nearby.add(node);
        }
        // Validate full walking corridors before displaying a segment, including newly placed obstacles.
        for(int i=0;i+1<nearby.size();i++) {
            if(!NavigationWalk.between(level,nearby.get(i),nearby.get(i+1))) { nearby=new ArrayList<>(nearby.subList(0,i+1)); break; }
        }
        var samples=NavigationCurve.sample(nearby,.25,p->NavigationWalk.surface(level,p.x(),p.y(),p.z())!=null,(a,b)->{
            Vec3 from=NavigationWalk.surface(level,a.x(),a.y(),a.z()),to=NavigationWalk.surface(level,b.x(),b.y(),b.z());
            return from!=null&&to!=null&&Math.abs(from.y-to.y)<=.61;
        });
        int sent=0; Vec3 last=null;
        for(var p:samples) {
            Vec3 surface=NavigationWalk.surface(level,p.x(),p.y(),p.z());
            if(surface==null || surface.distanceToSqr(player.position())>range*range || (last!=null && last.distanceToSqr(surface)<.64)) continue;
            level.sendParticles(player,particle,false,surface.x,surface.y+.2,surface.z,1,0,0,0,0);
            last=surface; if(++sent>=32) break;
        }
    }
    public static void preview(ServerPlayer player) {
        var graph=NavigationSavedData.get(player.getServer()).graph(); String dim=player.level().dimension().location().toString();
        int count=0;
        for(var edge:graph.edges()) {
            var a=graph.node(edge.a()); var b=graph.node(edge.b());
            if(!a.dimension().equals(dim) || Math.min(a.distanceSquared(player.getX(),player.getY(),player.getZ()),b.distanceSquared(player.getX(),player.getY(),player.getZ()))>32*32) continue;
            draw(player,List.of(a,b),DEBUG,32); if(++count>=32) break;
        }
    }
}
