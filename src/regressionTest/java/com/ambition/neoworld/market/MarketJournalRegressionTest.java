package com.ambition.neoworld.market;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.market.storage.MarketPlayerJournal;
import com.ambition.neoworld.market.storage.MarketSql;
import net.minecraft.nbt.*;
import org.h2.jdbcx.JdbcDataSource;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;

/** Executes the production coordinator with file-backed SQL and independently saved player NBT. */
public final class MarketJournalRegressionTest {
    private static int checks;
    private MarketJournalRegressionTest() {}

    public static int run() throws Exception {
        checks = 0;
        Path root = Files.createDirectories(Path.of("build", "market-journal-regression")).toAbsolutePath();
        for (String type : new String[]{"CREATE_LISTING", "PURCHASE", "CLAIM_ALL", "CLAIM_SALE", "SHOP_BUY", "SHOP_SELL"}) {
            for (boolean savedBeforeFailure : new boolean[]{false, true}) {
                Path dir = Files.createTempDirectory(root, "crash-");
                var ds = database(dir);
                var journal = new MarketPlayerJournal(ds::getConnection, "nw_market_player_journal");
                UUID owner = UUID.randomUUID(), op = UUID.randomUUID();
                var player = new Player(dir.resolve("player.dat")); player.save(UUID.randomUUID());
                player.failSave = !savedBeforeFailure;
                player.failAfterSave = savedBeforeFailure;
                try {
                    journal.execute(op, owner, type, player, c -> change(c, player, type));
                    throw new AssertionError("save fault was ignored");
                } catch (IOException expected) { checks++; }
                check(journal.find(op) != null && !journal.find(op).applied(), "SQL and pending journal survive save fault");
                check(rows(ds) == 1, "business mutation committed with journal");
                Player restarted = Player.load(player.file);
                check(restarted.silver == (savedBeforeFailure ? player.silver : 100), "player disk is independent of SQL commit");
                journal = new MarketPlayerJournal(database(dir)::getConnection, "nw_market_player_journal");
                journal.recover(owner, restarted);
                check(restarted.silver == player.silver && restarted.gold == player.gold && restarted.diamond == player.diamond && restarted.items == player.items, "recovery restores complete after-image");
                check(journal.find(op).applied(), "verified save acknowledges journal");
                restarted.silver += 7;
                journal.execute(op, owner, type, restarted, c -> { throw new AssertionError("duplicate action executed"); });
                check(restarted.silver == player.silver + 7, "duplicate does not overwrite newer player state");
                journal.recover(owner, restarted);
                check(restarted.silver == player.silver + 7 && rows(ds) == 1, "recovery is idempotent");
                try {
                    journal.execute(op, UUID.randomUUID(), type, restarted, c -> change(c, restarted, type));
                    throw new AssertionError("foreign operation accepted");
                } catch (SQLException expected) { checks++; }
                try {
                    journal.execute(op, owner, "OTHER", restarted, c -> change(c, restarted, type));
                    throw new AssertionError("wrong operation type accepted");
                } catch (SQLException expected) { checks++; }
            }
        }
        rollback(root);
        ambiguousCommit(root);
        unavailableAfterFailure(root);
        ownerAndWorldRecovery(root);
        cancelIdentity(root);
        checkpointLifecycle();
        mixedSystems(root);
        shopAuditAtomicity(root);
        snapshotRetention(root);
        System.out.println("Passed " + checks + " market journal fault/restart checks (production coordinator; player adapter simulated).");
        return checks;
    }

