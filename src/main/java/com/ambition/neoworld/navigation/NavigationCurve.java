package com.ambition.neoworld.navigation;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.BiPredicate;

/** Short Catmull-Rom segments; collision rejection falls back to the authored polyline. */
public final class NavigationCurve {
    public record Point(double x,double y,double z) {}
    private NavigationCurve() {}
    public static List<Point> sample(List<NavigationGraph.Node> nodes,double spacing,Predicate<Point> safe) {
        return sample(nodes,spacing,safe,(a,b)->true);
    }
    public static List<Point> sample(List<NavigationGraph.Node> nodes,double spacing,Predicate<Point> safe,BiPredicate<Point,Point> traversable) {
        if(!Double.isFinite(spacing)||spacing<=0) throw new IllegalArgumentException("Invalid spacing");
        List<Point> result=new ArrayList<>();
        for(int i=0;i+1<nodes.size();i++) {
            var a=nodes.get(Math.max(0,i-1)); var b=nodes.get(i); var c=nodes.get(i+1); var d=nodes.get(Math.min(nodes.size()-1,i+2));
            int count=Math.max(1,(int)Math.ceil(b.distance(c)/spacing));
            List<Point> segment=new ArrayList<>(); boolean valid=true;
            for(int j=0;j<=count;j++) {
                double t=(double)j/count;
                Point p=new Point(curve(a.x(),b.x(),c.x(),d.x(),t),
                        b.y()+(c.y()-b.y())*t,curve(a.z(),b.z(),c.z(),d.z(),t));
                if(!safe.test(p) || (!segment.isEmpty()&&!traversable.test(segment.getLast(),p))) valid=false;
                segment.add(p);
            }
            if(!valid) {
                segment.clear();
                for(int j=0;j<=count;j++) {
                    double t=(double)j/count;
                    segment.add(new Point(b.x()+(c.x()-b.x())*t,b.y()+(c.y()-b.y())*t,b.z()+(c.z()-b.z())*t));
                }
            }
            result.addAll(segment);
        }
        return List.copyOf(result);
    }
    private static double curve(double a,double b,double c,double d,double t) {
        return .5*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t);
    }
}
