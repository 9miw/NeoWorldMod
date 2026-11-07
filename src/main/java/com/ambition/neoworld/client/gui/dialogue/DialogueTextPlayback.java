package com.ambition.neoworld.client.gui.dialogue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Time-based visible code-point budget, independent of rendering and the Minecraft clock. */
public final class DialogueTextPlayback {
    public enum Action { NONE, REVEAL, SELECT }
    private static final long STEP_NANOS = 30_000_000L;
    // Java's extended graphemes keep combining marks and emoji together. Thai leading
    // vowels additionally belong with the following base, rather than appearing alone.
    private static final Pattern CLUSTER = Pattern.compile("[\u0E40-\u0E44]?\\X");
    private final Set<Integer> heldKeys = new HashSet<>();
    private int[] boundaries = new int[0];
    private long startedAt;
    private boolean waiting = true;
    private boolean skipped;

    public void show(String text, long now) {
        reflow(text);
        startedAt = now;
        waiting = false;
        skipped = false;
    }

    /** A width change may rewrap whitespace, but must not restart the speaker's clock. */
    public void reflow(String text) {
        var ends = new ArrayList<Integer>();
        var matcher = CLUSTER.matcher(text);
        int codePoints = 0;
        while (matcher.find()) {
            String cluster = matcher.group();
            codePoints += cluster.codePointCount(0, cluster.length());
            // SARA AM is a spacing Thai vowel; keep it with the preceding marked base
            // on Java versions whose grapheme tables split it from a tone-mark cluster.
            if (cluster.codePointAt(0) == 0x0E33 && !ends.isEmpty()) ends.set(ends.size() - 1, codePoints);
            else ends.add(codePoints);
        }
        boundaries = ends.stream().mapToInt(Integer::intValue).toArray();
    }

    public void waitForNpc() {
        waiting = true;
        skipped = false;
        boundaries = new int[0];
        // Held keys survive selection and a server response: repeats are never a second press.
    }

    public int visibleCodePoints(long now) {
        if (waiting || boundaries.length == 0) return 0;
        long elapsed = Math.max(0, now - startedAt);
        int count = skipped ? boundaries.length : (int) Math.min(boundaries.length, elapsed / STEP_NANOS);
        return count == 0 ? 0 : boundaries[count - 1];
    }

    public boolean canChoose(long now) {
        return !waiting && (boundaries.length == 0 || skipped
                || visibleCodePoints(now) == boundaries[boundaries.length - 1]);
    }

    public boolean isWaiting() { return waiting; }

    public Action pressAdvance(int key, boolean selectsChoice, long now) {
        if (!heldKeys.add(key) || waiting) return Action.NONE;
        if (!canChoose(now)) {
            skipped = true;
            return Action.REVEAL;
        }
        return selectsChoice ? Action.SELECT : Action.NONE;
    }

    public void releaseAdvance(int key) { heldKeys.remove(key); }
}
