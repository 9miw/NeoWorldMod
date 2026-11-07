package com.ambition.neoworld;

import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.database.DatabaseRegressionTest;
import com.ambition.neoworld.command.MarketCommandRules;
import com.ambition.neoworld.economy.CurrencyMath;
import com.ambition.neoworld.level.LevelFormula;
import com.ambition.neoworld.market.MarketMath;
import com.ambition.neoworld.market.MarketResult;
import com.ambition.neoworld.market.MarketTransactionPolicy;
import com.mojang.serialization.JsonOps;

// กรณีที่เคยทำให้เงินติดลบ เหรียญหาย และซื้อของได้โดยจ่ายไม่ครบ
public final class EconomyRegressionTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(java.util.Arrays.stream(com.ambition.neoworld.item.CoinItem.CoinType.values()).map(Enum::name).toList()
                .equals(java.util.List.of("SILVER", "DIAMOND")), "only Silver and Diamond remain active currencies");
        var oldConfig=com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        oldConfig.set("upgrade.costs.goldForLevel1",789L);
        oldConfig.set("upgrade.costs.goldForLevel2",999L);
        oldConfig.set("upgrade.costs.silverForLevel2",555L);
        var migration=com.ambition.neoworld.upgrade.UpgradeConfig.COMPATIBLE_SPEC;
        check(!migration.isCorrect(oldConfig), "old upgrade costs require migration before config correction");
        migration.correct(oldConfig);
        check(oldConfig.<Long>get("upgrade.costs.silverForLevel1")==789L
                && oldConfig.<Long>get("upgrade.costs.silverForLevel2")==555L
                && !oldConfig.contains("upgrade.costs.goldForLevel1"), "custom numeric costs preserved and explicit Silver wins");
        migration.correct(oldConfig);
        check(oldConfig.<Long>get("upgrade.costs.silverForLevel1")==789L, "upgrade config migration is idempotent");
        var buffer=io.netty.buffer.Unpooled.buffer();
        try {
            var payload=new com.ambition.neoworld.network.economy.SyncEconomyPayload(Long.MAX_VALUE,42);
            com.ambition.neoworld.network.economy.SyncEconomyPayload.STREAM_CODEC.encode(buffer,payload);
            var decoded=com.ambition.neoworld.network.economy.SyncEconomyPayload.STREAM_CODEC.decode(buffer);
            check(decoded.equals(payload)&&!buffer.isReadable(),"two-currency wire payload preserves field order and maximum amounts");
        } finally {buffer.release();}
        com.ambition.neoworld.client.state.ClientEconomyData.update(7,9);
        check(com.ambition.neoworld.client.state.ClientEconomyData.getSilver()==7
                && com.ambition.neoworld.client.state.ClientEconomyData.getDiamond()==9,"client tracks exactly the two active balances");
        PlayerData data = new PlayerData(1, 0, Long.MAX_VALUE - 1, Long.MAX_VALUE, Long.MAX_VALUE);
        check(data.addSilver(1), "deposit to exact limit");
        check(!data.addSilver(1) && data.getSilver() == Long.MAX_VALUE, "silver overflow preserves balance");
        check(!data.addDiamond(1) && data.getDiamond() == Long.MAX_VALUE, "diamond overflow preserves balance");
        check(!data.addSilver(0), "invalid deposits rejected");
        check(!data.hasEnoughSilver(-1) && !data.hasEnoughDiamond(-1), "invalid prices rejected");
        PlayerData parked = new PlayerData(1,0,100,200,300);
        check(parked.getLegacyRetiredBalance()==200 && parked.getSilver()==100, "legacy value is not converted to Silver");
        check(com.ambition.neoworld.economy.PlayerEconomyManager.deposit(parked,com.ambition.neoworld.item.CoinItem.CoinType.SILVER,10)
                && com.ambition.neoworld.economy.PlayerEconomyManager.deposit(parked,com.ambition.neoworld.item.CoinItem.CoinType.DIAMOND,10), "Silver and Diamond deposits remain enabled");
        PlayerData parkedRestored = PlayerData.CODEC.parse(JsonOps.INSTANCE,PlayerData.CODEC.encodeStart(JsonOps.INSTANCE,parked).getOrThrow()).getOrThrow();
        check(parkedRestored.getLegacyRetiredBalance()==200, "Frozen Gold survives player save and load");
        check(!data.removeSilver(-1) && data.getSilver() == Long.MAX_VALUE, "negative debit preserves balance");
        data.setSilver(100);
        check(!data.removeSilver(101) && data.getSilver() == 100, "insufficient debit preserves balance");
        check(data.removeSilver(100) && data.getSilver() == 0, "exact debit succeeds");
        check(CurrencyMath.total(Long.MAX_VALUE, 2) == -1, "stack multiplication overflow rejected");
        check(CurrencyMath.total(Long.MAX_VALUE, 1) == Long.MAX_VALUE, "maximum single coin supported");
        check(CurrencyMath.total(0, 1) == -1 && CurrencyMath.total(-1, 2) == -1, "invalid coin values rejected");
        check(CurrencyMath.total(100, 64) == 6400, "normal stack value");
        check(!CurrencyMath.canAfford(100, 60, 60), "two separately affordable costs exceed balance");
        check(!CurrencyMath.canAfford(Long.MAX_VALUE, Long.MAX_VALUE, 1), "combined price overflow rejected");
        check(CurrencyMath.canAfford(300, 100, 200), "different denominations use actual combined value");
        check(!CurrencyMath.canAfford(200, 100, 200), "different denominations cannot undercharge");
        check(MarketMath.percentage(10_000, 500) == 500, "market tax uses basis points");
        check(MarketMath.percentage(Long.MAX_VALUE, 10_000) == Long.MAX_VALUE, "market tax avoids overflow");
        check(MarketMath.sellerProceeds(10_001, 500) == 9_501, "market proceeds deduct rounded-down tax");
        check(MarketMath.percentage(100, -1) == -1 && MarketMath.percentage(100, 10_001) == -1,
                "invalid market rates rejected");
        check(MarketTransactionPolicy.checkPurchase(true, false, false, 99, 0, 100, 1, 10, 0).result()
                == MarketResult.INSUFFICIENT_FUNDS, "market rejects insufficient buyer balance");
        check(MarketTransactionPolicy.checkPurchase(false, false, false, 100, 0, 100, 1, 10, 0).result()
                == MarketResult.LISTING_NOT_FOUND, "market rejects a second purchase of removed listing");
        check(MarketTransactionPolicy.checkPurchase(true, false, true, 100, 0, 100, 1, 10, 0).result()
                == MarketResult.CANNOT_BUY_OWN_LISTING, "market rejects buying own listing");
        check(MarketTransactionPolicy.checkPurchase(true, false, false, 100, Long.MAX_VALUE,
                100, 1, 10, 0).result()
                == MarketResult.BALANCE_OVERFLOW, "market preserves sale when offline proceeds would overflow");
        check(MarketTransactionPolicy.checkPurchase(true, false, false, 500, 0,
                100, 5, 10, 500).sellerProceeds() == 475,
                "market purchase plans offline seller proceeds");
        check(MarketTransactionPolicy.checkPurchase(true, false, false, 500, 0,
                100, 5, 10, 500).totalPriceSilver() == 500,
                "market multiplies unit price by purchase quantity");
        check(MarketTransactionPolicy.checkPurchase(true, false, false, 500, 0,
                100, 0, 10, 0).result() == MarketResult.INVALID_QUANTITY,
                "market rejects zero purchase quantity");
        check(MarketTransactionPolicy.checkPurchase(true, false, false, 500, 0,
                100, 11, 10, 0).result() == MarketResult.INSUFFICIENT_STOCK,
                "market rejects purchase quantity above available stock");
        check(MarketTransactionPolicy.checkPurchase(true, false, false, Long.MAX_VALUE, 0,
                Long.MAX_VALUE, 2, 2, 0).result() == MarketResult.INVALID_PRICE,
                "market rejects overflowing purchase total");
        check(MarketCommandRules.durationTicks(2_880) == 3_456_000L, "market command converts duration to ticks");
        check(MarketCommandRules.totalPages(21, 10) == 3, "market command calculates pages");
        check(MarketCommandRules.clampPage(99, 3) == 3, "market command clamps page to available results");
        data.setExp(1);
        data.addExp(Long.MAX_VALUE);
        check(data.getExp() == Long.MAX_VALUE, "EXP overflow saturates");
        data.setLevel(Integer.MAX_VALUE);
        check(data.getLevel() == LevelFormula.MAX_LEVEL, "level cap enforced");
        PlayerData restored = PlayerData.CODEC.parse(JsonOps.INSTANCE,
                PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        check(restored.getLegacyRetiredBalance() == Long.MAX_VALUE && restored.getExp() == Long.MAX_VALUE,
                "large values survive save round trip");
        check(restored.getLevel() == LevelFormula.MAX_LEVEL, "level survives save round trip");
        int databaseChecks = DatabaseRegressionTest.run();
        int marketSecurityChecks = com.ambition.neoworld.market.MarketSecurityRegressionTest.run();
        com.ambition.neoworld.database.PersistenceCrashWindowRegressionTest.run();
        com.ambition.neoworld.market.MarketJournalRegressionTest.run();
        System.out.println("Passed " + checks + " economy, " + databaseChecks + " database, and "
                + marketSecurityChecks + " market security regression checks.");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
