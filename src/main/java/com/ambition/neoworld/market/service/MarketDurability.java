package com.ambition.neoworld.market.service;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.database.NeoWorldDatabase;
import com.ambition.neoworld.market.MarketResult;
import com.ambition.neoworld.market.menu.MarketSellMenu;
import com.ambition.neoworld.market.storage.JdbcMarketRepository;
import com.ambition.neoworld.market.storage.MarketPlayerJournal;
import com.ambition.neoworld.market.storage.MarketCheckpoint;
import com.ambition.neoworld.registry.ModAttachments;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.Callable;

/** Owns the server-thread player/SQL boundary. SQL ambiguity fails closed until next login recovery. */
public final class MarketDurability {
    private static final java.util.Set<ServerPlayer> QUARANTINED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<net.minecraft.server.MinecraftServer, String> WORLD_IDS = new java.util.WeakHashMap<>();
    private MarketDurability() {}

    private static MarketPlayerJournal journal(ServerPlayer player) throws IOException {
        String world = WORLD_IDS.get(player.server);
        if (world == null) {
            var path = player.server.getWorldPath(LevelResource.ROOT).resolve("neoworld/market-world-id");
            java.nio.file.Files.createDirectories(path.getParent());
            if (!java.nio.file.Files.exists(path)) {
                java.nio.file.Files.writeString(path, UUID.randomUUID().toString(), StandardOpenOption.CREATE_NEW);
                try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
            }
            try { world = UUID.fromString(java.nio.file.Files.readString(path).trim()).toString(); }
            catch (IllegalArgumentException e) { throw new IOException("Invalid market world identity", e); }
            WORLD_IDS.put(player.server, world);
        }
        return new MarketPlayerJournal(NeoWorldDatabase::getConnection,
                NeoWorldDatabase.tableName("market_player_journal"), world);
    }

    public static boolean isQuarantined(ServerPlayer player) { return QUARANTINED.contains(player); }

