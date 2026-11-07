package com.ambition.neoworld.upgrade;

/** Circular motion in a plane perpendicular to the blade, rather than world-space bobbing. */
public final class UpgradeOrbitMath {
    public record Point(double x, double y, double z) {
        public Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        public Point scale(double s) { return new Point(x * s, y * s, z * s); }
        public double dot(Point p) { return x * p.x + y * p.y + z * p.z; }
        public Point cross(Point p) { return new Point(y * p.z - z * p.y, z * p.x - x * p.z, x * p.y - y * p.x); }
    }
    private UpgradeOrbitMath() {}
    public static Point position(Point start, Point end, double along, double angle, double radius) {
        Point delta = end.add(start.scale(-1));
        double length = Math.sqrt(delta.dot(delta));
        if (!Double.isFinite(length) || length < 1e-6 || !Double.isFinite(angle)
                || !Double.isFinite(radius) || radius < 0 || !Double.isFinite(along) || along < 0 || along > 1) {
            throw new IllegalArgumentException("Invalid blade orbit");
        }
        Point axis = delta.scale(1 / length);
        Point reference = Math.abs(axis.y) < .9 ? new Point(0, 1, 0) : new Point(1, 0, 0);
        Point u = axis.cross(reference);
        u = u.scale(1 / Math.sqrt(u.dot(u)));
        Point v = axis.cross(u);
        return start.add(delta.scale(along)).add(u.scale(radius * Math.cos(angle))).add(v.scale(radius * Math.sin(angle)));
    }
}
