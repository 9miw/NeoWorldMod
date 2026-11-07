package com.ambition.neoworld.economy.analytics;

import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.registry.ModAttachments;
import com.google.gson.JsonObject;
import java.time.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Called only on the server thread. Captures net outcomes, including rollback to no change. */
public final class EconomyAnalytics {
    public static final ZoneId DAY_ZONE = ZoneId.of("Asia/Bangkok");
    private static final Map<UUID, long[]> OBSERVED = new HashMap<>();
    private static final Map<UUID, Presence> PRESENCE = new HashMap<>();
    private static final Set<UUID> IN_SCOPE = new HashSet<>();
    private static final Map<UUID,Scope> SCOPES = new HashMap<>();
    private static final Map<UUID,Long> SNAPSHOTS = new HashMap<>();
    private static String day = "";
    private static boolean running;
    private EconomyAnalytics() {}
    public static void start() {
        OBSERVED.clear(); PRESENCE.clear(); IN_SCOPE.clear(); SCOPES.clear(); SNAPSHOTS.clear(); day="";
        AnalyticsWriter.start(); running=true;
        enqueue(event("RUN_START",null,"server","","NONE","",0,0,0,"{}"));
    }
    public static void stop() {
        for (Presence p : PRESENCE.values()) p.flush();
        var health=new JsonObject();health.addProperty("dropped",AnalyticsWriter.dropped());health.addProperty("pending",AnalyticsWriter.pending());
        enqueue(event("RUN_STOP",null,"server","","NONE","",0,0,0,health.toString()));
        running=false; AnalyticsWriter.stop(); OBSERVED.clear(); PRESENCE.clear(); IN_SCOPE.clear(); SCOPES.clear(); SNAPSHOTS.clear();
    }
    public static String day(long millis) { return Instant.ofEpochMilli(millis).atZone(DAY_ZONE).toLocalDate().toString(); }
    public static boolean admin(ServerPlayer p) { return p.hasPermissions(2) || p.isCreative() || p.isSpectator(); }
    private static long[] balances(ServerPlayer p) {
        PlayerData d=p.getData(ModAttachments.PLAYER_DATA);
        return new long[]{d.getSilver(),d.getDiamond()};
    }
    public static Scope observe(ServerPlayer p, String source, String reference, String flow) {
        if (!running || p == null || !IN_SCOPE.add(p.getUUID())) return new Scope(null,null,"","","","");
        long[] before=balances(p);
        reconcile(p,before);
        var scope = new Scope(p,before,source,reference,flow,"");
        SCOPES.put(p.getUUID(),scope); return scope;
    }
    public static String correlation(UUID player) {
        Scope scope=SCOPES.get(player); return scope==null?UUID.randomUUID().toString():scope.correlation();
    }
    public static final class Scope implements AutoCloseable {
        private final ServerPlayer player;
        private final long[] before;
        private final String source, reference, flow;
        private String correlation;
        private JsonObject detail = new JsonObject();
        private boolean closed;
        private Scope(ServerPlayer p,long[] before,String source,String reference,String flow,String correlation) {
            this.player=p; this.before=before; this.source=source; this.reference=reference; this.flow=flow; this.correlation=correlation;
        }
        public String correlation() { if(correlation.isEmpty())correlation=UUID.randomUUID().toString();return correlation; }
        public void detail(JsonObject detail) { this.detail=detail.deepCopy(); }
        @Override public void close() {
            if (closed || player==null) return; closed=true;
            long[] after=balances(player);
            if(!Arrays.equals(before,after))differences(player,before,after,source,reference,flow,correlation(),detail);
            OBSERVED.put(player.getUUID(),after); IN_SCOPE.remove(player.getUUID()); SCOPES.remove(player.getUUID());
        }
    }
    private static void reconcile(ServerPlayer p,long[] current) {
        long[] previous=OBSERVED.put(p.getUUID(),current);
        if (previous!=null && !Arrays.equals(previous,current)) differences(p,previous,current,"unattributed","","UNKNOWN",UUID.randomUUID().toString());
    }
    private static void differences(ServerPlayer p,long[] before,long[] after,String source,String reference,String flow,String correlation) {
        differences(p,before,after,source,reference,flow,correlation,new JsonObject());
    }
    private static void differences(ServerPlayer p,long[] before,long[] after,String source,String reference,String flow,String correlation,JsonObject metadata) {
        String[] currencies={"SILVER","DIAMOND"};
        for(int i=0;i<2;i++) if(before[i]!=after[i]) {
            JsonObject detail=metadata.deepCopy(); detail.addProperty("correlation",correlation);
            enqueue(event("WALLET",p,source,reference,flow,currencies[i],before[i],after[i],after[i]-before[i],detail.toString()));
        }
    }
    public static EconomyAnalyticsRepository.Event event(String type,ServerPlayer p,String source,String reference,
            String flow,String currency,long before,long after,long amount,String detail) {
        return new EconomyAnalyticsRepository.Event(UUID.randomUUID().toString(),System.currentTimeMillis(),type,
                p==null?null:p.getUUID().toString(),p==null?0:p.getData(ModAttachments.PLAYER_DATA).getLevel(),
                flow.equals("ADMIN") || p!=null&&admin(p),source,reference,flow,currency,before,after,amount,detail);
    }
    public static void enqueue(EconomyAnalyticsRepository.Event e) { if(running) AnalyticsWriter.offer(AnalyticsWork.event(e)); }
    public static void coinIssued(ServerPlayer player,net.minecraft.world.item.ItemStack stack,String source,String reference) {
        if(stack.getItem() instanceof com.ambition.neoworld.item.CoinItem coin) {
            var detail=new JsonObject();detail.addProperty("quantity",stack.getCount());
            long total=com.ambition.neoworld.item.CoinItem.getTotalValue(stack);
            detail.addProperty("value_per_coin",com.ambition.neoworld.item.CoinItem.getCoinValue(stack));
            enqueue(event(total>0?"COIN_ISSUED":"COIN_INVALID",player,source,reference,total>0?"ISSUED":"UNKNOWN",coin.getCoinType().name(),0,0,
                    Math.max(0,total),detail.toString()));
        }
    }
    public static void login(ServerPlayer p) { if(running) { OBSERVED.put(p.getUUID(),balances(p)); snapshot(p); } }
    public static void logout(ServerPlayer p) {
        if(!running) return;
        snapshot(p); var presence=PRESENCE.remove(p.getUUID()); if(presence!=null) presence.flush(); OBSERVED.remove(p.getUUID()); SNAPSHOTS.remove(p.getUUID());
    }
    public static void daily() {
        if(!running)return;
        String today=day(System.currentTimeMillis());
        if(!today.equals(day)) { if (AnalyticsWriter.offer(AnalyticsWork.carryDaily(today))) day=today; }
    }
    public static void snapshot(ServerPlayer p) {
        if(!running) return;
        long now=System.currentTimeMillis(); String today=day(now);
        daily(); SNAPSHOTS.put(p.getUUID(),now);
        long[] b=balances(p); reconcile(p,b);
        var balance=new EconomyAnalyticsRepository.Balance(p.getUUID().toString(),now,
                p.getData(ModAttachments.PLAYER_DATA).getLevel(),admin(p),b[0],p.getData(ModAttachments.PLAYER_DATA).getLegacyRetiredBalance(),b[1]);
        AnalyticsWriter.offer(AnalyticsWork.balance(balance,today));
    }
    public static void sample(ServerPlayer p,long elapsedNanos,boolean afk) {
        if(!running) return;
        long now=System.currentTimeMillis();
        int level=p.getData(ModAttachments.PLAYER_DATA).getLevel();
        String state=p.isSpectator()||!p.isAlive()?"EXCLUDED":afk?"AFK":"ACTIVE";
        Presence old=PRESENCE.get(p.getUUID());
        if(old==null || old.level!=level || old.admin!=admin(p) || !old.state.equals(state) || !day(now).equals(day(old.start))) {
            if(old!=null) old.flush(); old=new Presence(p,level,state,now); PRESENCE.put(p.getUUID(),old);
        }
        old.elapsed+=Math.max(0,Math.min(5_000_000_000L,elapsedNanos));
        if(now-old.start>=60000) { old.flush(); PRESENCE.remove(p.getUUID()); }
        if(now-SNAPSHOTS.getOrDefault(p.getUUID(),0L)>=60000)snapshot(p);
    }
    private static final class Presence {
        final String player,state; final int level; final boolean admin; final long start; long elapsed;
        Presence(ServerPlayer p,int level,String state,long start) { player=p.getUUID().toString(); this.level=level; this.state=state; this.start=start; admin=admin(p); }
        void flush() {
            if(elapsed==0)return;
            var detail=new JsonObject(); detail.addProperty("state",state); detail.addProperty("start",start);
            enqueue(new EconomyAnalyticsRepository.Event(UUID.randomUUID().toString(),System.currentTimeMillis(),"PRESENCE",player,
                    level,admin,"playtime","","NONE","",0,0,elapsed/1_000_000,detail.toString())); elapsed=0;
        }
    }
}
