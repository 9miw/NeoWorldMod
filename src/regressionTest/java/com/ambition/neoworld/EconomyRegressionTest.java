package com.ambition.neoworld;

import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.economy.CurrencyMath;
import com.ambition.neoworld.level.LevelFormula;
import com.mojang.serialization.JsonOps;

// กรณีที่เคยทำให้เงินติดลบ เหรียญหาย และซื้อของได้โดยจ่ายไม่ครบ
public final class EconomyRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        PlayerData data = new PlayerData(1, 0, Long.MAX_VALUE - 1, Long.MAX_VALUE, Long.MAX_VALUE);
        check(data.addSilver(1), "deposit to exact limit");
        check(!data.addSilver(1) && data.getSilver() == Long.MAX_VALUE, "silver overflow preserves balance");
        check(!data.addGold(1) && data.getGold() == Long.MAX_VALUE, "gold overflow preserves balance");
        check(!data.addDiamond(1) && data.getDiamond() == Long.MAX_VALUE, "diamond overflow preserves balance");
        check(!data.addSilver(0) && !data.addGold(-1), "invalid deposits rejected");
        check(!data.hasEnoughSilver(-1) && !data.hasEnoughGold(0) && !data.hasEnoughDiamond(-1), "invalid prices rejected");
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
        data.setExp(1);
        data.addExp(Long.MAX_VALUE);
        check(data.getExp() == Long.MAX_VALUE, "EXP overflow saturates");
        data.setLevel(Integer.MAX_VALUE);
        check(data.getLevel() == LevelFormula.MAX_LEVEL, "level cap enforced");
        PlayerData restored = PlayerData.CODEC.parse(JsonOps.INSTANCE,
                PlayerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        check(restored.getGold() == Long.MAX_VALUE && restored.getExp() == Long.MAX_VALUE,
                "large values survive save round trip");
        check(restored.getLevel() == LevelFormula.MAX_LEVEL, "level survives save round trip");
        System.out.println("Passed " + checks + " economy regression checks.");
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
}
