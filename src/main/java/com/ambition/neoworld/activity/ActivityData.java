package com.ambition.neoworld.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ambition.neoworld.activity.ActivityReward.Pending;

/** Serialized with player inventory and money; runtime session clocks are deliberately excluded. */
public final class ActivityData {
    public static final int[] ONLINE_MINUTES = {15, 30, 60, 120};
    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    public static final Codec<ActivityData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("day", Long.MIN_VALUE).forGetter(d -> d.day),
            Codec.LONG.optionalFieldOf("month", Long.MIN_VALUE).forGetter(d -> d.month),
            Codec.BOOL.optionalFieldOf("checked_in", false).forGetter(d -> d.checkedIn),
            Codec.intRange(0, 30).optionalFieldOf("month_days", 0).forGetter(d -> d.monthDays),
            Codec.intRange(0, 7200).optionalFieldOf("online_seconds", 0).forGetter(d -> d.onlineSeconds),
            Codec.intRange(0, 15).optionalFieldOf("online_mask", 0).forGetter(d -> d.onlineMask),
            Pending.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(d -> d.pending),
            Codec.intRange(-1, 0x3fffffff).optionalFieldOf("monthly_claimed", -1).forGetter(d -> d.monthlyClaimed)
    ).apply(i, ActivityData::new));
    public long day, month;
    public boolean checkedIn;
    public int monthDays, onlineSeconds, onlineMask;
    private int monthlyClaimed;
    public final List<Pending> pending;
    public ActivityData() { this(Long.MIN_VALUE, Long.MIN_VALUE, false, 0, 0, 0, List.of(), 0); }
    private ActivityData(long day, long month, boolean checkedIn, int monthDays, int seconds, int mask,
            List<Pending> pending, int monthlyClaimed) {
        this.day = day; this.month = month; this.checkedIn = checkedIn; this.monthDays = monthDays;
        onlineSeconds = seconds; onlineMask = mask; this.pending = new ArrayList<>(pending);
        // Old saves had cumulative milestones; conservatively preserve those redeemed indices.
        this.monthlyClaimed = monthlyClaimed == -1 ? (1 << monthDays) - 1 : monthlyClaimed;
        this.monthDays = Integer.bitCount(this.monthlyClaimed);
    }
    public static LocalDate activityDate(Instant now) { return now.atZone(ZONE).minusHours(4).toLocalDate(); }
    public static long nextReset(Instant now) {
        return activityDate(now).plusDays(1).atTime(4, 0).atZone(ZONE).toEpochSecond();
    }
    public boolean roll(Instant now) {
        var date = activityDate(now);
        long nextDay = date.toEpochDay(), nextMonth = date.getYear() * 12L + date.getMonthValue();
        // A backwards wall clock must not reopen already claimed periods.
        if (nextDay < day) return false;
        if (nextMonth > month) { month = nextMonth; monthDays = 0; monthlyClaimed = 0; }
        if (nextDay > day) { day = nextDay; checkedIn = false; onlineSeconds = 0; onlineMask = 0; }
        // Monthly claims never enter the inbox: old cumulative entitlements cannot bypass the calendar.
        pending.removeIf(reward -> reward.key().startsWith("monthly/"));
        return true;
    }
    public boolean checkIn(ActivityReward daily) {
        if (checkedIn) return false;
        checkedIn = true;
        pending.add(new Pending("daily/" + day, "เช็กอิน " + LocalDate.ofEpochDay(day), daily));
        return true;
    }
    public int currentMonthDay() { return LocalDate.ofEpochDay(day).getDayOfMonth(); }
    public String monthlyKey(int index) { return "monthly/" + month + "/" + index; }
    /** 0 future/unavailable, 1 today, 2 received, 3 expired. */
    public int monthlyStatus(int index) {
        if (index < 1 || index > 30) return 0;
        if ((monthlyClaimed & (1 << (index - 1))) != 0) return 2;
        return index == currentMonthDay() ? 1 : index < currentMonthDay() ? 3 : 0;
    }
    public boolean canClaimMonthly(String key) {
        int index = currentMonthDay();
        return index <= 30 && monthlyStatus(index) == 1 && monthlyKey(index).equals(key);
    }
    /** Call only after inventory and currency delivery succeed on the server thread. */
    public boolean markMonthlyClaimed(String key) {
        if (!canClaimMonthly(key)) return false;
        monthlyClaimed |= 1 << (currentMonthDay() - 1);
        monthDays = Integer.bitCount(monthlyClaimed); return true;
    }
    public void addOnlineSeconds(int seconds, List<ActivityReward> rewards) {
        if (seconds < 0) throw new IllegalArgumentException("Negative online time");
        onlineSeconds = (int) Math.min(7200L, (long) onlineSeconds + seconds);
        for (int i = 0; i < ONLINE_MINUTES.length; i++) {
            if (onlineSeconds >= ONLINE_MINUTES[i] * 60 && (onlineMask & (1 << i)) == 0) {
                onlineMask |= 1 << i;
                pending.add(new Pending("online/" + day + "/" + i,
                        "ออนไลน์ " + ONLINE_MINUTES[i] + " นาที • " + LocalDate.ofEpochDay(day), rewards.get(i)));
            }
        }
    }
}
