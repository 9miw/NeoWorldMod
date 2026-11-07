package com.ambition.neoworld.shop;

import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.market.storage.MarketCheckpoint;
import net.minecraft.nbt.CompoundTag;

public final class ShopWalletCheckpointRegressionTest {
    private ShopWalletCheckpointRegressionTest() {}
    public static int run() {
        var original = new PlayerData(7, 9, 100, 200, 300, 900);
        var tag = new CompoundTag();
        MarketCheckpoint.captureWallet(tag, original);
        var current = new PlayerData(8, 10, 1, 2, 3, 999);
        var restored = MarketCheckpoint.restoreWallet(tag, current);
        check(restored.getSilver() == 100 && restored.getLegacyRetiredBalance() == 200 && restored.getDiamond() == 300,
                "shared checkpoint restores every wallet currency");
        check(restored.getLevel() == 8 && restored.getExp() == 10 && restored.getTotalExp() == 999,
                "wallet restore preserves unrelated progression");
        var legacy = new CompoundTag(); legacy.putLong("silver", 75);
        var imported = MarketCheckpoint.restoreWallet(legacy, original);
        check(imported.getSilver() == 75 && imported.getLegacyRetiredBalance() == 200 && imported.getDiamond() == 300,
                "v1 market snapshot preserves currencies it did not store");
        System.out.println("Passed 3 shared wallet checkpoint checks.");
        return 3;
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