    private static void snapshotRetention(Path root) throws Exception {
        for(boolean maria:new boolean[]{false,true}) {
            Path dir=Files.createTempDirectory(root,"retention-");
            var ds=database(dir,maria ? Config.DatabaseType.MARIADB : Config.DatabaseType.H2,maria ? ";MODE=MariaDB" : "");
            var journal=new MarketPlayerJournal(ds::getConnection,"nw_market_player_journal");
            var player=new Player(dir.resolve("player.dat")); player.save(UUID.randomUUID());
            UUID owner=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID(), third=UUID.randomUUID();
            var firstResult=journal.execute(first,owner,"PURCHASE",player,c -> changeWithId(c,player,"PURCHASE",1));
            byte[] historical=journal.find(first).state();
            journal.execute(second,owner,"SHOP_BUY",player,c -> changeWithId(c,player,"SHOP_BUY",2));
            check(journal.find(first).state().length==0,"older applied snapshot is compacted after a newer verified checkpoint");
            check(journal.find(first).result().equals(firstResult),"compaction retains the original operation result");
            check(journal.find(second).state().length>0,"latest applied snapshot remains available for owner recovery");
            journal.execute(first,owner,"PURCHASE",player,c -> { throw new AssertionError("compacted operation executed twice"); });
            check(player.silver==90 && player.diamond==290,"compacted receipt still prevents duplicate effects");
            player.failSave=true;
            try {
                journal.execute(third,owner,"PURCHASE",player,c -> changeWithId(c,player,"PURCHASE",3));
                throw new AssertionError("pending snapshot fault ignored");
            } catch(IOException expected) { checks++; }
            journal.compactApplied(owner);
            check(!journal.find(third).applied() && journal.find(third).state().length>0 && journal.find(second).state().length>0,
                    "compaction keeps pending snapshot and preceding applied recovery point");
            var otherWorld=new MarketPlayerJournal(ds::getConnection,"nw_market_player_journal","another-world");
            var other=new Player(dir.resolve("other.dat")); other.save(UUID.randomUUID());
            UUID otherOperation=UUID.randomUUID(), otherOwner=UUID.randomUUID(), otherPlayerOperation=UUID.randomUUID();
            otherWorld.execute(otherOperation,owner,"PURCHASE",other,c -> changeWithId(c,other,"PURCHASE",4));
            journal.execute(otherPlayerOperation,otherOwner,"PURCHASE",other,c -> changeWithId(c,other,"PURCHASE",5));
            var restarted=Player.load(player.file);journal.recover(owner,restarted);
            check(restarted.silver==80 && restarted.diamond==290 && journal.find(third).applied(),"pending operation recovers normally after compaction");
            check(journal.find(second).state().length==0 && journal.find(third).state().length>0,"recovered checkpoint supersedes the preceding full snapshot");
            restarted.checkpoint="stale-level-dat"; restarted.silver=0;
            journal.recoverOwner(owner,restarted);
            check(restarted.silver==80 && restarted.diamond==290,"owner recovery uses the retained latest snapshot");
            check(journal.find(otherOperation).state().length>0 && journal.find(otherPlayerOperation).state().length>0,
                    "compaction never crosses player or world scope");
            // Simulate historical applied BLOBs from before the compaction release.
            try(var c=ds.getConnection();var q=c.prepareStatement("UPDATE nw_market_player_journal SET player_state=? WHERE operation_id=?")) {
                q.setBytes(1,historical);q.setString(2,first.toString());q.executeUpdate();
            }
            var batch=MarketPlayerJournal.compactBatch(ds::getConnection,"nw_market_player_journal",1);
            check(batch.groups()==1 && batch.snapshots()==1 && journal.find(first).state().length==0,"bounded startup backfill compacts historical payloads");
            check(MarketPlayerJournal.compactBatch(ds::getConnection,"nw_market_player_journal",1).groups()==0,"backfill is idempotent and terminates");
            check(journal.find(third).state().length>0 && rows(ds)==5,"backfill retains latest data and never repeats business effects");
        }
    }

