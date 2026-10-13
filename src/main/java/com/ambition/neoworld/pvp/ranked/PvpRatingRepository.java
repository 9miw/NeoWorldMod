package com.ambition.neoworld.pvp.ranked;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.ambition.neoworld.database.NeoWorldDatabase;

/** Results and both ratings commit together; match UUID makes retries idempotent. */
public final class PvpRatingRepository {
    private PvpRatingRepository() { }
    public record Rating(String id, String name, int rating, int wins, int losses) { }
    public record View(Rating player,int position) { }
    public static View view(UUID id,String name) throws SQLException {
        try(var c=NeoWorldDatabase.getConnection()) { return view(c,NeoWorldDatabase.tableName("pvp_ratings"),id,name); }
    }
    public static View view(Connection c,String table,UUID id,String name) throws SQLException {
        if(!table.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Invalid ratings table");
        Rating own=null;
        try(var query=c.prepareStatement("SELECT player_id,player_name,rating,wins,losses FROM " + table + " WHERE player_id=?")) {
            query.setString(1,id.toString()); try(var r=query.executeQuery()) { if(r.next()) own=new Rating(id.toString(),r.getString("player_name"),r.getInt("rating"),r.getInt("wins"),r.getInt("losses")); }
        }
        if(own==null) return new View(new Rating(id.toString(),name,EloRating.INITIAL,0,0),0);
        if(own.wins()+own.losses()==0) return new View(own,0);
        try(var query=c.prepareStatement("SELECT COUNT(*)+1 FROM " + table + " WHERE wins+losses>0 AND (rating>? OR (rating=? AND wins>?) OR (rating=? AND wins=? AND player_id<?))")) {
            query.setInt(1,own.rating()); query.setInt(2,own.rating()); query.setInt(3,own.wins());
            query.setInt(4,own.rating()); query.setInt(5,own.wins()); query.setString(6,id.toString());
            try(var r=query.executeQuery()) { r.next(); return new View(own,r.getInt(1)); }
        }
    }
    public record ResultReport(boolean available,int beforeA,int afterA,int beforeB,int afterB) { }
    public static Rating player(UUID id, String name) throws SQLException {
        return NeoWorldDatabase.inTransaction(c -> ensure(c, NeoWorldDatabase.tableName("pvp_ratings"), id, name));
    }
    private static Rating ensure(Connection c, String table, UUID id, String name) throws SQLException {
        try (var query = c.prepareStatement("SELECT * FROM " + table + " WHERE player_id=? FOR UPDATE")) {
            query.setString(1, id.toString());
            try (var rows = query.executeQuery()) { if (rows.next()) return new Rating(id.toString(), rows.getString("player_name"), rows.getInt("rating"), rows.getInt("wins"), rows.getInt("losses")); }
        }
        try (var insert = c.prepareStatement("INSERT INTO " + table + " (player_id,player_name,rating,wins,losses) VALUES (?,?,1000,0,0)")) {
            insert.setString(1, id.toString()); insert.setString(2, name); insert.executeUpdate();
        }
        return new Rating(id.toString(), name, EloRating.INITIAL, 0, 0);
    }
    public static ResultReport result(UUID match, UUID a, UUID b, String nameA, String nameB, double score, String reason) throws SQLException {
        var result=NeoWorldDatabase.inTransaction(c -> {
            String results=NeoWorldDatabase.tableName("pvp_results");
            apply(c,NeoWorldDatabase.tableName("pvp_ratings"),results,match,a,b,nameA,nameB,score,reason);
            return report(c,results,match);
        });
        PvpRankingCache.clear();
        return result;
    }
    public static ResultReport report(Connection c,String results,UUID match) throws SQLException {
        if(!results.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Invalid result table");
        try(var query=c.prepareStatement("SELECT before_a,after_a,before_b,after_b FROM " + results + "_details WHERE match_id=?")) {
            query.setString(1,match.toString());
            try(var r=query.executeQuery()) { return r.next() ? new ResultReport(true,r.getInt(1),r.getInt(2),r.getInt(3),r.getInt(4)) : new ResultReport(false,0,0,0,0); }
        }
    }
    /** Caller owns commit/rollback; also exercised against isolated test databases. */
    public static boolean apply(Connection c, String ratings, String results, UUID match, UUID a, UUID b,
            String nameA, String nameB, double score, String reason) throws SQLException {
        if (!ratings.matches("[a-z][a-z0-9_]*") || !results.matches("[a-z][a-z0-9_]*") || a.equals(b)) throw new IllegalArgumentException("Invalid result identifiers");
        EloRating.delta(1000, 1000, score);
        try (var query = c.prepareStatement("SELECT match_id FROM " + results + " WHERE match_id=?")) {
            query.setString(1, match.toString()); try (var rows = query.executeQuery()) { if (rows.next()) return false; }
        }
        Rating ra, rb;
        if (a.toString().compareTo(b.toString()) < 0) { ra = ensure(c, ratings, a, nameA); rb = ensure(c, ratings, b, nameB); }
        else { rb = ensure(c, ratings, b, nameB); ra = ensure(c, ratings, a, nameA); }
        int change = EloRating.delta(ra.rating(), rb.rating(), score);
        update(c, ratings, a, nameA, ra.rating()+change, score == 1, score == 0);
        update(c, ratings, b, nameB, rb.rating()-change, score == 0, score == 1);
        try (var insert = c.prepareStatement("INSERT INTO " + results + " (match_id,player_a,player_b,score_a,reason) VALUES (?,?,?,?,?)")) {
            insert.setString(1, match.toString()); insert.setString(2, a.toString()); insert.setString(3, b.toString());
            insert.setDouble(4, score); insert.setString(5, reason); insert.executeUpdate();
        }
        try(var insert=c.prepareStatement("INSERT INTO " + results + "_details (match_id,before_a,after_a,before_b,after_b) VALUES (?,?,?,?,?)")) {
            insert.setString(1,match.toString()); insert.setInt(2,ra.rating()); insert.setInt(3,ra.rating()+change);
            insert.setInt(4,rb.rating()); insert.setInt(5,rb.rating()-change); insert.executeUpdate();
        }
        return true;
    }
    private static void update(Connection c, String table, UUID id, String name, int rating, boolean win, boolean loss) throws SQLException {
        try (var update = c.prepareStatement("UPDATE " + table + " SET player_name=?,rating=?,wins=wins+?,losses=losses+? WHERE player_id=?")) {
            update.setString(1, name); update.setInt(2, rating); update.setInt(3, win ? 1 : 0); update.setInt(4, loss ? 1 : 0); update.setString(5, id.toString()); update.executeUpdate();
        }
    }
    public static List<Rating> top() throws SQLException {
        try (var c = NeoWorldDatabase.getConnection(); var query = c.prepareStatement("SELECT * FROM " + NeoWorldDatabase.tableName("pvp_ratings") + " WHERE wins+losses>0 ORDER BY rating DESC,wins DESC,player_id ASC LIMIT 10"); var rows = query.executeQuery()) {
            List<Rating> top = new ArrayList<>();
            while (rows.next()) top.add(new Rating(rows.getString("player_id"),rows.getString("player_name"),rows.getInt("rating"),rows.getInt("wins"),rows.getInt("losses")));
            return List.copyOf(top);
        }
    }
    public static int position(UUID player) throws SQLException {
        try(var c=NeoWorldDatabase.getConnection()) { return position(c,NeoWorldDatabase.tableName("pvp_ratings"),player); }
    }
    public static int position(Connection c,String table,UUID player) throws SQLException {
        if(!table.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Invalid ratings table");
        try (var query=c.prepareStatement("SELECT COUNT(*)+1 FROM " + table
                + " p, " + table + " me WHERE me.player_id=? AND me.wins+me.losses>0 AND p.wins+p.losses>0"
                + " AND (p.rating>me.rating OR (p.rating=me.rating AND p.wins>me.wins) OR (p.rating=me.rating AND p.wins=me.wins AND p.player_id<me.player_id))")) {
            query.setString(1,player.toString());
            try(var rows=query.executeQuery()) {
                rows.next(); int position=rows.getInt(1);
                try(var own=c.prepareStatement("SELECT wins+losses FROM " + table + " WHERE player_id=?")) {
                    own.setString(1,player.toString()); try(var r=own.executeQuery()) { return r.next() && r.getInt(1)>0 ? position : 0; }
                }
            }
        }
    }
}
