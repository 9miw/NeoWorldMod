package com.ambition.neoworld.trade.service;

import com.ambition.neoworld.trade.menu.TradeMenu;
import com.ambition.neoworld.trade.TradeAgreement;
import com.ambition.neoworld.trade.TradePlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.economy.PlayerEconomyManager;
import com.ambition.neoworld.item.CoinItem;
import com.ambition.neoworld.network.TradeActionPayload;
import com.ambition.neoworld.network.TradeStatePayload;
import com.ambition.neoworld.network.TradeStatePayload.Phase;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.trade.TradePlan.Offer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** All access is confined to the server thread. No assets leave inventory before commit. */
public final class TradeManager {
    private static final Map<MinecraftServer, TradeManager> INSTANCES = new HashMap<>();
    private final MinecraftServer server;
    private final Map<UUID, Session> players = new HashMap<>();
    private final Map<UUID, Long> requestCooldown = new HashMap<>();
    private long tick;
    private TradeManager(MinecraftServer server) { this.server = server; }
    public static TradeManager get(MinecraftServer server) { return INSTANCES.computeIfAbsent(server, TradeManager::new); }
    public static void stop(MinecraftServer server) {
        TradeManager manager = INSTANCES.remove(server);
        if (manager != null) for (Session s : new ArrayList<>(manager.players.values())) manager.finish(s, Phase.CANCELLED, "เซิร์ฟเวอร์กำลังปิด");
    }

    private static final class Side {
        final UUID player;
        final Map<Integer, Offer> offers = new LinkedHashMap<>();
        List<ItemStack> observed;
        ItemStack observedCarried = ItemStack.EMPTY;
        long balance, silver, heartbeat, lastAction = -100;
        Side(ServerPlayer player, long tick) {
            this.player = player.getUUID(); observed = snapshot(player); balance = balance(player); heartbeat = tick;
        }
    }
    private static final class Session {
        final UUID id = UUID.randomUUID();
        final Side a, b;
        final long requestDeadline;
        final TradeAgreement agreement = new TradeAgreement();
        Phase phase = Phase.REQUESTED;
        boolean finished;
        Session(ServerPlayer a, ServerPlayer b, long tick) {
            this.a = new Side(a, tick); this.b = new Side(b, tick); requestDeadline = tick + 300;
        }
        Side side(UUID player) { return a.player.equals(player) ? a : b; }
        Side other(Side side) { return side == a ? b : a; }
    }

    public String request(ServerPlayer requester, ServerPlayer target) {
        assertThread();
        if (target == null || requester == target) return "ไม่พบผู้เล่นที่แลกเปลี่ยนได้";
        if (players.containsKey(requester.getUUID()) || players.containsKey(target.getUUID())) return "ผู้เล่นกำลังมีคำขอหรือรายการแลกเปลี่ยนอยู่";
        if (tick - requestCooldown.getOrDefault(requester.getUUID(), -100L) < 100) return "กรุณารอสักครู่ก่อนส่งคำขอใหม่";
        if (!requestEligible(requester, target)) return "ผู้เล่นยังไม่พร้อมรับคำขอแลกเปลี่ยน";
        requestCooldown.put(requester.getUUID(), tick);
        Session session = new Session(requester, target, tick);
        players.put(session.a.player, session); players.put(session.b.player, session);
        sync(session, "");
        return null;
    }

