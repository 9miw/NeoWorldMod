package com.ambition.neoworld.client.gui.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.ambition.neoworld.client.gui.player.PlayerActionLayout.Rect;

/** Runs without the client/NeoForge loader: java --class-path build/classes/java/main <this file>. */
public final class PlayerActionLayoutRegressionTest {
    private static int checks;
    public static void main(String[] args) {
        Rect body = new Rect(280, 100, 60, 140);
        Rect first = PlayerActionLayout.place(body, 150, 120, 640, 290, List.of(body));
        require(first != null, "ordinary adjacent panel must fit");
        verify(first, 640, 290, List.of(body));
        require(first.equals(PlayerActionLayout.place(body, 150, 120, 640, 290, List.of(body))), "deterministic placement");
        require(PlayerActionLayout.place(body, 150, 120, 100, 80, List.of(body)) == null, "undersized window");
        require(PlayerActionLayout.place(body, 150, 120, 640, 290, List.of(new Rect(0, 0, 640, 290))) == null, "fully blocked window");
        var occupied = new ArrayList<Rect>();
        for (int i = 0; i < 8; i++) {
            Rect placed = PlayerActionLayout.place(new Rect(312, 160, 1, 1), 120, 90, 640, 290, occupied);
            if (placed == null) break;
            verify(placed, 640, 290, occupied); occupied.add(placed);
        }
        require(occupied.size() >= 4, "several panels must share the viewport");
        Random random = new Random(51020);
        int successes = 0;
        for (int i = 0; i < 600; i++) {
            int width = 320 + random.nextInt(641), bottom = 180 + random.nextInt(361);
            var blocks = new ArrayList<Rect>();
            for (int j = 0; j < 4; j++) blocks.add(new Rect(random.nextInt(width), random.nextInt(bottom), 20 + random.nextInt(70), 20 + random.nextInt(100)));
            Rect owner = blocks.getFirst();
            Rect result = PlayerActionLayout.place(owner, 120 + random.nextInt(80), 90 + random.nextInt(60), width, bottom, blocks);
            if (result != null) { successes++; verify(result, width, bottom, blocks); }
        }
        require(successes > 200, "randomized scenarios must exercise successful placement");
        System.out.println("Passed " + checks + " player-action layout checks (" + successes + " randomized placements).");
    }
    private static void verify(Rect placed, int width, int bottom, List<Rect> obstacles) {
        require(placed.x() >= 8 && placed.y() >= 8, "top/left window margin");
        require(placed.x() + placed.width() <= width - 8 && placed.y() + placed.height() <= bottom, "window/HUD clearance");
        for (Rect block : obstacles) require(!placed.overlaps(block, 6), "body/panel/hint clearance");
    }
    private static void require(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
}
