package com.ambition.neoworld.data;

import com.ambition.neoworld.economy.CurrencyMath;
import com.ambition.neoworld.level.LevelFormula;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// คลาสสำหรับเก็บข้อมูลของผู้เล่น เช่น เลเวล, ค่าประสบการณ์ (EXP), และสกุลเงินต่างๆ (Silver, Diamond; legacy archived data)
public class PlayerData {
    private static final Codec<Long> NON_NEGATIVE_LONG = Codec.LONG.validate(value -> value >= 0
            ? DataResult.success(value) : DataResult.error(() -> "EXP tracking values must be non-negative"));
    // Codec สำหรับแปลงข้อมูล PlayerData ไป-กลับระหว่าง Object กับไฟล์เซฟเกม (NBT)
    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("level").orElse(1).forGetter(PlayerData::getLevel),
            Codec.LONG.fieldOf("exp").orElse(0L).forGetter(PlayerData::getExp),
            Codec.LONG.fieldOf("silver").orElse(0L).forGetter(PlayerData::getSilver),
            Codec.LONG.fieldOf("gold").orElse(0L).forGetter(PlayerData::getLegacyRetiredBalance),
            Codec.LONG.fieldOf("diamond").orElse(0L).forGetter(PlayerData::getDiamond),
            NON_NEGATIVE_LONG.optionalFieldOf("total_exp", 0L).forGetter(PlayerData::getTotalExp)
        ).apply(instance, PlayerData::new)
    );

    private int level;     // เลเวลตัวละคร (เริ่มต้น 1, สูงสุดตาม LevelFormula.MAX_LEVEL)
    private long exp;      // ค่าประสบการณ์ปัจจุบันในเลเวลนี้
    private long totalExp; // EXP ที่ได้รับตั้งแต่เริ่มนับ ไม่รวมประวัติก่อนอัปเดต
    private long silver;   // เหรียญเงิน (ใช้จ่ายทั่วไป)
    private long legacyRetiredBalance; // Archived save value, never an active currency.
    private long diamond;  // เหรียญเพชร (เงินเติม / แคชพรีเมียม)

    // Constructor ค่าเริ่มต้น (เลเวล 1, EXP 0, เงินทุกสกุล 0)
    public PlayerData() {
        this(1, 0L, 0L, 0L, 0L);
    }

    // Constructor กำหนดค่าเริ่มต้นทั้งหมด
    public PlayerData(int level, long exp, long silver, long legacyRetiredBalance, long diamond) {
        this(level, exp, silver, legacyRetiredBalance, diamond, 0L);
    }

    /** Restores saved counters without synthesizing missing legacy EXP. */
    public PlayerData(int level, long exp, long silver, long legacyRetiredBalance, long diamond,
                      long totalExp) {
        this.level = Math.max(1, Math.min(LevelFormula.MAX_LEVEL, level));
        this.exp = Math.max(0L, exp);
        this.silver = Math.max(0L, silver);
        this.legacyRetiredBalance = Math.max(0L, legacyRetiredBalance);
        this.diamond = Math.max(0L, diamond);
        this.totalExp = Math.max(0L, totalExp);
    }

    // === Level & EXP ===
    public int getLevel() {
        return this.level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, Math.min(LevelFormula.MAX_LEVEL, level));
    }

    public long getExp() {
        return this.exp;
    }

    public void setExp(long exp) {
        this.exp = Math.max(0L, exp);
    }

    public void addExp(long amount) {
        addExp(amount, true);
    }

    public void addExp(long amount, boolean countTotal) {
        if (amount > 0) {
            if (countTotal) this.totalExp = amount > Long.MAX_VALUE - this.totalExp ? Long.MAX_VALUE : this.totalExp + amount;
            if (this.level < LevelFormula.MAX_LEVEL) {
                this.exp = amount > Long.MAX_VALUE - this.exp ? Long.MAX_VALUE : this.exp + amount;
            }
        }
    }

    public long getTotalExp() { return this.totalExp; }

    public void removeTotalExp(long amount) {
        if (amount > 0) this.totalExp = amount >= this.totalExp ? 0L : this.totalExp - amount;
    }

    // === Silver (เหรียญเงิน - ใช้จ่ายทั่วไป) ===
    public long getSilver() {
        return this.silver;
    }

    public void setSilver(long amount) {
        this.silver = Math.max(0L, amount);
    }

    public boolean addSilver(long amount) {
        if (!CurrencyMath.canAdd(this.silver, amount)) return false;
        this.silver += amount;
        return true;
    }

    public boolean removeSilver(long amount) {
        if (amount > 0 && this.silver >= amount) {
            this.silver -= amount;
            return true;
        }
        return false;
    }

    public boolean hasEnoughSilver(long amount) {
        return amount > 0 && this.silver >= amount;
    }

    /** Read-only compatibility value, excluded from active wallets and client synchronization. */
    public long getLegacyRetiredBalance() { return legacyRetiredBalance; }

    // === Diamond (เหรียญเพชร - เงินเติม/พรีเมียม) ===
    public long getDiamond() {
        return this.diamond;
    }

    public void setDiamond(long amount) {
        this.diamond = Math.max(0L, amount);
    }

    public boolean addDiamond(long amount) {
        if (!CurrencyMath.canAdd(this.diamond, amount)) return false;
        this.diamond += amount;
        return true;
    }

    public boolean removeDiamond(long amount) {
        if (amount > 0 && this.diamond >= amount) {
            this.diamond -= amount;
            return true;
        }
        return false;
    }

    public boolean hasEnoughDiamond(long amount) {
        return amount > 0 && this.diamond >= amount;
    }
}

