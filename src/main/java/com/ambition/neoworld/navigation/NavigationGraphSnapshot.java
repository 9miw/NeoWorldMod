package com.ambition.neoworld.navigation;

import com.ambition.neoworld.network.navigation.NavigationGraphPayload;
import java.util.*;

public record NavigationGraphSnapshot(long revision,Map<String,NavigationGraph.Node> nodes,Set<NavigationGraph.Edge> edges) {
    public NavigationGraphSnapshot { nodes=Map.copyOf(nodes);edges=Set.copyOf(edges); }
    public static NavigationGraphSnapshot capture(NavigationGraph graph) {
        Map<String,NavigationGraph.Node> nodes=new HashMap<>();for(var n:graph.nodes())nodes.put(n.id(),n);
        return new NavigationGraphSnapshot(graph.revision(),nodes,graph.edges());
    }
    public static List<NavigationGraphPayload> pages(NavigationGraphSnapshot before,NavigationGraphSnapshot after) {
        if(before!=null&&before.revision()==after.revision())return List.of();
        var nodes=after.nodes.values().stream().filter(n->before==null||!n.equals(before.nodes.get(n.id()))).sorted(Comparator.comparing(NavigationGraph.Node::id)).toList();
        var removed=before==null?List.<String>of():before.nodes.keySet().stream().filter(id->!after.nodes.containsKey(id)).sorted().toList();
        Comparator<NavigationGraph.Edge> order=Comparator.comparing(NavigationGraph.Edge::a).thenComparing(NavigationGraph.Edge::b);
        var edges=after.edges.stream().filter(e->before==null||!before.edges.contains(e)).sorted(order).toList();
        var removedEdges=before==null?List.<NavigationGraph.Edge>of():before.edges.stream().filter(e->!after.edges.contains(e)).sorted(order).toList();
        int size=NavigationGraphPayload.PAGE_SIZE;
        int count=Math.max(1,(Math.max(Math.max(nodes.size(),removed.size()),Math.max(edges.size(),removedEdges.size()))+size-1)/size);
        List<NavigationGraphPayload> pages=new ArrayList<>(count);
        for(int i=0;i<count;i++)pages.add(new NavigationGraphPayload(before==null?-1:before.revision(),after.revision(),before==null,i,count,
                slice(nodes,i,size),slice(removed,i,size),slice(edges,i,size),slice(removedEdges,i,size)));
        return List.copyOf(pages);
    }
    private static <T> List<T> slice(List<T> values,int page,int size){int start=Math.min(page*size,values.size());return values.subList(start,Math.min(start+size,values.size()));}
}
