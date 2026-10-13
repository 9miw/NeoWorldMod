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
            Codec.intRange(0, 28).optionalFieldOf("month_days", 0).forGetter(d -> d.monthDays),
            Codec.intRange(0, 7200).optionalFieldOf("online_seconds", 0).forGetter(d -> d.onlineSeconds),
            Codec.intRange(0, 15).optionalFieldOf("online_mask", 0).forGetter(d -> d.onlineMask),
            Pending.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(d -> d.pending)
    ).apply(i, ActivityData::new));
    public long day, month;
    public boolean checkedIn;
    public int monthDays, onlineSeconds, onlineMask;
    public final List<Pending> pending;
    public ActivityData() { this(Long.MIN_VALUE, Long.MIN_VALUE, false, 0, 0, 0, List.of()); }
    private ActivityData(long day, long month, boolean checkedIn, int monthDays, int seconds, int mask,
            List<Pending> pending) {
        this.day = day; this.month = month; this.checkedIn = checkedIn; this.monthDays = monthDays;
        onlineSeconds = seconds; onlineMask = mask; this.pending = new ArrayList<>(pending);
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
        if (nextMonth > month) { month = nextMonth; monthDays = 0; }
        if (nextDay > day) { day = nextDay; checkedIn = false; onlineSeconds = 0; onlineMask = 0; }
        return true;
    }
    public boolean checkIn(ActivityReward daily, List<ActivityReward> monthly) {
        if (checkedIn) return false;
        checkedIn = true;
        pending.add(new Pending("daily/" + day, "เช็กอิน " + LocalDate.ofEpochDay(day), daily));
        if (monthDays < 28) {
            monthDays++;
            pending.add(new Pending("monthly/" + month + "/" + monthDays,
                    "รายเดือน " + ((month - 1) / 12) + "-" + ((month - 1) % 12 + 1) + " วันที่ " + monthDays,
                    monthly.get(monthDays - 1)));
        }
        return true;
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
