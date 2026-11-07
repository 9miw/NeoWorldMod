package com.ambition.neoworld.database;

import com.ambition.neoworld.Config;
import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.market.storage.JdbcMarketRepository;
import com.ambition.neoworld.shop.ShopRepository;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.h2.jdbcx.JdbcDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

/**
 * Characterizes known gaps between independent SQL commits and player saves.
 * These are boundary simulations, not executions of MarketService/ShopService or
 * a Minecraft process crash. Passing means the gap was reproduced, not repaired.
 */
public final class PersistenceCrashWindowRegressionTest {
    private PersistenceCrashWindowRegressionTest() {}

    public static void run() throws Exception {
        Path root = Files.createDirectories(Path.of("build", "crash-window-regression")).toAbsolutePath();
        Path dir = Files.createTempDirectory(root, "case-");
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:file:" + dir.resolve("database").toString().replace('\\', '/'));
        NeoWorldDatabase.runMigrations(source, Config.DatabaseType.H2, "nw_");
        UUID owner = UUID.randomUUID();
        Path save = dir.resolve("player.dat");
        var saved = new CompoundTag();
        saved.put("wallet", PlayerData.CODEC.encodeStart(NbtOps.INSTANCE,
                new PlayerData(1, 0, 100, 0, 0)).getOrThrow());
        saved.putInt("item_count", 1);
        saved.putLong("shop_seq", 0);
        NbtIo.writeCompressed(saved, save);

        // Listing committed while the removed item exists only in the live inventory.
        try (Connection c = source.getConnection(); var s = c.prepareStatement(
                "INSERT INTO nw_market_listings (seller_id,seller_name,item_nbt,price_silver,created_game_time,expires_game_time) VALUES (?,'test',?,10,1,100)")) {
            s.setString(1, owner.toString()); s.setBytes(2, new byte[]{1}); s.executeUpdate();
        }
        check(count(source, "nw_market_listings") == 1 && disk(save).getInt("item_count") == 1,
                "listing persists independently of the old player inventory");

        // Seller claim committed while the credited wallet has not been saved.
        PlayerData live = PlayerData.CODEC.parse(NbtOps.INSTANCE, disk(save).get("wallet")).getOrThrow();
        check(live.addSilver(25), "claim can credit live wallet");
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            JdbcMarketRepository.recordOperation(c, UUID.randomUUID(), "CLAIM_SALE", owner,
                    1, "COMMITTED", "SUCCESS", "", 1, 1);
            c.commit();
        }
        long restoredSilver = PlayerData.CODEC.parse(NbtOps.INSTANCE, disk(save).get("wallet")).getOrThrow().getSilver();
        check(count(source, "nw_market_operations") == 1 && live.getSilver() == 125 && restoredSilver == 100,
                "committed claim audit does not persist credited player wallet");

        // Reopening a shop uses live sequence 1, although the disk still has 0.
        try (Connection c = source.getConnection(); var s = c.prepareStatement(
                "INSERT INTO nw_shop_transactions (id,player_id,shop_id,offer_id,action,quantity,total_silver,item_nbt,currency,total_amount,status,tx_seq,balance_before,balance_after) VALUES ('gap',?,1,1,'BUY',1,10,?,'SILVER',10,'PENDING',1,100,90)")) {
            s.setString(1, owner.toString()); s.setBytes(2, new byte[]{1}); s.executeUpdate();
        }
        try (Connection c = source.getConnection()) {
            check(ShopRepository.pendingTransactions(c, "nw_shop_transactions", owner).size() == 1,
                    "shop intent exists before acknowledgement");
            ShopRepository.updateTransactionStatus(c, "nw_shop_transactions", "gap",
                    ShopRepository.TransactionStatus.COMMITTED);
        }
        try (Connection c = source.getConnection()) {
            check(ShopRepository.pendingTransactions(c, "nw_shop_transactions", owner).isEmpty()
                    && disk(save).getLong("shop_seq") == 0,
                    "acknowledged shop intent is absent from recovery despite old player save");
        }
        System.out.println("Historical independent-write gaps reproduced (intentionally bypasses the shared market/shop coordinator).");
    }

    private static CompoundTag disk(Path path) throws Exception {
        return NbtIo.readCompressed(path, NbtAccounter.create(1024 * 1024));
    }

    private static long count(JdbcDataSource source, String table) throws Exception {
        try (var c = source.getConnection(); var s = c.createStatement();
             var r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            r.next(); return r.getLong(1);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
