package com.ambition.neoworld.upgrade;

public final class UpgradeBonusRegressionTest {
    public static void main(String[] args) {
        near(UpgradeBonusMath.damage(10, 10, 3), 3, "+10 weapon");
        near(UpgradeBonusMath.damage(50, 15, 3), 22.5, "+15 NeoWorld sword");
        near(UpgradeBonusMath.damage(10, 9, 3), 2.7, "downgrade recalculates original basis");
        near(UpgradeBonusMath.damage(10, 0, 3), 0, "break clears damage");
        near(UpgradeBonusMath.flat(15, 0.2) * 4, 12, "four armor pieces stack");
        near(UpgradeBonusMath.flat(15, 0.1), 1.5, "impact and stun armor");
        near(UpgradeBonusMath.flat(0, 0.1), 0, "break clears flat bonus");
        near(UpgradeBonusMath.damage(10, 15, 0), 0, "config disables damage bonus");
        near(UpgradeBonusMath.damage(-10, 15, 3), 0, "negative original damage gives no bonus");
        near(UpgradeBonusMath.damage(10, 15, 3) - UpgradeBonusMath.damage(10, 14, 3), 0.3,
                "last upgrade adds one level of damage, not a compounded percentage");
        try {
            UpgradeBonusMath.damage(10, 16, 3);
            throw new AssertionError("Invalid level accepted");
        } catch (IllegalArgumentException expected) {}
        try {
            UpgradeBonusMath.flat(1, Double.NaN);
            throw new AssertionError("NaN accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("Upgrade bonus regression checks passed");
    }

    private static void near(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 1e-9) throw new AssertionError(message + ": " + actual);
    }
}
