package com.ambition.neoworld.economy.analytics;

import com.ambition.neoworld.Config;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;

public final class EconomyAnalyticsRegressionTest {
    private static int checks;
    public static int run() throws Exception {
        checks=0;
        database(Config.DatabaseType.H2,""); database(Config.DatabaseType.MARIADB,";MODE=MariaDB");
        check(EconomyReport.percentile(List.of(BigDecimal.TEN,BigDecimal.valueOf(20)),0.5).compareTo(BigDecimal.valueOf(15))==0,"even median averages middle players");
        check(EconomyReport.percentile(List.of(),0.9)==null,"no active hours does not fabricate zero income");
        check(EconomyReport.csv("=HYPERLINK(\"bad\")").startsWith("\"'="),"spreadsheet formula text escaped");
        check(EconomyReport.csv("-99").equals("\"-99\""),"negative sink amounts remain numeric");
        check(EconomyAnalytics.day(java.time.Instant.parse("2026-10-19T17:00:00Z").toEpochMilli()).equals("2026-10-20"),"snapshot day uses Bangkok timezone");
        return checks + AnalyticsOutboxRegressionTest.run();
    }
    private static void database(Config.DatabaseType type,String mode)throws Exception {
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:analytics_"+type+";DB_CLOSE_DELAY=-1"+mode);
        try(var c=source.getConnection()) {
            EconomyAnalyticsRepository.createSchema(c,type,"T_");
            EconomyAnalyticsRepository.createSchema(c,type,"T_");
            var e=new EconomyAnalyticsRepository.Event("sale-1",10,"MARKET_SOLD","seller",3,false,"market","listing-1","TRANSFER","SILVER",0,0,100,
                    "{\"quantity\":2,\"item_nbt_base64\":\"AQI=\",\"buyer_id\":\"buyer\",\"item_id\":\"minecraft:stone\",\"unit_price\":50,\"total\":100,\"tax\":5}");
            c.setAutoCommit(false);
            EconomyAnalyticsRepository.insert(c,"T_economy_events",e);c.rollback();
            check(count(c,"T_economy_events")==0,"failed transaction leaves no archived sale");
            EconomyAnalyticsRepository.insert(c,"T_economy_events",e);c.commit();
            EconomyAnalyticsRepository.insert(c,"T_economy_events",e);c.commit();
            check(count(c,"T_economy_events")==1,"retry after uncertain commit does not duplicate event");
            try(var s=c.createStatement()) {
                s.execute("CREATE TABLE T_market_transactions(id INTEGER PRIMARY KEY)");
                s.execute("INSERT INTO T_market_transactions VALUES (1)");s.execute("DELETE FROM T_market_transactions WHERE id=1");
            }
            check(count(c,"T_economy_events")==1,"deleting player-visible sales retains archive");
            EconomyAnalyticsRepository.balance(c,"T_",new EconomyAnalyticsRepository.Balance("seller",100,3,false,100,20,5),"2026-10-19");c.commit();
            EconomyAnalyticsRepository.carryDaily(c,"T_","2026-10-20");EconomyAnalyticsRepository.carryDaily(c,"T_","2026-10-20");c.commit();
            check(count(c,"T_economy_daily_balances")==2,"daily carry is idempotent for offline players");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT observed_at,silver FROM T_economy_daily_balances WHERE snapshot_day='2026-10-20'")) {
                r.next();check(r.getLong(1)==100 && r.getLong(2)==100,"offline snapshot retains actual last-seen time");
            }
            EconomyAnalyticsRepository.balance(c,"T_",new EconomyAnalyticsRepository.Balance("seller",200,4,false,120,20,5),"2026-10-20");c.rollback();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT silver FROM T_economy_balances")) {
                r.next();check(r.getLong(1)==100,"failed snapshot transaction retains latest committed balances");
            }
            EconomyAnalyticsRepository.balance(c,"T_",new EconomyAnalyticsRepository.Balance("seller",200,4,false,120,20,5),"2026-10-20");c.commit();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT silver FROM T_economy_daily_balances WHERE snapshot_day='2026-10-19'")) {
                r.next();check(r.getLong(1)==100,"new snapshot preserves earlier day's balance");
            }
            var dir=Files.createTempDirectory("neoworld-analysis-csv-");
            try {
                EconomyReport.exportQuery(c,dir,"events.csv","SELECT detail FROM T_economy_events");
                String csv=Files.readString(dir.resolve("events.csv"));
                check(csv.contains("\"\"quantity\"\":2")&&!csv.contains("clob"),"CSV exports JSON content rather than JDBC CLOB labels");
            } finally {Files.deleteIfExists(dir.resolve("events.csv"));Files.deleteIfExists(dir);}
            report(c,type);
        }
    }
    private static void report(Connection c,Config.DatabaseType type)throws Exception {
        com.ambition.neoworld.shop.ShopRepository.createSchema(c,type,"T_");
        com.ambition.neoworld.shop.ShopRepository.addCurrencies(c,"T_");
        try(var s=c.createStatement()) {
            s.execute("CREATE TABLE T_market_pending_balances (player_id VARCHAR(36) PRIMARY KEY,silver BIGINT)");
            s.execute("INSERT INTO T_shop_stores(id,name) VALUES (1,'Test shop')");
            s.execute("INSERT INTO T_shop_offers(id,shop_id,item_nbt,buy_price,sell_price,category,currency) VALUES (1,1,X'0102',100,50,'MATERIALS','SILVER')");
        }
        insert(c,"listing",5,"MARKET_LISTED","market","TRANSFER",0,"{}");
        insert(c,"mint",20,"WALLET","npc_sell","SOURCE",100,"{\"correlation\":\"mint\",\"item_id\":\"minecraft:stone\",\"quantity\":2,\"action\":\"SELL\",\"unit_price\":50}");
        insert(c,"admin",20,"WALLET","admin_give","ADMIN",999,"{}");
        insert(c,"transfer",20,"WALLET","market_purchase","TRANSFER",-100,"{}");
        insert(c,"tax",20,"MARKET_TAX","market_tax","SINK",5,"{}");
        insert(c,"issued",20,"COIN_ISSUED","activity_reward","ISSUED",50,"{}");
        insert(c,"redeem",20,"WALLET","coin_redemption_unknown","UNKNOWN",50,"{}");
        insert(c,"presence",20,"PRESENCE","playtime","NONE",3600000,"{\"state\":\"ACTIVE\"}");c.commit();
        var dir=Files.createTempDirectory("neoworld-full-report-");
        try {
            new EconomyReport("T_").write(c,dir,8,1000);
            String summary=Files.readString(dir.resolve("summary.csv"));
            check(summary.contains("\"confirmed_system_net/SILVER\",\"95\""),"transfers, admin edits and coin redemption do not inflate minted net");
            check(summary.contains("\"physical_coin_issued/SILVER/activity_reward\",\"50\""),"physical coin issuance reported separately");
            check(!summary.contains("999"),"admin command excluded even if recipient is not an operator");
            String sales=Files.readString(dir.resolve("market_sales.csv"));
            check(sales.contains("\"0.005\""),"sale duration finds listing outside report period");
            check(Files.readString(dir.resolve("npc_trades.csv")).contains("minecraft:stone"),"NPC report carries item ID and audit correlation");
            check(Files.readString(dir.resolve("npc_affordability.csv")).contains("\"1.0000\""),"purchase time uses known system income per active hour");
            check(Files.readString(dir.resolve("player_income.csv")).contains("\"150\",\"150.0000\",\"100\",\"100.0000\""),"wallet receipts and confirmed system income remain distinct");
        } finally {
            try(var paths=Files.list(dir)){for(var path:paths.toList())Files.delete(path);}Files.delete(dir);
        }
    }
    private static void insert(Connection c,String id,long time,String type,String source,String flow,long amount,String detail)throws Exception {
        EconomyAnalyticsRepository.insert(c,"T_economy_events",new EconomyAnalyticsRepository.Event(id,time,type,"seller",3,false,source,
                type.equals("MARKET_LISTED")?"listing-1":"shop:1/offer:1",flow,"SILVER",0,amount<0?0:amount,amount,detail));
    }
    private static int count(Connection c,String table)throws Exception {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getInt(1);}
    }
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);checks++;}
}
