package com.ambition.neoworld.data;

import com.ambition.neoworld.economy.CurrencyMath;
import com.ambition.neoworld.level.LevelFormula;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

// คลาสสำหรับเก็บข้อมูลของผู้เล่น เช่น เลเวล, ค่าประสบการณ์ (EXP), และสกุลเงินต่างๆ (Silver, Gold, Diamond)
public class PlayerData {
    // Codec สำหรับแปลงข้อมูล PlayerData ไป-กลับระหว่าง Object กับไฟล์เซฟเกม (NBT)
    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("level").orElse(1).forGetter(PlayerData::getLevel),
            Codec.LONG.fieldOf("exp").orElse(0L).forGetter(PlayerData::getExp),
            Codec.LONG.fieldOf("silver").orElse(0L).forGetter(PlayerData::getSilver),
            Codec.LONG.fieldOf("gold").orElse(0L).forGetter(PlayerData::getGold),
            Codec.LONG.fieldOf("diamond").orElse(0L).forGetter(PlayerData::getDiamond)
        ).apply(instance, PlayerData::new)
    );

    private int level;     // เลเวลตัวละคร (เริ่มต้น 1, สูงสุดตาม LevelFormula.MAX_LEVEL)
    private long exp;      // ค่าประสบการณ์ปัจจุบันในเลเวลนี้
    private long silver;   // เหรียญเงิน (ใช้จ่ายทั่วไป)
    private long gold;     // เหรียญทอง (เงินชั้นสูง / ตีบวก)
    private long diamond;  // เหรียญเพชร (เงินเติม / แคชพรีเมียม)

    // Constructor ค่าเริ่มต้น (เลเวล 1, EXP 0, เงินทุกสกุล 0)
    public PlayerData() {
        this(1, 0L, 0L, 0L, 0L);
    }

    // Constructor กำหนดค่าเริ่มต้นทั้งหมด
    public PlayerData(int level, long exp, long silver, long gold, long diamond) {
        this.level = Math.max(1, Math.min(LevelFormula.MAX_LEVEL, level));
        this.exp = Math.max(0L, exp);
        this.silver = Math.max(0L, silver);
        this.gold = Math.max(0L, gold);
        this.diamond = Math.max(0L, diamond);
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
        if (amount > 0) {
            this.exp = amount > Long.MAX_VALUE - this.exp ? Long.MAX_VALUE : this.exp + amount;
        }
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

    // === Gold (เหรียญทอง - เงินชั้นสูงสำหรับตีบวก) ===
    public long getGold() {
        return this.gold;
    }

    public void setGold(long amount) {
        this.gold = Math.max(0L, amount);
    }

    public boolean addGold(long amount) {
        if (!CurrencyMath.canAdd(this.gold, amount)) return false;
        this.gold += amount;
        return true;
    }

    public boolean removeGold(long amount) {
        if (amount > 0 && this.gold >= amount) {
            this.gold -= amount;
            return true;
        }
        return false;
    }

    public boolean hasEnoughGold(long amount) {
        return amount > 0 && this.gold >= amount;
    }

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

