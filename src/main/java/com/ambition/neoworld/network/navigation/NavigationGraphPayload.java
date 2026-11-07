package com.ambition.neoworld.network.navigation;

import com.ambition.neoworld.navigation.NavigationGraph;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Ordered pages form one atomic graph reset or delta. Variable-length fields are bounded. */
public record NavigationGraphPayload(long baseRevision,long revision,boolean reset,int page,int pages,
        List<NavigationGraph.Node> nodes,List<String> removedNodes,List<NavigationGraph.Edge> edges,List<NavigationGraph.Edge> removedEdges)
        implements CustomPacketPayload {
    public static final int PAGE_SIZE=128;
    public static final Type<NavigationGraphPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","navigation_graph"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NavigationGraphPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{
                b.writeLong(p.baseRevision);b.writeLong(p.revision);b.writeBoolean(p.reset);b.writeVarInt(p.page);b.writeVarInt(p.pages);
                b.writeVarInt(p.nodes.size());
                for(var n:p.nodes) { b.writeUtf(n.id(),160);b.writeUtf(n.dimension(),256);b.writeDouble(n.x());b.writeDouble(n.y());b.writeDouble(n.z()); }
                b.writeVarInt(p.removedNodes.size());for(var id:p.removedNodes)b.writeUtf(id,160);
                writeEdges(b,p.edges);writeEdges(b,p.removedEdges);
            },b->{
                long base=b.readLong(),revision=b.readLong();boolean reset=b.readBoolean();int page=b.readVarInt(),pages=b.readVarInt();
                List<NavigationGraph.Node> nodes=new ArrayList<>();int count=count(b);
                for(int i=0;i<count;i++)nodes.add(new NavigationGraph.Node(b.readUtf(160),b.readUtf(256),b.readDouble(),b.readDouble(),b.readDouble()));
                List<String> removed=new ArrayList<>();count=count(b);for(int i=0;i<count;i++)removed.add(b.readUtf(160));
                return new NavigationGraphPayload(base,revision,reset,page,pages,nodes,removed,readEdges(b),readEdges(b));
            });
    public NavigationGraphPayload {
        if(revision<0||pages<1||pages>262144||page<0||page>=pages||(!reset&&(baseRevision<0||revision<=baseRevision))
                ||nodes.size()>PAGE_SIZE||removedNodes.size()>PAGE_SIZE||edges.size()>PAGE_SIZE||removedEdges.size()>PAGE_SIZE)
            throw new IllegalArgumentException("Invalid navigation graph page");
        nodes=List.copyOf(nodes);removedNodes=List.copyOf(removedNodes);edges=List.copyOf(edges);removedEdges=List.copyOf(removedEdges);
        for(var n:nodes) if(n.id().length()>160||n.dimension().length()>256||ResourceLocation.tryParse(n.dimension())==null
                ||Math.abs(n.x())>30_000_000||Math.abs(n.y())>30_000_000||Math.abs(n.z())>30_000_000)
            throw new IllegalArgumentException("Invalid navigation graph node");
        for(var id:removedNodes)validateId(id);
        for(var e:edges){validateId(e.a());validateId(e.b());}
        for(var e:removedEdges){validateId(e.a());validateId(e.b());}
    }
    private static void validateId(String id) { if(id.isBlank()||id.length()>160)throw new IllegalArgumentException("Invalid marker ID"); }
    private static int count(RegistryFriendlyByteBuf b) { int count=b.readVarInt();if(count<0||count>PAGE_SIZE)throw new IllegalArgumentException("Invalid graph page count");return count; }
    private static void writeEdges(RegistryFriendlyByteBuf b,List<NavigationGraph.Edge> edges) { b.writeVarInt(edges.size());for(var e:edges){b.writeUtf(e.a(),160);b.writeUtf(e.b(),160);} }
    private static List<NavigationGraph.Edge> readEdges(RegistryFriendlyByteBuf b) { List<NavigationGraph.Edge> edges=new ArrayList<>();int count=count(b);for(int i=0;i<count;i++)edges.add(new NavigationGraph.Edge(b.readUtf(160),b.readUtf(160)));return edges; }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
