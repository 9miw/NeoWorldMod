package com.ambition.neoworld.ranking;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable ranking values; no server/client dependencies. */
public final class RankingTable {
    public enum Category { PVP, LEVEL, SILVER }
    public record Entry(UUID id,String name,int level,long exp,long silver,int rating,int wins,int losses,long totalExp) {
        public Entry(UUID id,String name,int level,long exp,long silver,int rating,int wins,int losses) {
            this(id,name,level,exp,silver,rating,wins,losses,0L);
        }
        public Entry {
            Objects.requireNonNull(id); Objects.requireNonNull(name);
            if(name.isBlank() || name.length()>64 || level<1 || exp<0 || totalExp<0 || silver<0 || wins<0 || losses<0)
                throw new IllegalArgumentException("Invalid ranking entry");
        }
        public Entry named(String name) { return new Entry(id,name,level,exp,silver,rating,wins,losses,totalExp); }
    }
    public record View(List<Entry> leaders,Entry personal,int position) {
        public View {
            leaders=List.copyOf(leaders);
            if(leaders.size()>10 || position<0 || (position>0 && personal==null)) throw new IllegalArgumentException("Invalid ranking view");
        }
    }
    private RankingTable() { }
    public static View select(Category category,List<Entry> entries,UUID player) {
        Comparator<Entry> order=switch(category) {
            case PVP -> Comparator.comparingInt(Entry::rating).reversed().thenComparing(Comparator.comparingInt(Entry::wins).reversed());
            case LEVEL -> Comparator.comparingInt(Entry::level).reversed().thenComparing(Comparator.comparingLong(Entry::exp).reversed());
            case SILVER -> Comparator.comparingLong(Entry::silver).reversed();
        };
        var sorted=entries.stream().filter(e -> category!=Category.PVP || (long)e.wins()+e.losses()>0)
                .sorted(order.thenComparing(e -> e.id().toString())).toList();
        var own=entries.stream().filter(e -> e.id().equals(player)).findFirst().orElse(null);
        int position=0;
        for(int i=0;i<sorted.size();i++) if(sorted.get(i).id().equals(player)) { position=i+1; break; }
        return new View(sorted.stream().limit(10).toList(),own,position);
    }
}
