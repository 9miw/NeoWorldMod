package com.ambition.neoworld.pvp.room;

import java.util.UUID;
import com.ambition.neoworld.pvp.ranked.PvpRatingRepository;
public record RoundIntermission(long until,UUID defeated,boolean terminal,PvpRatingRepository.ResultReport report) {
    public static RoundIntermission begin(long now,UUID defeated,boolean terminal,PvpRatingRepository.ResultReport report) {
        return new RoundIntermission(now+100,defeated,terminal,report);
    }
    public int seconds(long now) { return (int)Math.clamp((until-now+19)/20,0,5); }
    public boolean finished(long now) { return now>=until; }
}