    private static void rollback(Path root) throws Exception {
        Path dir = Files.createTempDirectory(root, "rollback-"); var ds = database(dir);
        var journal = new MarketPlayerJournal(ds::getConnection, "nw_market_player_journal");
        var player = new Player(dir.resolve("player.dat")); player.save(UUID.randomUUID());
        UUID owner = UUID.randomUUID(), op = UUID.randomUUID();
        try {
            journal.execute(op, owner, "PURCHASE", player, c -> {
                change(c, player, "PURCHASE");
                // Borrowed connections cannot commit the outer transaction.
                try (var borrowed = MarketSql.connection()) { borrowed.commit(); }
                return new MarketPlayerJournal.Result("SUCCESS", 1);
            });
            throw new AssertionError("nested commit accepted");
        } catch (SQLException expected) { checks++; }
        check(rows(ds) == 0 && journal.find(op) == null, "SQL failure rolls back business and journal together");
        check(player.silver == 100 && player.items == 1, "SQL failure restores live player before-image");
        var rejected = journal.execute(UUID.randomUUID(), owner, "PURCHASE", player, c -> {
            change(c, player, "PURCHASE"); return new MarketPlayerJournal.Result("INSUFFICIENT_FUNDS", 0);
        });
        check(rejected.code().equals("INSUFFICIENT_FUNDS") && rows(ds) == 0 && player.silver == 100,
                "business rejection rolls back partial writes and live state");
    }

    private static void ambiguousCommit(Path root) throws Exception {
        Path dir = Files.createTempDirectory(root, "ambiguous-"); var ds = database(dir);
        boolean[] first = {true};
        MarketSql.Connections connections = () -> {
            Connection real = ds.getConnection();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        try {
                            Object result = method.invoke(real, args);
                            if (method.getName().equals("commit") && first[0]) {
                                first[0] = false; throw new SQLException("lost commit acknowledgement");
                            }
                            return result;
                        } catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        };
        var journal = new MarketPlayerJournal(connections, "nw_market_player_journal");
        var player = new Player(dir.resolve("player.dat")); player.save(UUID.randomUUID());
        UUID operation = UUID.randomUUID();
        journal.execute(operation, UUID.randomUUID(), "PURCHASE", player, c -> change(c, player, "PURCHASE"));
        check(rows(ds) == 1 && player.silver == 90 && journal.find(operation).applied(),
                "ambiguous commit recovers durable evidence instead of refunding a committed purchase");
    }

