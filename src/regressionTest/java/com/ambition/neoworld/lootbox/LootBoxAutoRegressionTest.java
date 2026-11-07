package com.ambition.neoworld.lootbox;

import com.ambition.neoworld.client.gui.lootbox.LootBoxPreferences;
import java.nio.file.Files;

public final class LootBoxAutoRegressionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        reject(() -> new LootBoxAutoRun(0, 0));
        reject(() -> new LootBoxAutoRun(65, 0));
        var run = new LootBoxAutoRun(3, 100);
        check(run.ready(100) && run.remaining() == 3, "new session opens requested additional boxes");
        run.opened(100);
        reject(() -> run.opened(101));
        check(run.remaining() == 2 && !run.ready(179) && run.ready(180), "successful exchange decrements once and preserves cooldown");
        run.opened(180); run.opened(260);
        check(run.remaining() == 0 && !run.ready(340), "stops exactly at requested count");
        var cancelled = new LootBoxAutoRun(10, 0); cancelled.stop();
        check(cancelled.remaining() == 0 && !cancelled.ready(1000), "cancelled sessions never continue");
        var maximum = new LootBoxAutoRun(64, 0);
        for (int i = 0; i < 64; i++) maximum.opened(i * 80L);
        check(maximum.remaining() == 0, "maximum batch stops after exactly 64 exchanges");
        var fast = new LootBoxAutoRun(5, 100, true);
        check(!fast.ready(103) && fast.ready(104), "fast session begins after four ticks");
        for (int i = 0; i < 5; i++) {
            fast.opened(104 + i * 4);
            check(!fast.ready(105 + i * 4), "fast mode cannot open before next four-tick interval");
        }
        check(fast.remaining() == 0 && fast.interval() == 4, "five fast exchanges finish within one second");
        var directory = Files.createTempDirectory("lootbox-preference-test");
        var path = directory.resolve("settings.properties");
        try {
            var settings = new LootBoxPreferences(path);
            check(settings.effects(), "new settings enable effects");
            settings.setEffects(false);
            check(!new LootBoxPreferences(path).effects(), "effects off survives reload");
            settings.setEffects(true);
            check(new LootBoxPreferences(path).effects(), "effects on survives reload");
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(directory); }
        System.out.println("Loot box auto/preference checks passed: " + checks);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected rejection");
    }
}
