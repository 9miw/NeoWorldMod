package com.ambition.neoworld.navigation;

import com.ambition.neoworld.network.navigation.NavigationGraphPayload;
import java.util.*;

/** Atomic graph page assembly; the previous graph remains usable until every page validates. */
public final class NavigationClientGraph {
    private NavigationGraph graph=new NavigationGraph();
    private long revision=-1,pendingRevision=-1,pendingBase;
    private int nextPage,totalPages;
    private boolean pendingReset;
    private Map<String,NavigationGraph.Node> nodes=new HashMap<>(),pendingNodes;
    private Set<NavigationGraph.Edge> edges=new HashSet<>(),pendingEdges;
    public NavigationGraph graph(){return graph;}
    public long revision(){return revision;}
    public boolean ready(){return revision>=0;}
    public boolean transferring(){return pendingNodes!=null;}
    public boolean accept(NavigationGraphPayload p) {
        if(p.page()==0) {
            if(!p.reset()&&p.baseRevision()!=revision)throw new IllegalArgumentException("Navigation graph revision mismatch");
            pendingRevision=p.revision();pendingBase=p.baseRevision();nextPage=0;totalPages=p.pages();pendingReset=p.reset();
            pendingNodes=p.reset()?new HashMap<>():new HashMap<>(nodes);pendingEdges=p.reset()?new HashSet<>():new HashSet<>(edges);
        }
        if(pendingNodes==null||p.page()!=nextPage||p.pages()!=totalPages||p.revision()!=pendingRevision||p.baseRevision()!=pendingBase||p.reset()!=pendingReset)
            throw new IllegalArgumentException("Out-of-order navigation graph pages");
        var removed=Set.copyOf(p.removedNodes());
        removed.forEach(pendingNodes::remove);
        if(!removed.isEmpty())pendingEdges.removeIf(e->removed.contains(e.a())||removed.contains(e.b()));
        for(var n:p.nodes())pendingNodes.put(n.id(),n);
        pendingEdges.removeAll(p.removedEdges());pendingEdges.addAll(p.edges());
        if(pendingNodes.size()>8192)throw new IllegalArgumentException("Too many navigation markers");
        if(++nextPage<totalPages)return false;
        NavigationGraph rebuilt=new NavigationGraph();for(var n:pendingNodes.values())rebuilt.add(n);
        for(var e:pendingEdges)rebuilt.connect(e.a(),e.b());
        graph=rebuilt;nodes=pendingNodes;edges=pendingEdges;revision=pendingRevision;
        pendingNodes=null;pendingEdges=null;return true;
    }
    public void clear(){graph=new NavigationGraph();nodes=new HashMap<>();edges=new HashSet<>();revision=-1;pendingNodes=null;pendingEdges=null;}
}
