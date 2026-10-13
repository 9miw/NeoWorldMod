package com.ambition.neoworld.database;

import java.sql.Connection;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import com.ambition.neoworld.Config;
import com.ambition.neoworld.pvp.ranked.EloRating;
import com.ambition.neoworld.pvp.ranked.PvpRatingRepository;
import com.ambition.neoworld.pvp.ranked.RankedMatch;

public final class PvpRatingRegressionTest {
    public static int run() throws Exception {
        check(EloRating.delta(1000,1000,1)==16,"Equal win +16");
        check(EloRating.delta(1000,1000,0)==-16,"Equal loss -16");
        check(EloRating.delta(1000,1400,1)>16,"Upset earns more");
        check(EloRating.delta(1400,1000,1)<16,"Favorite earns less");
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),id=UUID.randomUUID();
        var match=new RankedMatch(a,b,"A","B",0);
        match.round(a); check(match.result==null,"One round is not a match win"); match.round(b); match.round(a);
        check(match.result==1,"Best of three winner"); match.forfeit(a); check(match.result==1,"Result cannot change after completion");
        for (String mode : new String[]{"", ";MODE=MariaDB"}) {
            var source=new JdbcDataSource(); source.setURL("jdbc:h2:mem:pvp_"+UUID.randomUUID()+mode+";DB_CLOSE_DELAY=-1");
            NeoWorldDatabase.runMigrations(source,mode.isEmpty() ? Config.DatabaseType.H2 : Config.DatabaseType.MARIADB,"test_");
            try (var c=source.getConnection()) {
                c.setAutoCommit(false);
                check(PvpRatingRepository.apply(c,"test_pvp_ratings","test_pvp_results",id,a,b,"A","B",1,"test"),"First result applied"); c.commit();
                check(!PvpRatingRepository.apply(c,"test_pvp_ratings","test_pvp_results",id,a,b,"A","B",1,"test"),"Duplicate result ignored"); c.commit();
                var report=PvpRatingRepository.report(c,"test_pvp_results",id);
                check(report.available() && report.afterA()-report.beforeA()==16 && report.afterB()-report.beforeB()==-16,"Stored report preserves exact deltas after duplicate submission");
                try(var change=c.prepareStatement("UPDATE test_pvp_ratings SET rating=9000 WHERE player_id=?")) { change.setString(1,a.toString()); change.executeUpdate(); }
                check(PvpRatingRepository.report(c,"test_pvp_results",id).afterA()==1016,"Report remains this match's score after later rating changes"); c.rollback();
                check(rating(c,a)==1016 && rating(c,b)==984,"Both ratings updated once");
                var readOnly=PvpRatingRepository.view(c,"test_pvp_ratings",a,"A");
                check(readOnly.player().rating()==1016 && readOnly.position()==1,"Display-only query provides Rating and rank");
                UUID newcomer=UUID.randomUUID();
                var defaults=PvpRatingRepository.view(c,"test_pvp_ratings",newcomer,"Newcomer");
                check(defaults.player().rating()==1000 && defaults.position()==0,"Unregistered player display has initial Rating without ranking");
                try(var q=c.prepareStatement("SELECT COUNT(*) FROM test_pvp_ratings WHERE player_id=?")) {
                    q.setString(1,newcomer.toString()); try(var r=q.executeQuery()) { r.next(); check(r.getInt(1)==0,"Opening GUI does not insert a player row"); }
                }
                check(PvpRatingRepository.position(c,"test_pvp_ratings",a)==1 && PvpRatingRepository.position(c,"test_pvp_ratings",b)==2,"Numeric rank follows Rating order");
                try(var add=c.prepareStatement("INSERT INTO test_pvp_ratings(player_id,player_name,rating,wins,losses) VALUES (?,?,?,0,0)")) {
                    add.setString(1,UUID.randomUUID().toString()); add.setString(2,"NewPlayer"); add.setInt(3,1000); add.executeUpdate();
                }
                for(int i=0;i<12;i++) {
                    try(var add=c.prepareStatement("INSERT INTO test_pvp_ratings(player_id,player_name,rating,wins,losses) VALUES (?,?,2000,1,0)")) {
                        add.setString(1,UUID.randomUUID().toString()); add.setString(2,"Leader"+i); add.executeUpdate();
                    }
                }
                check(PvpRatingRepository.position(c,"test_pvp_ratings",b)==14,"Personal rank works outside Top 10");
                check(PvpRatingRepository.position(c,"test_pvp_ratings",UUID.randomUUID())==0,"Unknown player has no numeric rank");
                c.commit();
                PvpRatingRepository.apply(c,"test_pvp_ratings","test_pvp_results",UUID.randomUUID(),a,b,"A","B",0,"rollback"); c.rollback();
                check(rating(c,a)==1016 && rating(c,b)==984,"Rollback preserves both ratings");
                try { PvpRatingRepository.apply(c,"test_pvp_ratings","test_pvp_results",UUID.randomUUID(),a,b,"A","B",1,null); throw new AssertionError("Invalid final result accepted"); }
                catch (java.sql.SQLException expected) { c.rollback(); }
                check(rating(c,a)==1016 && rating(c,b)==984,"Result insert failure rolls back both rating updates");
            }
            // Upgrade v6 without erasing old ratings/results or inventing historical Rating deltas.
            try(var c=source.getConnection(); var s=c.createStatement()) { s.execute("DROP TABLE test_pvp_results_details"); s.execute("DELETE FROM test_schema_history WHERE version=7"); }
            NeoWorldDatabase.runMigrations(source,mode.isEmpty() ? Config.DatabaseType.H2 : Config.DatabaseType.MARIADB,"test_");
            try(var c=source.getConnection()) {
                check(rating(c,a)==1016 && rating(c,b)==984,"v6-to-v7 preserves existing ratings");
                check(!PvpRatingRepository.report(c,"test_pvp_results",id).available(),"Historical result does not fabricate a Rating delta");
            }
            // Simulate an existing version-5 database retaining player data while adding ranked tables.
            try (var c=source.getConnection(); var s=c.createStatement()) { s.execute("DROP TABLE test_pvp_results_details"); s.execute("DROP TABLE test_pvp_results"); s.execute("DROP TABLE test_pvp_ratings"); s.execute("DELETE FROM test_schema_history WHERE version IN (6,7)"); }
            NeoWorldDatabase.runMigrations(source,mode.isEmpty() ? Config.DatabaseType.H2 : Config.DatabaseType.MARIADB,"test_");
            try (var c=source.getConnection(); var s=c.createStatement(); var r=s.executeQuery("SELECT MAX(version) FROM test_schema_history")) { r.next(); check(r.getInt(1)==7,"Upgrade to version 7 succeeds"); }
        }
        return 39;
    }
    private static int rating(Connection c,UUID id) throws Exception {
        try (var s=c.prepareStatement("SELECT rating FROM test_pvp_ratings WHERE player_id=?")) { s.setString(1,id.toString()); try(var r=s.executeQuery()) { r.next(); return r.getInt(1); } }
    }
    private static void check(boolean good,String message) { if(!good) throw new AssertionError(message); }
}
