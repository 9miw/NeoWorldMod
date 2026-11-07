package com.ambition.neoworld.client.gui.dialogue;

public final class DialogueTextPlaybackRegressionTest {
    private static final long STEP = 30_000_000L;
    private static final long START = 5_000_000_000L;
    private static final int ENTER = 257, SPACE = 32;

    public static void main(String[] args) {
        var speech = new DialogueTextPlayback();
        check(!speech.canChoose(START), "waiting for NPC cannot choose");
        speech.show("เก่งน้ำ🙂", START);
        check(speech.visibleCodePoints(START) == 0, "new speech begins empty");
        check(prefix("เก่งน้ำ🙂", speech.visibleCodePoints(START + STEP)).equals("เก่"), "Thai leading vowel and tone reveal with base");
        check(prefix("เก่งน้ำ🙂", speech.visibleCodePoints(START + STEP * 2)).equals("เก่ง"), "second Thai cluster");
        check(prefix("เก่งน้ำ🙂", speech.visibleCodePoints(START + STEP * 3)).equals("เก่งน้ำ"), "Thai sara am and tone stay with base");
        check(speech.canChoose(START + STEP * 4), "complete speech enables choices");
        speech.show("👩‍💻กำลังคุย", START);
        check(prefix("👩‍💻กำลังคุย", speech.visibleCodePoints(START + STEP)).equals("👩‍💻"), "emoji ZWJ is one cluster");
        check(speech.pressAdvance(ENTER, true, START) == DialogueTextPlayback.Action.REVEAL, "first Enter reveals only");
        check(speech.canChoose(START), "skip completes current speech");
        check(speech.pressAdvance(ENTER, true, START + STEP) == DialogueTextPlayback.Action.NONE, "key repeat cannot select after skip");
        speech.releaseAdvance(ENTER);
        check(speech.pressAdvance(ENTER, true, START + STEP) == DialogueTextPlayback.Action.SELECT, "new Enter may select");
        speech.waitForNpc();
        check(speech.visibleCodePoints(START + STEP * 100) == 0, "selection clears old speech immediately");
        check(!speech.canChoose(START + STEP * 100), "wait for server response blocks old choices");
        speech.show("ใหม่", START + STEP * 100);
        check(speech.visibleCodePoints(START + STEP * 100) == 0, "new NPC response restarts reveal");
        check(speech.pressAdvance(ENTER, true, START + STEP * 100) == DialogueTextPlayback.Action.NONE, "held Enter cannot act on newly arrived speech");
        speech.releaseAdvance(ENTER);
        check(speech.pressAdvance(SPACE, false, START + STEP * 100) == DialogueTextPlayback.Action.REVEAL, "Space also reveals");
        speech.releaseAdvance(SPACE);
        check(speech.pressAdvance(SPACE, false, START + STEP * 101) == DialogueTextPlayback.Action.NONE, "Space never submits a choice");
        speech.show("abcdef", START);
        speech.reflow("abcdef");
        check(speech.visibleCodePoints(START + STEP * 2) == 2, "resize/reflow preserves timing");
        speech.show("", START);
        check(speech.canChoose(START), "empty NPC speech is immediately ready");
        System.out.println("Dialogue text playback regression checks passed");
    }

    private static String prefix(String text, int codePoints) {
        return text.substring(0, text.offsetByCodePoints(0, codePoints));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
