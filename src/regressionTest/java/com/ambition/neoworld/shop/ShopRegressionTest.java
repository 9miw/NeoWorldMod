package com.ambition.neoworld.shop;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.trade.TradeInventoryPlan.Stack;
import org.h2.jdbcx.JdbcDataSource;
import java.sql.*;
import java.util.*;

public final class ShopRegressionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        ShopWalletCheckpointRegressionTest.run();
        check(ShopRules.validPrices(100, 25), "normal prices");
        check(ShopRules.validPrices(100, 0) && ShopRules.validPrices(0, 25), "one-sided shops");
        check(!ShopRules.validPrices(0,0) && !ShopRules.validPrices(-1,1), "invalid prices");
        check(!ShopRules.validPrices(100,100) && !ShopRules.validPrices(100,101), "profitable round trips rejected");
        check(ShopRules.total(100,64) == 6400, "quantity pricing");
        check(ShopRules.total(Long.MAX_VALUE,1) == Long.MAX_VALUE, "maximum price");
        rejects(() -> ShopRules.total(Long.MAX_VALUE,2), "overflow rejected");
        rejects(() -> ShopRules.total(1,0), "zero quantity rejected");
        rejects(() -> ShopRules.total(1,65), "oversized quantity rejected");
        check(ShopRules.code(1).equals("1") && ShopRules.code(1001).equals("1001"), "shop IDs displayed without prefix or padding");
        check(ShopRules.balance(100,100,false)==0, "exact purchase balance");
        check(ShopRules.balance(100,25,true)==125, "sale credits balance");
        rejects(() -> ShopRules.balance(99,100,false), "insufficient funds rejected");
        rejects(() -> ShopRules.balance(Long.MAX_VALUE,1,true), "sale credit overflow rejected");
        currencies();
        guiPolicy();
        views();
        database(Config.DatabaseType.H2, "");
        database(Config.DatabaseType.MARIADB, ";MODE=MariaDB");
        recovery(Config.DatabaseType.H2, "");
        recovery(Config.DatabaseType.MARIADB, ";MODE=MariaDB");
        inventory();
        System.out.println("Passed " + checks + " shop regression checks.");
    }

    private static void guiPolicy() {
        check(ShopCurrency.values().length==2, "only two shop currencies remain");
        check(ShopCurrency.SILVER.id()==0 && ShopCurrency.DIAMOND.id()==2, "surviving wire IDs remain stable");
        rejects(() -> ShopCurrency.fromId(1), "retired currency ID must not be remapped");
        for(var currency:ShopCurrency.values())check(currency.enabled(), "every offered currency is active");
    }
    private static void views() {
        check(ShopView.ALL.includes(10, 0) && ShopView.ALL.includes(0, 5), "all shows both offer directions");
        check(ShopView.STORE_SELLS.includes(10, 0) && !ShopView.STORE_SELLS.includes(0, 5), "store selling filter");
        check(ShopView.STORE_BUYS.includes(0, 5) && !ShopView.STORE_BUYS.includes(10, 0), "store buying filter");
        check(ShopView.STORE_SELLS.includes(10, 5) && ShopView.STORE_BUYS.includes(10, 5), "two-sided offers appear in both tabs");
        check(!ShopView.ALL.includes(0, 0), "unavailable offers excluded");
        check(ShopView.fromId(0) == ShopView.ALL && ShopView.fromId(2) == ShopView.STORE_BUYS, "stable synchronized view IDs");
        rejects(() -> ShopView.fromId(3), "invalid view rejected");
    }

    private static void currencies() {
        for (ShopCurrency currency : ShopCurrency.values()) {
            check(ShopCurrency.fromId(currency.id()) == currency, "currency menu ID round trip");
            var wallet = new PlayerData(1,0,100,200,300);
            long before = currency.balance(wallet);
            currency.setBalance(wallet,ShopRules.balance(before,7,false));
            check(currency.balance(wallet)==before-7,"purchase debits chosen currency");
            for (ShopCurrency other : ShopCurrency.values()) if (other != currency)
                check(other.balance(wallet)==switch(other) { case SILVER -> 100; case DIAMOND -> 300; },"purchase preserves other currencies");
            currency.setBalance(wallet,ShopRules.balance(currency.balance(wallet),12,true));
            check(currency.balance(wallet)==before+5,"sale credits chosen currency");
            currency.setBalance(wallet,before);
            check(wallet.getSilver()==100 && wallet.getLegacyRetiredBalance()==200 && wallet.getDiamond()==300,"rollback restores exact wallet snapshot");
            currency.setBalance(wallet,Long.MAX_VALUE);
            rejects(() -> ShopRules.balance(currency.balance(wallet),1,true),"currency credit overflow");
            check(currency.balance(wallet)==Long.MAX_VALUE,"overflow preserves balance");
            currency.setBalance(wallet,0);
            rejects(() -> ShopRules.balance(currency.balance(wallet),1,false),"cannot use other currencies to cover shortage");
            rejects(() -> currency.setBalance(wallet,-1),"negative currency assignment rejected");
            check(currency.balance(wallet)==0,"invalid assignment preserves balance");
            check(ShopRules.conflicts(10,1,currency,100,20,currency),"same-currency profitable purchase rejected");
            for (ShopCurrency other : ShopCurrency.values()) if (other != currency)
                check(!ShopRules.conflicts(10,1,currency,100,20,other),"different denominations not compared numerically");
        }
        rejects(() -> ShopCurrency.fromId(-1),"negative currency ID rejected");
        rejects(() -> ShopCurrency.fromId(3),"unknown currency ID rejected");
        check(java.util.Arrays.stream(ShopCurrency.values()).map(ShopCurrency::glyph).distinct().count()==2,"distinct currency glyphs");
    }

    private static void inventory() {
        var unit = new Stack<>("diamond",1,64);
        var empty = empty();
        var bought = plan(empty,unit,64,false);
        check(bought.getFirst().count()==64, "purchase inserts full stack");
        check(empty.stream().allMatch(s -> s.count()==0), "plan preserves original inventory");
        var full = empty(); for (int i=0;i<36;i++) full.set(i,new Stack<>("stone",64,64));
        rejects(() -> plan(full,unit,1,false), "full inventory rejected");
        check(full.getFirst().count()==64, "failure preserves inventory");
        var partial=empty(); partial.set(0,new Stack<>("diamond",63,64));
        var merged=plan(partial,unit,16,false);
        check(merged.get(0).count()==64 && merged.get(1).count()==15,"merge and spill");
        var sword = new Stack<>("sword",1,1);
        var swords=plan(empty(),sword,16,false);
        check(swords.stream().filter(s -> s.count()>0).count()==16 && swords.get(0).count()==1,"unstackable items span batches");
        rejects(() -> plan(empty(),sword,64,false),"unstackable capacity checked");
        var sale=empty(); for(int i=0;i<36;i++) sale.set(i,new Stack<>("diamond",1,64));
        check(plan(sale,unit,36,true).stream().allMatch(s -> s.count()==0),"sale spans all 36 slots and batches");
        rejects(() -> plan(sale,unit,37,true),"insufficient goods rejected");
        check(sale.get(0).count()==1,"insufficient sale preserves input");
        var named=new Stack<>("diamond:Special",1,64); var names=empty(); names.set(0,named);
        rejects(() -> plan(names,unit,1,true),"component mismatch rejected");
        check(plan(names,named,1,true).get(0).count()==0,"matching components accepted");
        rejects(() -> plan(empty(),unit,0,false),"invalid inventory quantity rejected");
    }

    private static void database(Config.DatabaseType type, String mode) throws Exception {
        JdbcDataSource ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:shop_" + type + mode + ";DB_CLOSE_DELAY=-1");
        try (Connection c = ds.getConnection()) {
            ShopRepository.createSchema(c,type,"test_"); ShopRepository.createSchema(c,type,"test_");
            long first, second;
            try (PreparedStatement s = c.prepareStatement("INSERT INTO test_shop_stores(name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                s.setString(1,"ร้านอาวุธ"); s.executeUpdate(); try (ResultSet r=s.getGeneratedKeys()) { check(r.next(),"generated shop key"); first=r.getLong(1); }
                s.setString(1,"ร้านยา"); s.executeUpdate(); try (ResultSet r=s.getGeneratedKeys()) { check(r.next(),"second generated shop key"); second=r.getLong(1); }
            }
            check(first>0 && second>first, "distinct automatic IDs");
            try (PreparedStatement s = c.prepareStatement("INSERT INTO test_shop_offers(shop_id,item_nbt,buy_price,sell_price,category) VALUES (?,?,?,?,?)")) {
                s.setLong(1,first); s.setBytes(2,new byte[]{1}); s.setLong(3,100); s.setLong(4,25); s.setString(5,"อาวุธ"); s.executeUpdate();
            }
            try (Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT COUNT(*) FROM test_shop_offers WHERE shop_id="+second)) { r.next(); check(r.getInt(1)==0,"shops have isolated catalogs"); }
            try (Statement s=c.createStatement()) {
                s.executeUpdate("INSERT INTO test_shop_transactions(id,player_id,shop_id,offer_id,action,quantity,total_silver,item_nbt) VALUES ('legacy','player',1,1,'BUY',16,1600,X'01')");
                // Simulate interrupted migration after its first auto-committed DDL statement.
                s.executeUpdate("ALTER TABLE test_shop_offers ADD COLUMN currency VARCHAR(16) NOT NULL DEFAULT 'SILVER'");
            }
            ShopRepository.addCurrencies(c,"test_");
            ShopRepository.addCurrencies(c,"test_");
            try (Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT currency,buy_price,sell_price FROM test_shop_offers")) {
                check(r.next() && r.getString(1).equals("SILVER") && r.getLong(2)==100 && r.getLong(3)==25,"v8 offer prices preserved as Silver");
            }
            try (Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT currency,total_amount,total_silver FROM test_shop_transactions WHERE id='legacy'")) {
                check(r.next() && r.getString(1).equals("SILVER") && r.getLong(2)==1600 && r.getLong(3)==1600,"legacy audit amount preserved");
            }
            for (ShopCurrency currency : ShopCurrency.values()) {
                try (PreparedStatement s=c.prepareStatement("INSERT INTO test_shop_offers(shop_id,item_nbt,buy_price,sell_price,category,currency) VALUES (?,X'01',10,1,'test',?)")) {
                    s.setLong(1,second); s.setString(2,currency.name()); s.executeUpdate();
                }
                try (PreparedStatement s=c.prepareStatement("INSERT INTO test_shop_transactions(id,player_id,shop_id,offer_id,action,quantity,total_silver,item_nbt,currency,total_amount) VALUES (?,'player',1,1,'BUY',1,?,X'01',?,10)")) {
                    s.setString(1,currency.name()); s.setLong(2,currency==ShopCurrency.SILVER ? 10 : 0); s.setString(3,currency.name()); s.executeUpdate();
                }
            }
            ShopRepository.addCurrencies(c,"test_");
            try (Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT currency,total_amount,total_silver FROM test_shop_transactions WHERE id<>'legacy'")) {
                int count=0;
                while (r.next()) {
                    ShopCurrency currency=ShopCurrency.valueOf(r.getString(1));
                    check(r.getLong(2)==10 && r.getLong(3)==(currency==ShopCurrency.SILVER ? 10 : 0),"migration retry preserves currency audit amounts"); count++;
                }
                check(count==2,"all active currency audit records retained");
            }
            try (Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT COUNT(DISTINCT currency) FROM test_shop_offers WHERE shop_id="+second)) {
                check(r.next() && r.getInt(1)==2,"store supports two active currencies");
            }
            c.setAutoCommit(false);
            try (Statement s=c.createStatement()) { s.executeUpdate("INSERT INTO test_shop_transactions(id,player_id,shop_id,offer_id,action,quantity,total_silver,item_nbt) VALUES ('test','player',1,1,'BUY',16,1600,X'01')"); }
            c.rollback();
            try (Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT COUNT(*) FROM test_shop_transactions")) { r.next(); check(r.getInt(1)==3,"audit rollback preserves prior history"); }

            // Migration 13 test: add recovery schema
            ShopRepository.addRecoverySchema(c, type, "test_");
            ShopRepository.addRecoverySchema(c, type, "test_"); // idempotent retry
            try (Statement s=c.createStatement(); ResultSet r=s.executeQuery("SELECT status, tx_seq, balance_before, balance_after FROM test_shop_transactions WHERE id='legacy'")) {
                check(r.next(),"legacy transaction has recovery defaults");
                check(r.getString(1).equals("COMMITTED"),"legacy transaction status defaults to COMMITTED");
                check(r.getLong(2)==0L,"legacy tx_seq defaults to 0");
                check(r.getLong(3)==-1L && r.getLong(4)==-1L,"legacy balances default to -1");
            }

            // Test targeted offer query
            try (PreparedStatement s=c.prepareStatement("SELECT buy_price, sell_price, currency FROM test_shop_offers WHERE shop_id=? AND id=?")) {
                s.setLong(1, first);
                s.setLong(2, 1);
                try (ResultSet r=s.executeQuery()) {
                    check(r.next() && r.getLong(1)==100 && r.getLong(2)==25 && r.getString(3).equals("SILVER"),"targeted offer query returns correct row");
                }
                s.setLong(1, second); // Wrong shop_id
                s.setLong(2, 1);
                try (ResultSet r=s.executeQuery()) {
                    check(!r.next(),"targeted offer query rejects wrong shop_id");
                }
            }
        }
    }

    private static void recovery(Config.DatabaseType type, String mode) throws Exception {
        JdbcDataSource ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:recovery_" + type + mode + ";DB_CLOSE_DELAY=-1");
        try (Connection c = ds.getConnection()) {
            ShopRepository.createSchema(c,type,"rec_"); ShopRepository.addCurrencies(c,"rec_"); ShopRepository.addRecoverySchema(c,type,"rec_");
            UUID first=UUID.randomUUID(), second=UUID.randomUUID();
            try (var s=c.prepareStatement("INSERT INTO rec_shop_transactions (id,player_id,shop_id,offer_id,action,quantity,total_silver,item_nbt,currency,total_amount,status,tx_seq,balance_before,balance_after) VALUES (?,?,1,1,'BUY',1,25,X'01','SILVER',25,?,1,100,75)")) {
                for (String id : List.of("pending1","pending2","other","done")) {
                    s.setString(1,id); s.setString(2,(id.equals("other") ? second : first).toString());
                    s.setString(3,id.equals("done") ? "COMMITTED" : "PENDING"); s.executeUpdate();
                }
            }
            check(ShopRepository.markLegacyPendingForReview(c,"rec_shop_transactions",first)==2,"legacy pending rows preserved for review");
            check(ShopRepository.markLegacyPendingForReview(c,"rec_shop_transactions",first)==0,"legacy review is idempotent");
            check(ShopRepository.pendingTransactions(c,"rec_shop_transactions",first).isEmpty(),"reviewed history is no longer an active pending transaction");
            check(ShopRepository.pendingTransactions(c,"rec_shop_transactions",second).size()==1,"review does not touch another player");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT status,balance_before,balance_after,item_nbt FROM rec_shop_transactions WHERE id='pending1'")) {
                check(r.next() && r.getString(1).equals("NEEDS_REVIEW"),"old sequence is not guessed to be committed or rolled back");
                check(r.getLong(2)==100 && r.getLong(3)==75 && r.getBytes(4)[0]==1,"review preserves money and item evidence");
            }
            try(var s=c.createStatement();var r=s.executeQuery("SELECT status FROM rec_shop_transactions WHERE id='done'")) {
                check(r.next() && r.getString(1).equals("COMMITTED"),"completed historical transaction stays committed");
            }
        }
    }

    private static List<Stack<String>> plan(List<Stack<String>> source, Stack<String> unit, int quantity, boolean sell) { return ShopInventoryPlan.plan(source,unit,quantity,sell,String::equals); }
    private static ArrayList<Stack<String>> empty() { var list=new ArrayList<Stack<String>>(); for(int i=0;i<36;i++) list.add(new Stack<>(null,0,64)); return list; }
    private static void check(boolean ok,String name) { if(!ok) throw new AssertionError(name); checks++; }
    private static void rejects(Runnable action,String name) { try { action.run(); } catch(IllegalArgumentException | ArithmeticException e) { checks++; return; } throw new AssertionError(name); }
}
