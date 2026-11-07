package com.ambition.neoworld.navigation;

import java.util.*;
import java.util.function.BiPredicate;

/** World-independent authored road graph. Distance alone never creates an edge. */
public final class NavigationGraph {
    public record Node(String id, String dimension, double x, double y, double z) {
        public Node {
            if (id==null || id.isBlank() || dimension==null || dimension.isBlank()
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Invalid navigation marker");
        }
        public double distance(Node other) { return Math.sqrt(distanceSquared(other.x,other.y,other.z)); }
        public double distanceSquared(double px,double py,double pz) { return (x-px)*(x-px)+(y-py)*(y-py)+(z-pz)*(z-pz); }
    }
    public record Edge(String a,String b) {
        public Edge {
            if(a==null || b==null || a.equals(b)) throw new IllegalArgumentException("Invalid navigation edge");
            if(a.compareTo(b)>0) { String swap=a; a=b; b=swap; }
        }
    }
    private final Map<String,Node> nodes = new LinkedHashMap<>();
    private record Cell(String dimension,int x,int z) {}
    private final Map<Cell,Set<String>> cells=new HashMap<>();
    private final Map<String,Set<String>> links = new HashMap<>();
    private final Map<Edge,Boolean> overrides = new HashMap<>();
    private long revision;
    public Collection<Node> nodes() { return List.copyOf(nodes.values()); }
    public Node node(String id) { return nodes.get(id); }
    public long revision() { return revision; }
    public Map<Edge,Boolean> overrides() { return Map.copyOf(overrides); }
    public Boolean override(String a,String b) { return overrides.get(new Edge(a,b)); }
    public Set<String> neighbors(String id) { return Set.copyOf(links.getOrDefault(id,Set.of())); }
    private static Cell cell(Node n) { return new Cell(n.dimension(),(int)Math.floor(n.x()/16),(int)Math.floor(n.z()/16)); }
    public List<Node> nearby(String dimension,double x,double y,double z,double radius) {
        if(!Double.isFinite(radius)||radius<=0||radius>64) throw new IllegalArgumentException("Invalid lookup radius");
        var result=new ArrayList<Node>();
        for(int cx=(int)Math.floor((x-radius)/16);cx<=(int)Math.floor((x+radius)/16);cx++)
            for(int cz=(int)Math.floor((z-radius)/16);cz<=(int)Math.floor((z+radius)/16);cz++)
                for(String id:cells.getOrDefault(new Cell(dimension,cx,cz),Set.of())) {
                    Node node=nodes.get(id);
                    if(node.distanceSquared(x,y,z)<=radius*radius) result.add(node);
                }
        result.sort(Comparator.comparing(Node::id)); return result;
    }
    public Set<Edge> edges() {
        Set<Edge> result=new HashSet<>();
        links.forEach((a,neighbors)->neighbors.forEach(b->result.add(new Edge(a,b))));
        return result;
    }
    public void add(Node node) {
        if(nodes.size()>=8192 || nodes.containsKey(node.id())) throw new IllegalArgumentException("Duplicate marker or marker limit reached");
        nodes.put(node.id(),node); links.put(node.id(),new HashSet<>());
        cells.computeIfAbsent(cell(node),c->new HashSet<>()).add(node.id()); revision++;
    }
    public void remove(String id) {
        Node removed=nodes.remove(id); if(removed==null) return;
        var bucket=cells.get(cell(removed)); bucket.remove(id); if(bucket.isEmpty()) cells.remove(cell(removed));
        links.remove(id); links.values().forEach(v->v.remove(id));
        overrides.keySet().removeIf(e->e.a().equals(id)||e.b().equals(id)); revision++;
    }
    public void connect(String a,String b) {
        requirePair(a,b);
        if(Boolean.FALSE.equals(overrides.get(new Edge(a,b)))) return;
        if(links.get(a).add(b)) { links.get(b).add(a); revision++; }
    }
    public void disconnect(String a,String b) {
        requirePair(a,b);
        if(links.get(a).remove(b)) { links.get(b).remove(a); revision++; }
    }
    public void override(String a,String b,boolean enabled) {
        requirePair(a,b); overrides.put(new Edge(a,b),enabled); revision++;
        if(enabled) connect(a,b); else disconnect(a,b);
    }
    public void autoConnect(String id,double radius,BiPredicate<Node,Node> walkable) {
        Node a=nodes.get(id);
        if(a==null || !Double.isFinite(radius) || radius<=0) throw new IllegalArgumentException("Invalid auto-connect request");
        for(Node b:nodes.values()) {
            if(a.id().equals(b.id()) || !a.dimension().equals(b.dimension())) continue;
            Edge edge=new Edge(a.id(),b.id());
            if(overrides.containsKey(edge)) continue;
            if(a.distance(b)<=radius && walkable.test(a,b)) connect(a.id(),b.id());
            else disconnect(a.id(),b.id());
        }
    }
    public List<Node> route(String from,String to) {
        return route(from,to,(a,b)->true);
    }
    public List<Node> route(String from,String to,BiPredicate<Node,Node> accessible) {
        if(!nodes.containsKey(from)||!nodes.containsKey(to)) return List.of();
        record Visit(String id,double cost) {}
        var pending=new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::cost));
        Map<String,Double> distance=new HashMap<>(); Map<String,String> previous=new HashMap<>();
        distance.put(from,0d); pending.add(new Visit(from,0));
        while(!pending.isEmpty()) {
            Visit visit=pending.remove();
            if(visit.cost()>distance.getOrDefault(visit.id(),Double.POSITIVE_INFINITY)) continue;
            if(visit.id().equals(to)) {
                var result=new LinkedList<Node>(); String id=to;
                while(id!=null) { result.addFirst(nodes.get(id)); id=previous.get(id); }
                return List.copyOf(result);
            }
            for(String next:links.get(visit.id())) {
                if(!accessible.test(nodes.get(visit.id()),nodes.get(next))) continue;
                double cost=visit.cost()+nodes.get(visit.id()).distance(nodes.get(next));
                if(cost<distance.getOrDefault(next,Double.POSITIVE_INFINITY)) {
                    distance.put(next,cost); previous.put(next,visit.id()); pending.add(new Visit(next,cost));
                }
            }
        }
        return List.of();
    }
    private void requirePair(String a,String b) {
        if(a.equals(b)||!nodes.containsKey(a)||!nodes.containsKey(b)||!nodes.get(a).dimension().equals(nodes.get(b).dimension()))
            throw new IllegalArgumentException("Markers must exist in the same dimension");
    }
}
