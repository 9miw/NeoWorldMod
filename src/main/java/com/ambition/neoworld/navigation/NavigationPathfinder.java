package com.ambition.neoworld.navigation;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Original best-total-cost policy, now usable by the client without server classes. */
public final class NavigationPathfinder {
    private NavigationPathfinder() {}
    public static List<NavigationGraph.Node> find(NavigationGraph graph,NavigationGraph.Node origin,NavigationGraph.Node destination,
            Predicate<NavigationGraph.Node> entryReady,Predicate<NavigationGraph.Node> exitReady,
            BiPredicate<NavigationGraph.Node,NavigationGraph.Node> edgeReady) {
        var starts=graph.nearby(origin.dimension(),origin.x(),origin.y(),origin.z(),16).stream()
                .sorted(Comparator.comparingDouble((NavigationGraph.Node n)->n.distance(origin)).thenComparing(NavigationGraph.Node::id)).limit(8).toList();
        var ends=graph.nearby(destination.dimension(),destination.x(),destination.y(),destination.z(),16).stream()
                .sorted(Comparator.comparingDouble((NavigationGraph.Node n)->n.distance(destination)).thenComparing(NavigationGraph.Node::id)).limit(4).toList();
        double best=Double.POSITIVE_INFINITY;List<NavigationGraph.Node> result=List.of();
        Map<NavigationGraph.Edge,Boolean> accessible=new HashMap<>();
        for(var start:starts) {
            if(!entryReady.test(start))continue;
            for(var end:ends) {
                if(!exitReady.test(end))continue;
                var route=graph.route(start.id(),end.id(),(a,b)->accessible.computeIfAbsent(new NavigationGraph.Edge(a.id(),b.id()),e->edgeReady.test(a,b)));
                if(route.isEmpty())continue;
                double cost=origin.distance(start)+end.distance(destination);
                for(int i=0;i+1<route.size();i++)cost+=route.get(i).distance(route.get(i+1));
                if(cost<best){best=cost;var full=new ArrayList<NavigationGraph.Node>();full.add(origin);full.addAll(route);full.add(destination);result=List.copyOf(full);}
            }
        }
        return result;
    }
}
