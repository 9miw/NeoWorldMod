package com.ambition.neoworld.navigation;

import com.ambition.neoworld.network.navigation.NavigationGraphPayload;
import com.ambition.neoworld.network.navigation.NavigationTargetPayload;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class NavigationClientRoutingRegressionTest {
    public static void run() {
        var graph=new NavigationGraph();
        var a=new NavigationGraph.Node("a","minecraft:overworld",0,64,0);
        var b=new NavigationGraph.Node("b","minecraft:overworld",8,64,0);
        graph.add(a); graph.add(b); graph.connect("a","b");
        var first=NavigationGraphSnapshot.capture(graph);
        var cache=new NavigationClientGraph();
        for(var page:NavigationGraphSnapshot.pages(null,first)) roundTrip(page,cache);
        check(cache.graph().route("a","b").equals(List.of(a,b)),"Initial graph reconstructs authored links on client");
        graph.disconnect("a","b");
        var changed=NavigationGraphSnapshot.capture(graph);
        var updates=NavigationGraphSnapshot.pages(first,changed);
        check(updates.getFirst().nodes().isEmpty()&&updates.getFirst().removedEdges().size()==1,"Unlink sends a removed edge, not a full graph");
        for(var page:updates) roundTrip(page,cache);
        check(cache.graph().route("a","b").isEmpty(),"Client route immediately respects removed links");
        check(NavigationGraphSnapshot.pages(changed,changed).isEmpty(),"Unchanged graph produces no network traffic");
        var partial=new NavigationClientGraph();
        check(!partial.accept(new NavigationGraphPayload(-1,100,true,0,2,List.of(a),List.of(),List.of(),List.of())),"Partial initial graph is not published");
        check(!partial.ready(),"Route computation cannot use a half-received graph");
        partial.accept(new NavigationGraphPayload(-1,100,true,1,2,List.of(b),List.of(),List.of(new NavigationGraph.Edge("a","b")),List.of()));
        check(partial.ready()&&partial.graph().route("a","b").size()==2,"Paged graph commits atomically");
        reject(()->partial.accept(new NavigationGraphPayload(99,101,false,0,1,List.of(),List.of(),List.of(),List.of())));
        var goal=new NavigationTargetPayload(42,false,new NavigationTargetPayload.Goal(ResourceLocation.parse("minecraft:overworld"),new Vec3(8,64,0),3));
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try { NavigationTargetPayload.STREAM_CODEC.encode(buffer,goal); check(NavigationTargetPayload.STREAM_CODEC.decode(buffer).equals(goal),"Target codec preserves quest, phase and destination"); }
        finally { buffer.release(); }
        check(NavigationTargetPayload.EMPTY.goal()==null,"Explicit stop removes the target without a timeout");
        var waiting=new NavigationTargetPayload(42,false,null);
        var waitingBuffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try {
            NavigationTargetPayload.STREAM_CODEC.encode(waitingBuffer,waiting);
            check(NavigationTargetPayload.STREAM_CODEC.decode(waitingBuffer).equals(waiting),
                    "Tracking a quest without a destination retains its ID for the untrack button");
        } finally { waitingBuffer.release(); }
        var routeGraph=new NavigationGraph();
        var near=new NavigationGraph.Node("near","minecraft:overworld",1,64,0);
        var far=new NavigationGraph.Node("far","minecraft:overworld",0,64,5);
        var detour=new NavigationGraph.Node("detour","minecraft:overworld",8,64,0);
        var end=new NavigationGraph.Node("end","minecraft:overworld",0,64,10);
        for(var n:List.of(near,far,detour,end))routeGraph.add(n);
        routeGraph.connect("near","detour");routeGraph.connect("detour","end");routeGraph.connect("far","end");
        var route=NavigationPathfinder.find(routeGraph,new NavigationGraph.Node("@player","minecraft:overworld",0,64,0),
                new NavigationGraph.Node("@target","minecraft:overworld",0,64,10),n->true,n->true,(x,y)->true);
        check(route.get(1).equals(far),"Moving computation to client preserves original best-total-cost policy, not the reverted nearest-entry policy");
        var cornerA=new NavigationGraph.Node("cornerA","minecraft:overworld",15,64,15);
        var cornerB=new NavigationGraph.Node("cornerB","minecraft:overworld",17,64,17);
        check(!NavigationWalk.loaded(cornerA,cornerB,(x,z)->!(x==1&&z==0)),"Walkability must check real corridor chunks, including marker-free corners");
        check(NavigationWalk.loaded(cornerA,cornerB,(x,z)->true),"Fully cached corridor is eligible for local collision checks");
        graph.remove("b");
        var removed=NavigationGraphSnapshot.capture(graph);
        for(var page:NavigationGraphSnapshot.pages(changed,removed))roundTrip(page,cache);
        check(cache.graph().node("b")==null,"Delta deletion removes a marker from client cache");
        cache.clear();check(!cache.ready()&&cache.graph().nodes().isEmpty(),"Disconnect/reset clears graph and revision");
        var clearBuffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try {NavigationTargetPayload.STREAM_CODEC.encode(clearBuffer,NavigationTargetPayload.EMPTY);check(NavigationTargetPayload.STREAM_CODEC.decode(clearBuffer).equals(NavigationTargetPayload.EMPTY),"Stop codec explicitly clears the target");}
        finally {clearBuffer.release();}
        var invalid=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try {
            invalid.writeLong(-1);invalid.writeLong(0);invalid.writeBoolean(true);invalid.writeVarInt(0);invalid.writeVarInt(1);invalid.writeVarInt(129);
            reject(()->NavigationGraphPayload.STREAM_CODEC.decode(invalid));
        } finally {invalid.release();}
        reject(()->new NavigationTargetPayload.Goal(ResourceLocation.parse("minecraft:overworld"),new Vec3(Double.NaN,64,0),3));
        reject(()->new NavigationTargetPayload.Goal(ResourceLocation.parse("minecraft:overworld"),Vec3.ZERO,0));
        System.out.println("Client navigation graph/delta/target regression checks passed");
    }
    private static void roundTrip(NavigationGraphPayload page,NavigationClientGraph cache) {
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try { NavigationGraphPayload.STREAM_CODEC.encode(buffer,page); cache.accept(NavigationGraphPayload.STREAM_CODEC.decode(buffer)); }
        finally { buffer.release(); }
    }
    private static void check(boolean value,String text) { if(!value) throw new AssertionError(text); }
    private static void reject(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid graph revision accepted");
    }
}
