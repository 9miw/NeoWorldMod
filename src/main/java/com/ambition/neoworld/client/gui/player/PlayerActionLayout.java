package com.ambition.neoworld.client.gui.player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Pure screen-space placement, shared by the HUD and its geometry regression checks. */
public final class PlayerActionLayout {
    public record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        public boolean overlaps(Rect other, int gap) {
            return x < other.right() + gap && right() + gap > other.x
                    && y < other.bottom() + gap && bottom() + gap > other.y;
        }
    }
    private PlayerActionLayout() {}

    public static Rect place(Rect body, int width, int height, int viewportWidth, int usableBottom,
                             List<Rect> obstacles) {
        int maxX = viewportWidth - width - 8, maxY = usableBottom - height;
        if (maxX < 8 || maxY < 8) return null;
        var xs = new LinkedHashSet<Integer>();
        var ys = new LinkedHashSet<Integer>();
        xs.add(body.right() + 10); xs.add(body.x - width - 10);
        xs.add(body.x + (body.width - width) / 2); xs.add(8); xs.add(maxX);
        ys.add(body.y + (body.height - height) / 2); ys.add(body.y - height - 10);
        ys.add(body.bottom() + 10); ys.add(8); ys.add(maxY);
        for (Rect block : obstacles) {
            xs.add(block.right() + 8); xs.add(block.x - width - 8);
            ys.add(block.bottom() + 8); ys.add(block.y - height - 8);
        }
        Rect best = null;
        double cost = Double.POSITIVE_INFINITY;
        // Prefer adjacency to the owner; deterministic candidates avoid frame-to-frame randomness.
        List<Rect> blocks = new ArrayList<>(obstacles);
        for (int candidateX : xs) for (int candidateY : ys) {
            int x = Math.clamp(candidateX, 8, maxX), y = Math.clamp(candidateY, 8, maxY);
            Rect candidate = new Rect(x, y, width, height);
            if (blocks.stream().anyMatch(block -> candidate.overlaps(block, 6))) continue;
            double dx = x + width / 2.0 - (body.x + body.width / 2.0);
            double dy = y + height / 2.0 - (body.y + body.height / 2.0);
            double distance = dx * dx + dy * dy;
            if (distance < cost) { best = candidate; cost = distance; }
        }
        return best;
    }
}
