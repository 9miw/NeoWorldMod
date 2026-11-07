package com.ambition.neoworld.navigation;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Resolves the local visible road and destination onto loaded support surfaces. */
public final class NavigationTrailBuilder {
    private static final double RANGE = 24;
    private static final double HEIGHT = .15;
    private NavigationTrailBuilder() {}

    public static NavigationTrailView build(Level level, Vec3 position, List<NavigationGraph.Node> route, NavigationSavedData.Target target) {
        return new NavigationTrailView(level.dimension().location(), path(level, position, route), ring(level, position, target));
    }

    private static List<Vec3> path(Level level, Vec3 position, List<NavigationGraph.Node> route) {
        List<NavigationGraph.Node> nearby = new ArrayList<>();
        double authoredLength = 0;
        for (var node : route) {
            if (!nearby.isEmpty()) authoredLength += nearby.getLast().distance(node);
            nearby.add(node);
            if (authoredLength > RANGE + 16 || nearby.size() >= NavigationTrailView.MAX_PATH_POINTS
                    || (nearby.size() > 2 && node.distanceSquared(position.x, position.y, position.z) > RANGE * RANGE)) break;
        }
        for (int i = 0; i + 1 < nearby.size(); i++) {
            if (!NavigationWalk.between(level, nearby.get(i), nearby.get(i + 1))) {
                nearby = new ArrayList<>(nearby.subList(0, i + 1));
                break;
            }
        }
        // A per-snapshot cache avoids probing the same support shapes again for each curve check.
        var surfaces = new java.util.HashMap<NavigationCurve.Point, java.util.Optional<Vec3>>();
        java.util.function.Function<NavigationCurve.Point, Vec3> surface = p -> surfaces.computeIfAbsent(p,
                point -> java.util.Optional.ofNullable(NavigationWalk.surface(level, point.x(), point.y(), point.z()))).orElse(null);
        var samples = NavigationCurve.sample(nearby, .25,
                p -> surface.apply(p) != null,
                (a, b) -> {
                    var from = surface.apply(a);
                    var to = surface.apply(b);
                    return from != null && to != null && Math.abs(from.y - to.y) <= .61;
                });
        List<Vec3> result = new ArrayList<>();
        double length = 0;
        for (var sample : samples) {
            var ground = surface.apply(sample);
            if (ground == null) break;
            if (ground.distanceToSqr(position) > RANGE * RANGE) {
                if (!result.isEmpty()) break;
                continue;
            }
            Vec3 point = ground.add(0, HEIGHT, 0);
            if (!result.isEmpty()) {
                double distance = point.distanceTo(result.getLast());
                if (distance < .001) continue;
                if (distance > 1 || Math.abs(point.y - result.getLast().y) > .61) break;
                length += distance;
                if (length > RANGE) break;
            }
            result.add(point);
            if (result.size() == NavigationTrailView.MAX_PATH_POINTS) break;
        }
        return result.size() < 2 ? List.of() : List.copyOf(result);
    }

    private static List<Vec3> ring(Level level, Vec3 position, NavigationSavedData.Target target) {
        if (!target.dimension().equals(level.dimension().location().toString())
                || position.distanceToSqr(new Vec3(target.x(), target.y(), target.z())) > RANGE * RANGE) return List.of();
        List<Vec3> points = new ArrayList<>(NavigationTrailView.MAX_RING_POINTS);
        for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI * 2 / 48;
            var surface = NavigationWalk.surface(level, target.x() + .75 * Math.cos(angle),
                    target.y(), target.z() + .75 * Math.sin(angle));
            if (surface == null || (!points.isEmpty() && Math.abs(surface.y + HEIGHT - points.getLast().y) > .61)) return List.of();
            points.add(surface.add(0, HEIGHT, 0));
        }
        if (Math.abs(points.getFirst().y - points.getLast().y) > .61) return List.of();
        points.add(points.getFirst());
        return List.copyOf(points);
    }
}