    private static void unavailableAfterFailure(Path root) throws Exception {
        Path dir = Files.createTempDirectory(root, "offline-"); var ds = database(dir);
        boolean[] offline = {false};
        MarketSql.Connections connections = () -> {
            if (offline[0]) throw new SQLException("database unavailable during outcome query");
            var real = ds.getConnection();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commit")) {
                            offline[0] = true; throw new SQLException("connection lost before commit");
                        }
                        try { return method.invoke(real, args); }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        };
        var player = new Player(dir.resolve("player.dat")); player.save(UUID.randomUUID());
        var journal = new MarketPlayerJournal(connections, "nw_market_player_journal");
        try {
            journal.execute(UUID.randomUUID(), UUID.randomUUID(), "PURCHASE", player, c -> change(c, player, "PURCHASE"));
            throw new AssertionError("offline failure was ignored");
        } catch (SQLException expected) { checks++; }
        check(player.silver == 100 && player.items == 1 && rows(ds) == 0,
                "outcome lookup failure restores before-image before disconnect can save it");
    }

    private static void ownerAndWorldRecovery(Path root) throws Exception {
        Path dir = Files.createTempDirectory(root, "owner-"); var ds = database(dir);
        UUID owner = UUID.randomUUID(), operation = UUID.randomUUID();
        var player = new Player(dir.resolve("player.dat")); player.save(UUID.randomUUID());
        byte[] oldLevelDat = player.capture();
        var journal = new MarketPlayerJournal(ds::getConnection, "nw_market_player_journal", "world-a");
        journal.execute(operation, owner, "PURCHASE", player, c -> change(c, player, "PURCHASE"));
        player.restore(oldLevelDat); // Integrated owner loaded stale level.dat despite acknowledged .dat checkpoint.
        journal.recoverOwner(owner, player);
        check(player.silver == 90 && player.hasCheckpoint(operation), "singleplayer owner repairs stale acknowledged load checkpoint");
        player.silver = 87; // Later ordinary progress with the same checkpoint must remain intact.
        player.items = 0; // Death drops emptied inventory, but NeoForge retained the checkpoint.
        journal.recoverOwner(owner, player);
        check(player.silver == 87 && player.items == 0, "matching owner checkpoint preserves subsequent gameplay/death drops");
        var otherWorld = new MarketPlayerJournal(ds::getConnection, "nw_market_player_journal", "world-b");
        otherWorld.recoverOwner(owner, player);
        check(player.silver == 87, "another world never replays a player snapshot");
        try {
            otherWorld.execute(operation, owner, "PURCHASE", player, c -> change(c, player, "PURCHASE"));
            throw new AssertionError("cross-world operation accepted");
        } catch (SQLException expected) { checks++; }
    }

    private static void shopAuditAtomicity(Path root) throws Exception {
        for (var currency : new com.ambition.neoworld.shop.ShopCurrency[]{com.ambition.neoworld.shop.ShopCurrency.SILVER,
                com.ambition.neoworld.shop.ShopCurrency.DIAMOND}) {
            for (boolean sell : new boolean[]{false,true}) for (int fault=0; fault<3; fault++) {
                Path dir=Files.createTempDirectory(root,"shop-audit-"); var ds=database(dir);
                com.ambition.neoworld.database.NeoWorldDatabase.runMigrations(ds,Config.DatabaseType.H2,"nw_");
                var journal=new MarketPlayerJournal(ds::getConnection,"nw_market_player_journal");
                var player=new Player(dir.resolve("player.dat")); player.save(UUID.randomUUID());
                UUID owner=UUID.randomUUID(), operation=UUID.randomUUID();
                int failure=fault;
                long before=currency==com.ambition.neoworld.shop.ShopCurrency.SILVER ? 100 : 300;
                long after=before+(sell ? 30 : -30);
                player.failSave=fault==1;
                try {
                    journal.execute(operation,owner,sell ? "SHOP_SELL" : "SHOP_BUY",player,c -> {
                        com.ambition.neoworld.shop.ShopRepository.recordCommitted(c,operation,owner,1,2,sell,3,30,currency,
                                new byte[]{1,2,3},before,after);
                        if(currency==com.ambition.neoworld.shop.ShopCurrency.SILVER) player.silver=after; else player.diamond=after;
                        player.items+=sell ? -1 : 3;
                        if(failure==0) throw new SQLException("fault after audit and player mutation, before commit");
                        return new MarketPlayerJournal.Result("SUCCESS",2);
                    });
                    check(fault==2,"normal shop transaction reaches success");
                } catch(SQLException | IOException expected) { check(fault!=2,"injected shop fault propagates"); }
                if(fault==0) {
                    check(journal.find(operation)==null && player.silver==100 && player.diamond==300 && player.items==1,
                            "failed shop SQL restores all player currencies/items and leaves no journal");
                    try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM nw_shop_transactions")) {
                        check(r.next() && r.getInt(1)==0,"failed shop transaction leaves no audit row");
                    }
                    continue;
                }
                var restarted=Player.load(player.file); journal.recover(owner,restarted);
                check((currency==com.ambition.neoworld.shop.ShopCurrency.SILVER ? restarted.silver : restarted.diamond)==after
                        && restarted.gold==200 && restarted.items==(sell ? 0 : 4),"shop save recovery restores selected currency and item transfer");
                try(var c=ds.getConnection();var s=c.prepareStatement("SELECT status,currency,total_amount,balance_before,balance_after,item_nbt FROM nw_shop_transactions WHERE id=?")) {
                    s.setString(1,operation.toString());
                    try(var r=s.executeQuery()) {
                        check(r.next() && r.getString(1).equals("COMMITTED") && r.getString(2).equals(currency.name())
                                && r.getLong(3)==30 && r.getLong(4)==before && r.getLong(5)==after && r.getBytes(6).length==3,
                                "real shop audit and journal commit together with original currency/item evidence");
                    }
                }
                journal.execute(operation,owner,sell ? "SHOP_SELL" : "SHOP_BUY",restarted,
                        c -> { throw new AssertionError("shop duplicate ran again"); });
                check((currency==com.ambition.neoworld.shop.ShopCurrency.SILVER ? restarted.silver : restarted.diamond)==after,
                        "duplicate shop request does not charge or grant again");
            }
        }
    }

    private static void mixedSystems(Path root) throws Exception {
        for (boolean shopFirst : new boolean[]{true,false}) {
            Path dir=Files.createTempDirectory(root,"mixed-"); var ds=database(dir);
            var journal=new MarketPlayerJournal(ds::getConnection,"nw_market_player_journal");
            var player=new Player(dir.resolve("player.dat")); player.save(UUID.randomUUID());
            UUID owner=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID();
            String firstType=shopFirst ? "SHOP_BUY" : "PURCHASE", secondType=shopFirst ? "PURCHASE" : "SHOP_BUY";
            journal.execute(first,owner,firstType,player,c -> changeWithId(c,player,firstType,1));
            player.failSave=true;
            try {
                journal.execute(second,owner,secondType,player,c -> changeWithId(c,player,secondType,2));
                throw new AssertionError("mixed-system save fault ignored");
            } catch(IOException expected) { checks++; }
            var restarted=Player.load(player.file);
            journal.recover(owner,restarted);
            check(restarted.silver==90 && restarted.diamond==290 && restarted.gold==200 && restarted.items==2,
                    "mixed market/shop recovery preserves both preceding operations and currencies");
            journal.execute(first,owner,firstType,restarted,c -> { throw new AssertionError("old system replayed"); });
            check(restarted.silver==90 && restarted.diamond==290 && rows(ds)==2,
                    "retry from the earlier system never overwrites the newer system checkpoint");
            journal.recoverOwner(owner,restarted);
            check(restarted.silver==90 && restarted.diamond==290,"owner recovery follows the shared newest operation");
        }
    }

    private static MarketPlayerJournal.Result changeWithId(Connection c, Player player, String type, int id) throws Exception {
        MarketSql.transaction(nested -> {
            try(var s=nested.prepareStatement("INSERT INTO business(id) VALUES (?)")) { s.setInt(1,id); s.executeUpdate(); }
            return null;
        });
        if(type.equals("SHOP_BUY")) { player.diamond-=10; player.items++; }
        else player.silver-=10;
        return new MarketPlayerJournal.Result("SUCCESS",id);
    }

    private static void checkpointLifecycle() throws Exception {
        UUID operation = UUID.randomUUID();
        var persistent = new CompoundTag();
        com.ambition.neoworld.market.storage.MarketCheckpoint.write(persistent, operation);
        var clone = new CompoundTag();
        // The installed NeoForge restoreFrom patch copies this exact compound for both clone variants.
        clone.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,
                persistent.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).copy());
        check(com.ambition.neoworld.market.storage.MarketCheckpoint.matches(clone, operation),
                "market checkpoint is stored in the NeoForge clone-retained compound");
        check(!com.ambition.neoworld.market.storage.MarketCheckpoint.matches(clone, UUID.randomUUID()),
                "another operation does not match the retained checkpoint");
        var save = new CompoundTag(); save.put("NeoForgeData", clone);
        var bytes = new ByteArrayOutputStream(); NbtIo.writeCompressed(save, bytes);
        var restored = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.create(1024 * 1024));
        check(com.ambition.neoworld.market.storage.MarketCheckpoint.inPlayerSave(restored, operation),
                "disk verifier finds the checkpoint after NBT round trip");
        check(!com.ambition.neoworld.market.storage.MarketCheckpoint.inPlayerSave(new CompoundTag(), operation),
                "missing disk checkpoint is rejected");
    }

    private static void cancelIdentity(Path root) throws Exception {
        Path dir = Files.createTempDirectory(root, "cancel-"); var ds = database(dir);
        com.ambition.neoworld.database.NeoWorldDatabase.runMigrations(ds, Config.DatabaseType.H2, "nw_");
        UUID owner = UUID.randomUUID(), operation = UUID.randomUUID();
        MarketSql.atomic(ds::getConnection, c -> {
            com.ambition.neoworld.market.storage.JdbcMarketRepository.recordOperation(c, operation, "CANCEL", owner,
                    1, "COMMITTED", "SUCCESS", "", 1, 1);
            return null;
        });
        MarketSql.atomic(ds::getConnection, c -> {
            check(com.ambition.neoworld.market.storage.JdbcMarketRepository.cancel(operation, 1, owner, 2, null) == MarketResult.SUCCESS,
                    "matching cancel retry is idempotent");
            check(com.ambition.neoworld.market.storage.JdbcMarketRepository.cancel(operation, 1, UUID.randomUUID(), 2, null) == MarketResult.DATABASE_ERROR,
                    "cancel rejects another owner's operation");
            check(com.ambition.neoworld.market.storage.JdbcMarketRepository.cancel(operation, 2, owner, 2, null) == MarketResult.DATABASE_ERROR,
                    "cancel rejects mismatched listing");
            try (var s = c.prepareStatement("UPDATE nw_market_operations SET operation_type='PURCHASE' WHERE operation_id=?")) {
                s.setString(1, operation.toString()); s.executeUpdate();
            }
            check(com.ambition.neoworld.market.storage.JdbcMarketRepository.cancel(operation, 1, owner, 2, null) == MarketResult.DATABASE_ERROR,
                    "cancel rejects another operation type");
            return null;
        });
    }

    private static MarketPlayerJournal.Result change(Connection c, Player player, String type) throws Exception {
        // Uses the same market transaction scope as production repository calls.
        MarketSql.transaction(nested -> {
            try (var borrowed = MarketSql.connection(); var s = borrowed.createStatement()) {
                s.executeUpdate("INSERT INTO business(id) VALUES (1)");
            }
            return null;
        });
        if (type.equals("CREATE_LISTING")) player.items--;
        else if (type.equals("PURCHASE")) player.silver -= 10;
        else if (type.equals("SHOP_BUY")) { player.diamond -= 10; player.items++; }
        else if (type.equals("SHOP_SELL")) { player.diamond += 5; player.items--; }
        else { player.silver += 25; player.items += 2; }
        return new MarketPlayerJournal.Result("SUCCESS", 1);
    }

    private static JdbcDataSource database(Path dir) throws Exception {
        return database(dir,Config.DatabaseType.H2,"");
    }

    private static JdbcDataSource database(Path dir,Config.DatabaseType type,String mode) throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:file:" + dir.resolve("db").toString().replace('\\', '/') + ";WRITE_DELAY=0" + mode);
        try (var c = ds.getConnection(); var s = c.createStatement()) {
            MarketPlayerJournal.createSchema(c, type, "nw_");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS business(id INTEGER PRIMARY KEY)");
        }
        return ds;
    }

    private static int rows(JdbcDataSource ds) throws Exception {
        try (var c = ds.getConnection(); var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM business")) {
            r.next(); return r.getInt(1);
        }
    }

    private static final class Player implements MarketPlayerJournal.PlayerState {
        final Path file; long silver = 100, gold = 200, diamond = 300; int items = 1; String checkpoint = "";
        boolean failSave, failAfterSave;
        Player(Path file) { this.file = file; }
        static Player load(Path file) throws Exception {
            var p = new Player(file); p.restore(Files.readAllBytes(file)); return p;
        }
        public byte[] capture() throws Exception {
            var tag = new CompoundTag(); tag.putLong("silver", silver); tag.putLong("gold",gold); tag.putLong("diamond",diamond); tag.putInt("items", items);
            tag.putString("checkpoint", checkpoint);
            var out = new ByteArrayOutputStream(); NbtIo.writeCompressed(tag, out); return out.toByteArray();
        }
        public void restore(byte[] bytes) throws Exception {
            var tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(1024 * 1024));
            silver = tag.getLong("silver"); gold=tag.getLong("gold"); diamond=tag.getLong("diamond"); items = tag.getInt("items"); checkpoint = tag.getString("checkpoint");
        }
        public boolean hasCheckpoint(UUID operation) throws Exception {
            return checkpoint.equals(operation.toString());
        }
        public void save(UUID operation) throws Exception {
            if (failSave) throw new IOException("simulated failure before player save");
            checkpoint = operation.toString(); Files.write(file, capture());
            if (failAfterSave) throw new IOException("simulated stop after checkpoint, before SQL acknowledgement");
        }
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
}
