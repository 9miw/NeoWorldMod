package com.ambition.neoworld.upgrade;

import com.ambition.neoworld.upgrade.UpgradeOrbitMath.Point;

public final class UpgradeOrbitRegressionTest {
    public static void main(String[] args) {
        for (Point end : new Point[] {new Point(0, 2, 0), new Point(2, 0, 0), new Point(0, 0, 2), new Point(1, 2, 3)}) {
            Point start = new Point(0, 0, 0);
            Point center = end.scale(.6);
            Point axis = end.scale(1 / Math.sqrt(end.dot(end)));
            for (int step = 0; step < 24; step++) {
                double angle = step * Math.PI / 12;
                Point point = UpgradeOrbitMath.position(start, end, .6, angle, .25);
                Point offset = point.add(center.scale(-1));
                near(offset.dot(axis), 0, "orbit must not move up/down the blade");
                near(Math.sqrt(offset.dot(offset)), .25, "constant radial distance");
                Point opposite = UpgradeOrbitMath.position(start, end, .6, angle + Math.PI, .25);
                Point midpoint = point.add(opposite).scale(.5);
                near(midpoint.x(), center.x(), "half turn wraps to other side X");
                near(midpoint.y(), center.y(), "half turn wraps to other side Y");
                near(midpoint.z(), center.z(), "half turn wraps to other side Z");
                Point translated = UpgradeOrbitMath.position(new Point(4, 5, 6), end.add(new Point(4, 5, 6)), .6, angle, .25);
                near(translated.x() - point.x(), 4, "follows translated model X");
                near(translated.y() - point.y(), 5, "follows translated model Y");
                near(translated.z() - point.z(), 6, "follows translated model Z");
            }
        }
        try {
            UpgradeOrbitMath.position(new Point(0,0,0), new Point(0,0,0), .5, 0, .1);
            throw new AssertionError("Degenerate blade accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("Upgrade orbit axis/radius/rotation/translation checks passed");
    }
    private static void near(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 1e-8) throw new AssertionError(message);
    }
}
