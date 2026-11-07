package com.ambition.neoworld.navigation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Local render data only. These dense points are never sent over the network. */
public record NavigationTrailView(ResourceLocation dimension,List<Vec3> path,List<Vec3> ring) {
    public static final int MAX_PATH_POINTS=128,MAX_RING_POINTS=49;
    public NavigationTrailView {
        if(dimension==null||path.size()>MAX_PATH_POINTS||ring.size()>MAX_RING_POINTS)throw new IllegalArgumentException("Invalid local trail");
        path=List.copyOf(path);ring=List.copyOf(ring);
        for(var p:path)validate(p);for(var p:ring)validate(p);
    }
    private static void validate(Vec3 p){if(!Double.isFinite(p.x)||!Double.isFinite(p.y)||!Double.isFinite(p.z))throw new IllegalArgumentException("Invalid trail point");}
    public static NavigationTrailView empty(ResourceLocation dimension){return new NavigationTrailView(dimension,List.of(),List.of());}
}