    public void action(ServerPlayer player, TradeActionPayload payload) {
        assertThread();
        Session s = players.get(player.getUUID());
        if (s == null || s.finished || !s.id.equals(payload.sessionId())) return;
        Side side = s.side(player.getUUID());
        if (payload.action() == TradeActionPayload.Action.CANCEL) { finish(s, Phase.CANCELLED, "ยกเลิกการแลกเปลี่ยน"); return; }
        if (!valid(s)) { finish(s, Phase.CANCELLED, "ผู้เล่นไม่อยู่ในระยะหรือไม่พร้อมแลกเปลี่ยน"); return; }
        if (payload.action() == TradeActionPayload.Action.HEARTBEAT) { side.heartbeat = tick; return; }
        if (tick - side.lastAction < 2) return;
        side.lastAction = tick;
        if (s.phase == Phase.REQUESTED) {
            if (tick >= s.requestDeadline) { finish(s, Phase.CANCELLED, "คำขอหมดเวลา"); return; }
            if (payload.action() != TradeActionPayload.Action.ACCEPT || side != s.b || payload.revision() != s.agreement.revision()) return;
            if (!eligible(player(s.a), player(s.b))) {
                finish(s, Phase.CANCELLED, "ยังเปิดหน้าต่างแลกเปลี่ยนไม่ได้ ผู้เล่นทั้งสองต้องพร้อมแลกเปลี่ยน"); return;
            }
            s.a.heartbeat = tick; s.b.heartbeat = tick;
            refresh(s);
            s.phase = Phase.EDITING; changed(s);
            boolean openedA = openMenu(player(s.a), s.id), openedB = openMenu(player(s.b), s.id);
            if (!openedA || !openedB) { finish(s, Phase.CANCELLED, "เปิดหน้าต่างแลกเปลี่ยนไม่สำเร็จ"); return; }
            sync(s, ""); return;
        }
        refresh(s);
        if (payload.revision() != s.agreement.revision()) { sync(s, "รายการเปลี่ยนแล้ว กรุณาตรวจและทำรายการใหม่"); return; }
        try {
            switch (payload.action()) {
                case OFFER -> {
                    int slot = payload.slot(), count = payload.count();
                    if (slot < 0 || slot >= 36 || count < 0 || count > 64) throw new IllegalArgumentException("จำนวนหรือช่องไม่ถูกต้อง");
                    if (count == 0) {
                        if (side.offers.remove(slot) == null) return;
                    } else {
                        ItemStack actual = player.getInventory().getItem(slot);
                        if (actual.isEmpty() || actual.getItem() instanceof CoinItem || count > actual.getCount()
                                || count > actual.getMaxStackSize()) throw new IllegalArgumentException("ไอเทมหรือจำนวนนี้แลกเปลี่ยนไม่ได้");
                        if (!side.offers.containsKey(slot) && side.offers.size() >= 9) throw new IllegalArgumentException("เสนอได้สูงสุด 9 ช่อง");
                        Offer old = side.offers.get(slot);
                        if (old != null && ItemStack.matches(old.item(), actual.copyWithCount(count))) return;
                        side.offers.put(slot, new Offer(slot, actual.copyWithCount(count)));
                    }
                    changed(s);
                }
                case SILVER -> {
                    if (payload.silver() < 0 || payload.silver() > balance(player)) throw new IllegalArgumentException("Silver ไม่เพียงพอ");
                    if (side.silver == payload.silver()) return;
                    side.silver = payload.silver(); changed(s);
                }
                case CONFIRM -> {
                    if (s.agreement.confirmed(side == s.a ? 0 : 1)) return;
                    preflight(s);
                    s.agreement.confirm(side == s.a ? 0 : 1, payload.revision(), tick);
                    if (s.agreement.confirmed(0) && s.agreement.confirmed(1)) s.phase = Phase.COUNTDOWN;
                }
                case UNCONFIRM -> { if (!s.agreement.confirmed(side == s.a ? 0 : 1)) return; changed(s); }
                default -> { return; }
            }
            sync(s, "");
        } catch (IllegalArgumentException exception) { sync(s, exception.getMessage()); }
    }

    public void tick() {
        assertThread(); tick++;
        for (Session s : new java.util.HashSet<>(players.values())) {
            if (!valid(s)) { finish(s, Phase.CANCELLED, "ผู้เล่นไม่อยู่ในระยะหรือไม่พร้อมแลกเปลี่ยน"); continue; }
            if (s.phase == Phase.REQUESTED) {
                if (tick >= s.requestDeadline) finish(s, Phase.CANCELLED, "คำขอหมดเวลา");
                continue;
            }
            if (tick - s.a.heartbeat > 100 || tick - s.b.heartbeat > 100) {
                finish(s, Phase.CANCELLED, "หน้าต่างแลกเปลี่ยนปิดหรือการเชื่อมต่อขาดหาย"); continue;
            }
            boolean refreshed = refresh(s);
            if (s.phase == Phase.COUNTDOWN && s.agreement.ready(tick)) commit(s);
            else if (refreshed || (s.phase == Phase.COUNTDOWN && tick % 20 == 0)) sync(s, refreshed ? "กระเป๋าหรือยอดเงินเปลี่ยน กรุณายืนยันใหม่" : "");
        }
    }

    public void cancel(UUID player, String reason) {
        assertThread(); Session s = players.get(player); if (s != null) finish(s, Phase.CANCELLED, reason);
    }

    public void disconnect(UUID player) {
        cancel(player, "ผู้เล่นออกจากเกม"); requestCooldown.remove(player);
    }

