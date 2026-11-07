package com.ambition.neoworld.navigation;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Picks the first entity under the crosshair, bounded by the first solid block. */
public final class NavigationAim {
    public record Candidate<T>(T entity,AABB bounds,boolean npc) {}
    private NavigationAim() {}
    public static <T> Candidate<T> pick(Vec3 eye,Vec3 direction,double distance,List<Candidate<T>> candidates) {
        Vec3 end=eye.add(direction.normalize().scale(distance));
        Candidate<T> result=null; double best=distance*distance;
        for(var candidate:candidates) {
            var hit=candidate.bounds().contains(eye) ? java.util.Optional.of(eye) : candidate.bounds().clip(eye,end);
            if(hit.isEmpty()) continue;
            double squared=hit.get().distanceToSqr(eye);
            if(squared<best) { best=squared; result=candidate; }
        }
        return result;
    }
}
