package com.ambition.neoworld.upgrade;

import static com.ambition.neoworld.upgrade.UpgradeRules.*;

public final class UpgradeRulesRegressionTest {
    public static void main(String[] args) {
        int[] counts = new int[4];
        for (int roll = 0; roll < 100; roll++) {
            Result result = resolve(14, 5, 5, 5, 40, 50, 5, Protection.NONE, roll);
            counts[result.applied().ordinal()]++;
        }
        check(counts[0] == 10 && counts[1] == 0 && counts[2] == 40 && counts[3] == 50,
                "Pity must add five percentage points and retain total probability");
        Result reset = resolve(14, 9, 5, 5, 40, 50, 0, Protection.DOWNGRADE, 99);
        check(reset.level() == 0 && reset.failures() == 0, "Downgrade scroll must not prevent a break");
        Result protectedBreak = resolve(14, 9, 5, 5, 40, 50, 0, Protection.BREAK, 99);
        check(protectedBreak.rolled() == Outcome.RESET && protectedBreak.applied() == Outcome.DOWNGRADE
                && protectedBreak.level() == 13 && protectedBreak.failures() == 10, "Break scroll converts reset to downgrade");
        Result protectedDown = resolve(9, 2, 20, 15, 40, 25, 0, Protection.DOWNGRADE, 40);
        check(protectedDown.level() == 9 && protectedDown.failures() == 3, "Downgrade protection preserves level");
        Result success = resolve(14, 9, 5, 5, 40, 50, 0, Protection.NONE, 0);
        check(success.level() == 15 && success.failures() == 0, "Success resets failure points");
        Result floor = resolve(0, Integer.MAX_VALUE, 0, 0, 100, 0, 0, Protection.NONE, 99);
        check(floor.level() == 0 && floor.failures() == Integer.MAX_VALUE, "Level floor and failure overflow");
        Result fullBonus = resolve(2, 1, 0, 0, 0, 100, 100, Protection.NONE, 99);
        check(fullBonus.applied() == Outcome.SUCCESS, "Bonus may safely exhaust every failure bucket");
        try {
            resolve(15, 0, 100, 0, 0, 0, 0, Protection.NONE, 0);
            throw new AssertionError("Max-level attempts must be rejected");
        } catch (IllegalArgumentException expected) {}
        try {
            resolve(0, 0, 80, 30, 0, 0, 0, Protection.NONE, 0);
            throw new AssertionError("Invalid totals must be rejected");
        } catch (IllegalArgumentException expected) {}
        System.out.println("Upgrade outcome regression checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