    public static MarketService.ListingResult execute(ServerPlayer player, UUID operation, String type,
            ItemStack source, Callable<MarketService.ListingResult> action) {
        if (player == null || operation == null) return failed();
        if (!player.server.isSameThread()) throw new IllegalStateException("Market player mutations require the server thread");
        if (isQuarantined(player) || !player.connection.isAcceptingMessages()) return failed();
        if (player.server.getPlayerList().getPlayer(player.getUUID()) != player) return failed();
        if (!player.containerMenu.getCarried().isEmpty() || !player.inventoryMenu.getCarried().isEmpty())
            return unready(player);
        // Temporary crafting/container items do not belong to the player NBT checkpoint.
        if (player.containerMenu != player.inventoryMenu && !(player.containerMenu instanceof MarketSellMenu)
                && !(player.containerMenu instanceof com.ambition.neoworld.shop.ShopMenu))
            return unready(player);
        for (int i = 1; i <= 4; i++)
            if (player.inventoryMenu.getSlot(i).hasItem()) return unready(player);
        if (player.containerMenu instanceof MarketSellMenu menu && !menu.getListingItem().isEmpty()
                && menu.getListingItem() != source)
            return unready(player);
        boolean escrow = source != null && player.containerMenu instanceof MarketSellMenu menu
                && menu.getListingItem() == source;
        if (source != null && !escrow) {
            boolean owned = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++)
                if (player.getInventory().getItem(i) == source) owned = true;
            if (!owned) return failed();
        }
        try {
            var state = new State(player, escrow ? source : null);
            var outcome = journal(player).execute(operation, player.getUUID(), type, state, c -> {
                // Legacy operations have no player snapshot: never apply their effect for a second time.
                if (JdbcMarketRepository.findOperation(c, operation).isPresent())
                    return new MarketPlayerJournal.Result(MarketResult.DATABASE_ERROR.name(), 0);
                var result = action.call();
                if (result.result() == MarketResult.SUCCESS && escrow && !source.isEmpty()) {
                    player.getInventory().add(source);
                    if (!source.isEmpty()) {
                        player.sendSystemMessage(Component.literal("พื้นที่กระเป๋าไม่พอสำหรับสินค้าส่วนที่เหลือ กรุณาลงขายทั้งหมดหรือจัดกระเป๋าก่อน"));
                        return new MarketPlayerJournal.Result(MarketResult.INVENTORY_CHANGED.name(), 0);
                    }
                }
                return new MarketPlayerJournal.Result(result.result().name(), result.listingId().orElse(0));
            });
            com.ambition.neoworld.economy.PlayerEconomyManager.syncToClient(player);
            player.containerMenu.broadcastChanges();
            return new MarketService.ListingResult(MarketResult.valueOf(outcome.code()),
                    outcome.listingId() > 0 ? OptionalLong.of(outcome.listingId()) : OptionalLong.empty());
        } catch (Exception failure) {
            quarantine(player, failure);
            return failed();
        }
    }

    public static void recover(ServerPlayer player) {
        if (isQuarantined(player)) return;
        try {
            var journal = journal(player);
            var state = new State(player, null);
            if (player.server.isSingleplayerOwner(player.getGameProfile())) journal.recoverOwner(player.getUUID(), state);
            else journal.recover(player.getUUID(), state);
        }
        catch (Exception failure) { quarantine(player, failure); }
    }

    public record ShopOutcome(boolean success, String message) {}

    /** Uses the same journal, world identity and loaded checkpoint as market operations. */
    public static String executeShop(ServerPlayer player, UUID operation, boolean sell, long offerId,
            Callable<ShopOutcome> action) {
        if (player == null || operation == null) return "ซื้อขายไม่สำเร็จ";
        if (!player.server.isSameThread()) throw new IllegalStateException("Shop mutations require the server thread");
        if (isQuarantined(player) || !player.connection.isAcceptingMessages()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player) return "ผู้เล่นไม่พร้อมซื้อขาย";
        if (!player.containerMenu.getCarried().isEmpty() || !player.inventoryMenu.getCarried().isEmpty()
                || (player.containerMenu != player.inventoryMenu
                    && !(player.containerMenu instanceof com.ambition.neoworld.shop.ShopMenu)))
            return "กรุณาวางไอเทมบนเมาส์กลับกระเป๋า แล้วเปิดร้านใหม่";
        for (int i = 1; i <= 4; i++) if (player.inventoryMenu.getSlot(i).hasItem())
            return "กรุณาวางไอเทมในช่องคราฟต์กลับกระเป๋าก่อนซื้อขาย";
        try {
            ShopOutcome[] response = {null};
            var result = journal(player).execute(operation, player.getUUID(), sell ? "SHOP_SELL" : "SHOP_BUY",
                    new State(player, null), c -> {
                        response[0] = action.call();
                        return new MarketPlayerJournal.Result(response[0].success() ? "SUCCESS" : "REJECTED", offerId);
                    });
            com.ambition.neoworld.economy.PlayerEconomyManager.syncToClient(player);
            player.containerMenu.broadcastChanges();
            if (response[0] != null) return response[0].message();
            return result.code().equals("SUCCESS") ? "รายการซื้อขายนี้สำเร็จแล้ว" : "ซื้อขายไม่สำเร็จ";
        } catch (Exception failure) {
            quarantine(player, failure);
            return "บันทึกซื้อขายไม่สำเร็จ กรุณาเข้าเกมใหม่เพื่อกู้คืนรายการ";
        }
    }

    private static void quarantine(ServerPlayer player, Exception failure) {
        QUARANTINED.add(player);
        NeoWorld.LOGGER.error("Market player checkpoint failed for {}; recovery required", player.getUUID(), failure);
        player.connection.disconnect(Component.literal("บันทึกข้อมูลตลาดไม่สำเร็จ กรุณาเข้าเกมใหม่เพื่อกู้คืนรายการ"));
    }

    private static MarketService.ListingResult failed() {
        return new MarketService.ListingResult(MarketResult.DATABASE_ERROR, OptionalLong.empty());
    }

    private static MarketService.ListingResult unready(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("กรุณาวางไอเทมบนเมาส์ ในช่องคราฟต์ และช่องลงขายกลับกระเป๋า แล้วปิดหน้าต่างอื่นก่อนทำรายการตลาด"));
        return new MarketService.ListingResult(MarketResult.MARKET_BUSY, OptionalLong.empty());
    }

    private static final class State implements MarketPlayerJournal.PlayerState {
        private final ServerPlayer player;
        private final ItemStack escrow;
        State(ServerPlayer player, ItemStack escrow) { this.player = player; this.escrow = escrow; }

        public byte[] capture() throws IOException {
            CompoundTag tag = new CompoundTag();
            tag.putInt("version", 2);
            tag.put("inventory", player.getInventory().save(new ListTag()));
            MarketCheckpoint.captureWallet(tag, player.getData(ModAttachments.PLAYER_DATA));
            if (escrow != null) tag.putInt("escrow_count", escrow.getCount());
            var out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            return out.toByteArray();
        }

        public void restore(byte[] bytes) throws IOException {
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.create(64L * 1024 * 1024));
            if (tag.getInt("version") < 1 || tag.getInt("version") > 2) throw new IOException("Unsupported player snapshot version");
            player.getInventory().load(tag.getList("inventory", Tag.TAG_COMPOUND));
            player.getInventory().setChanged();
            player.setData(ModAttachments.PLAYER_DATA, MarketCheckpoint.restoreWallet(tag, player.getData(ModAttachments.PLAYER_DATA)));
            if (escrow != null && tag.contains("escrow_count")) escrow.setCount(tag.getInt("escrow_count"));
        }

        public boolean hasCheckpoint(UUID operation) {
            return MarketCheckpoint.matches(player.getPersistentData(), operation);
        }

        private boolean diskHasCheckpoint(UUID operation) throws IOException {
            var path = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + ".dat");
            if (!java.nio.file.Files.exists(path)) return false;
            return MarketCheckpoint.inPlayerSave(NbtIo.readCompressed(path, NbtAccounter.create(64L * 1024 * 1024)), operation);
        }

        public void save(UUID operation) throws IOException {
            MarketCheckpoint.write(player.getPersistentData(), operation);
            ((com.ambition.neoworld.mixin.MarketPlayerSaveInvoker) player.server.getPlayerList()).neoworld$saveMarketPlayer(player);
            if (!diskHasCheckpoint(operation)) throw new IOException("Player save did not persist the market checkpoint");
            var path = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + ".dat");
            try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
        }

    }
}