    public boolean menuValid(ServerPlayer player, UUID id) {
        Session s = players.get(player.getUUID());
        return s != null && !s.finished && s.id.equals(id) && s.phase != Phase.REQUESTED
                && physicallyEligible(player(s.a), player(s.b));
    }
    public void offerFromMenu(ServerPlayer player, UUID id, int slot, int count) {
        assertThread();
        Session s = players.get(player.getUUID());
        if (s == null || !s.id.equals(id) || s.phase == Phase.REQUESTED || !valid(s)) return;
        refresh(s);
        action(player, new TradeActionPayload(id, s.agreement.revision(), TradeActionPayload.Action.OFFER, slot, count, 0));
    }
    public void inventoryChanged(ServerPlayer player, UUID id) {
        assertThread();
        Session s = players.get(player.getUUID());
        if (s != null && s.id.equals(id) && !s.finished && valid(s) && refresh(s)) {
            sync(s, "กระเป๋าหรือไอเทมบนเมาส์เปลี่ยน กรุณายืนยันใหม่");
        }
    }
    private static boolean openMenu(ServerPlayer player, UUID id) {
        return player.openMenu(new SimpleMenuProvider((containerId, inventory, owner) ->
                new TradeMenu(containerId, inventory, id), Component.literal("แลกเปลี่ยน")),
                data -> data.writeUUID(id)).isPresent();
    }

    private boolean refresh(Session s) {
        boolean dirty = false;
        for (Side side : List.of(s.a, s.b)) {
            ServerPlayer player = player(side);
            List<ItemStack> current = snapshot(player);
            boolean inventoryChanged = false;
            for (int slot = 0; slot < 36; slot++) {
                if (!ItemStack.matches(side.observed.get(slot), current.get(slot))) {
                    side.offers.remove(slot); inventoryChanged = true;
                }
            }
            long balance = balance(player);
            ItemStack carried = player.containerMenu.getCarried();
            if (inventoryChanged || balance != side.balance || !ItemStack.matches(carried, side.observedCarried)) dirty = true;
            if (side.silver > balance) side.silver = 0;
            side.observed = current; side.balance = balance;
            side.observedCarried = carried.copy();
        }
        if (dirty) changed(s);
        return dirty;
    }

    private void changed(Session s) {
        s.agreement.changed();
        if (s.phase != Phase.REQUESTED) s.phase = Phase.EDITING;
    }
    private record Plan(List<ItemStack> a, List<ItemStack> b, long silverA, long silverB) {}
    private Plan preflight(Session s) {
        if (!valid(s)) throw new IllegalArgumentException("ผู้เล่นไม่พร้อมแลกเปลี่ยน");
        if (!player(s.a).containerMenu.getCarried().isEmpty() || !player(s.b).containerMenu.getCarried().isEmpty()) {
            throw new IllegalArgumentException("วางไอเทมบนเมาส์กลับกระเป๋าก่อนยืนยัน");
        }
        if (s.a.offers.isEmpty() && s.b.offers.isEmpty() && s.a.silver == 0 && s.b.silver == 0) throw new IllegalArgumentException("ยังไม่มีรายการแลกเปลี่ยน");
        List<Offer> a = List.copyOf(s.a.offers.values()), b = List.copyOf(s.b.offers.values());
        return new Plan(TradePlan.inventory(snapshot(player(s.a)), a, b),
                TradePlan.inventory(snapshot(player(s.b)), b, a),
                TradePlan.balance(balance(player(s.a)), s.a.silver, s.b.silver),
                TradePlan.balance(balance(player(s.b)), s.b.silver, s.a.silver));
    }

