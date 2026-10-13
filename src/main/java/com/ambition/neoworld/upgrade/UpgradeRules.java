package com.ambition.neoworld.upgrade;

/** Pure outcome rules, independent of Minecraft and inventory mutation. */
public final class UpgradeRules {
    public enum Protection { NONE, DOWNGRADE, BREAK }
    public enum Outcome { SUCCESS, UNCHANGED, DOWNGRADE, RESET }
    public record Result(Outcome rolled, Outcome applied, int level, int failures) {}

    private UpgradeRules() {}

    public static Result resolve(int level, int failures, int success, int unchanged, int downgrade,
                                 int reset, int bonus, Protection protection, int roll) {
        if (level < 0 || level >= 15 || failures < 0 || bonus < 0 || bonus > 100 || protection == null
                || roll < 0 || roll >= 100 || success < 0 || unchanged < 0 || downgrade < 0 || reset < 0
                || success > 100 || unchanged > 100 || downgrade > 100 || reset > 100
                || success + unchanged + downgrade + reset != 100) {
            throw new IllegalArgumentException("Invalid upgrade request or probability distribution");
        }
        // Transfer probability to success from unchanged, then downgrade, then reset.
        int remaining = Math.min(bonus, 100 - success);
        success += remaining;
        int taken = Math.min(remaining, unchanged);
        unchanged -= taken;
        remaining -= taken;
        taken = Math.min(remaining, downgrade);
        downgrade -= taken;
        remaining -= taken;
        reset -= remaining;
        Outcome rolled = roll < success ? Outcome.SUCCESS : roll < success + unchanged ? Outcome.UNCHANGED
                : roll < success + unchanged + downgrade ? Outcome.DOWNGRADE : Outcome.RESET;
        Outcome applied = rolled == Outcome.DOWNGRADE && protection == Protection.DOWNGRADE ? Outcome.UNCHANGED
                : rolled == Outcome.RESET && protection == Protection.BREAK ? Outcome.DOWNGRADE : rolled;
        int nextLevel = switch (applied) {
            case SUCCESS -> level + 1;
            case UNCHANGED -> level;
            case DOWNGRADE -> Math.max(0, level - 1);
            case RESET -> 0;
        };
        int nextFailures = applied == Outcome.SUCCESS || applied == Outcome.RESET ? 0
                : failures == Integer.MAX_VALUE ? failures : failures + 1;
        return new Result(rolled, applied, nextLevel, nextFailures);
    }
}
