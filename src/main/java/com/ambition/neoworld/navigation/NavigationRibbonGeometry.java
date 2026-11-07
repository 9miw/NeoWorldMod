package com.ambition.neoworld.navigation;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Shared geometry math, independent of the render thread and graphics context. */
public final class NavigationRibbonGeometry {
    public record Edge(Vec3 left, Vec3 right, double distance) {}
    private NavigationRibbonGeometry() {}

    /** Keep half-block risers above the tread instead of interpolating a diagonal through it. */
    public static List<Vec3> steps(List<Vec3> points) {
        List<Vec3> result = new ArrayList<>(points.size() * 2);
        for (Vec3 point : points) {
            if (!result.isEmpty()) {
                Vec3 previous = result.getLast();
                if (point.y - previous.y > .2) result.add(new Vec3(previous.x, point.y, previous.z));
                else if (previous.y - point.y > .2) result.add(new Vec3(point.x, previous.y, point.z));
            }
            result.add(point);
        }
        return List.copyOf(result);
    }

    public static List<Edge> edges(List<Vec3> points, double halfWidth, boolean closed) {
        if (!Double.isFinite(halfWidth) || halfWidth <= 0) throw new IllegalArgumentException("Invalid ribbon width");
        List<Edge> result = new ArrayList<>(points.size());
        double distance = 0;
        for (int i = 0; i < points.size(); i++) {
            Vec3 point = points.get(i);
            if (i > 0) distance += point.distanceTo(points.get(i - 1));
            Vec3 previous = points.get(i == 0 ? (closed && points.size() > 2 ? points.size() - 2 : 0) : i - 1);
            Vec3 next = points.get(i + 1 == points.size() ? (closed && points.size() > 2 ? 1 : i) : i + 1);
            Vec3 tangent = next.subtract(previous);
            double length = Math.hypot(tangent.x, tangent.z);
            // An endpoint may be a purely vertical riser: use the next horizontal direction.
            if (length < .000001) {
                for (int j = 1; j < points.size(); j++) {
                    Vec3 candidate = points.get(Math.min(points.size() - 1, i + j)).subtract(points.get(Math.max(0, i - j)));
                    double candidateLength = Math.hypot(candidate.x, candidate.z);
                    if (candidateLength >= .000001) { tangent = candidate; length = candidateLength; break; }
                }
            }
            Vec3 side = length < .000001 ? new Vec3(halfWidth, 0, 0)
                    : new Vec3(-tangent.z / length * halfWidth, 0, tangent.x / length * halfWidth);
            result.add(new Edge(point.add(side), point.subtract(side), distance));
        }
        return List.copyOf(result);
    }

    public static double pulse(double distance, double seconds) {
        double phase = (distance - 2.5 * seconds) % 5.5;
        if (phase < 0) phase += 5.5;
        double offset = Math.min(phase, 5.5 - phase);
        return Math.exp(-offset * offset / .22);
    }
}