    private void commit(Session s) {
        Plan plan;
        try { plan = preflight(s); }
        catch (IllegalArgumentException exception) { changed(s); sync(s, exception.getMessage()); return; }
        ServerPlayer a = player(s.a), b = player(s.b);
        List<ItemStack> beforeA = snapshot(a), beforeB = snapshot(b);
        long silverA = balance(a), silverB = balance(b);
        // No yielding or database IO within this mutation. This is not crash-atomic across player files.
        try {
            apply(a, plan.a); apply(b, plan.b);
            a.getData(ModAttachments.PLAYER_DATA).setSilver(plan.silverA);
            b.getData(ModAttachments.PLAYER_DATA).setSilver(plan.silverB);
        } catch (RuntimeException exception) {
            apply(a, beforeA); apply(b, beforeB);
            a.getData(ModAttachments.PLAYER_DATA).setSilver(silverA);
            b.getData(ModAttachments.PLAYER_DATA).setSilver(silverB);
            NeoWorld.LOGGER.error("Trade {} failed and was rolled back", s.id, exception);
            finish(s, Phase.CANCELLED, "โอนไม่สำเร็จ ยกเลิกรายการแล้ว");
            broadcastInventory(a); broadcastInventory(b); return;
        }
        // Remove ownership before publishing success so repeated packets cannot commit twice.
        finish(s, Phase.COMMITTED, "แลกเปลี่ยนสำเร็จ");
        broadcastInventory(a); broadcastInventory(b);
    }
    private static void apply(ServerPlayer player, List<ItemStack> items) {
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, items.get(slot).copy());
        player.getInventory().setChanged();
    }
    private static void broadcastInventory(ServerPlayer player) {
        player.inventoryMenu.broadcastChanges(); PlayerEconomyManager.syncToClient(player);
    }
    private void finish(Session s, Phase phase, String message) {
        if (s.finished) return;
        s.finished = true; s.phase = phase;
        players.remove(s.a.player, s); players.remove(s.b.player, s);
        for (Side side : List.of(s.a, s.b)) {
            ServerPlayer player = player(side);
            if (player != null && player.containerMenu instanceof TradeMenu menu && menu.belongsTo(s.id) && !menu.isRemoving()) player.closeContainer();
        }
        sync(s, message);
    }
    private void sync(Session s, String message) {
        for (Side side : List.of(s.a, s.b)) {
            ServerPlayer player = player(side), partner = player(s.other(side));
            if (player == null) continue;
            Side other = s.other(side);
            if (!s.finished && player.containerMenu instanceof TradeMenu menu && menu.belongsTo(s.id)) {
                menu.updateOffers(List.copyOf(side.offers.values()), List.copyOf(other.offers.values()));
                menu.broadcastChanges();
            }
            int remaining = (int) Math.max(0, (s.phase == Phase.REQUESTED ? s.requestDeadline : s.agreement.deadline()) - tick);
            PacketDistributor.sendToPlayer(player, new TradeStatePayload(s.id, s.agreement.revision(), s.phase,
                    partner == null ? "ผู้เล่น" : partner.getGameProfile().getName(), side == s.a,
                    List.copyOf(side.offers.values()), List.copyOf(other.offers.values()), side.silver, other.silver,
                    balance(player), s.agreement.confirmed(side == s.a ? 0 : 1), s.agreement.confirmed(side == s.a ? 1 : 0),
                    remaining, snapshot(player), message));
        }
    }
    private ServerPlayer player(Side side) { return server.getPlayerList().getPlayer(side.player); }
    private boolean valid(Session s) {
        ServerPlayer a = player(s.a), b = player(s.b);
        if (s.phase == Phase.REQUESTED) return requestEligible(a, b);
        if (!physicallyEligible(a, b)) return false;
        return a.containerMenu instanceof TradeMenu ma && ma.belongsTo(s.id)
                && b.containerMenu instanceof TradeMenu mb && mb.belongsTo(s.id);
    }
    private static boolean physicallyEligible(ServerPlayer a, ServerPlayer b) {
        return requestPlayersReady(a, b) && a.distanceToSqr(b) <= 64;
    }
    private static boolean requestPlayersReady(ServerPlayer a, ServerPlayer b) {
        return a != null && b != null && a.isAlive() && b.isAlive() && !a.isRemoved() && !b.isRemoved()
                && !a.isCreative() && !b.isCreative() && !a.isSpectator() && !b.isSpectator()
                && a.serverLevel() == b.serverLevel();
    }
    private static boolean requestEligible(ServerPlayer a, ServerPlayer b) {
        return requestPlayersReady(a, b)
                && a.containerMenu == a.inventoryMenu && b.containerMenu == b.inventoryMenu
                && a.containerMenu.getCarried().isEmpty() && b.containerMenu.getCarried().isEmpty();
    }
    private static boolean eligible(ServerPlayer a, ServerPlayer b) {
        return requestEligible(a, b) && a.distanceToSqr(b) <= 64;
    }
    private static long balance(ServerPlayer player) { return player.getData(ModAttachments.PLAYER_DATA).getSilver(); }
    private static List<ItemStack> snapshot(ServerPlayer player) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) items.add(player.getInventory().getItem(slot).copy());
        return items;
    }
    private void assertThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Trade state must be accessed on the server thread");
    }
}
