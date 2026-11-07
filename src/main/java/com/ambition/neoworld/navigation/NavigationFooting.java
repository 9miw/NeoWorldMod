package com.ambition.neoworld.navigation;

import net.minecraft.world.phys.AABB;
import java.util.*;

/** Chooses support under the whole player footprint, including the leading edge on stairs. */
public final class NavigationFooting {
    private NavigationFooting() {}
    public static OptionalDouble height(double x,double expectedY,double z,List<AABB> collisions) {
        var candidates=new TreeSet<Double>(Comparator.reverseOrder());
        for(var box:collisions) {
            if(box.maxY<=expectedY+1.01 && box.maxY>=expectedY-1.01
                    && box.maxX>x-.3 && box.minX<x+.3 && box.maxZ>z-.3 && box.minZ<z+.3) candidates.add(box.maxY);
        }
        for(double feet:candidates) {
            var body=new AABB(x-.3,feet+.001,z-.3,x+.3,feet+1.8,z+.3);
            if(collisions.stream().noneMatch(b->b.intersects(body))) return OptionalDouble.of(feet);
        }
        return OptionalDouble.empty();
    }
}
