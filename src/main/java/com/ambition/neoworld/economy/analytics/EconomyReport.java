package com.ambition.neoworld.economy.analytics;

import com.ambition.neoworld.database.NeoWorldDatabase;
import com.google.gson.JsonParser;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Streaming raw CSV plus bounded-period summaries; no player state is modified. */
public final class EconomyReport {
    private final String prefix;
    EconomyReport(String prefix) {
        if(!prefix.matches("[A-Za-z_][A-Za-z0-9_]{0,31}"))throw new IllegalArgumentException("Invalid analysis prefix");
        this.prefix=prefix;
    }
    public static String csv(Object value) {
        String text=value==null?"":value.toString();
        // Treat external names/components as text in spreadsheet software.
        String lead=text.stripLeading();
        if(!lead.isEmpty() && ("=+@".indexOf(lead.charAt(0))>=0 || lead.charAt(0)=='-' && !lead.matches("-\\d+(\\.\\d+)?")))text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }
    private static void row(Writer out,Object... values) throws IOException {
        for(int i=0;i<values.length;i++){if(i>0)out.write(',');out.write(csv(values[i]));}out.write('\n');
    }
    private static BufferedWriter file(Path dir,String name) throws IOException {
        var out=Files.newBufferedWriter(dir.resolve(name),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        out.write('\ufeff'); return out;
    }
    public static Path export(Path root,int days) throws Exception {
        long end=System.currentTimeMillis(),start=end-days*86400000L;
        Path dir=root.resolve(Instant.ofEpochMilli(end).toString().replace(':','-')+"-"+UUID.randomUUID());
        Files.createDirectories(dir);
        try(Connection c=NeoWorldDatabase.getConnection()) {
            c.setAutoCommit(false); c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            new EconomyReport(com.ambition.neoworld.Config.DATABASE_TABLE_PREFIX.get()).write(c,dir,start,end);
            c.commit();
        }
        Files.writeString(dir.resolve("README.txt"),"Period UTC: "+Instant.ofEpochMilli(start)+" to "+Instant.ofEpochMilli(end)
                +"\nDaily snapshots: Asia/Bangkok; observed_at is the last actual observation, including offline carry-forward."
                +"\nadmin=true and ADMIN flows excluded from summaries. Raw files retain all records."
                +"\nWallet SOURCE/SINK are confirmed system flows. TRANSFER is never new money."
                +"\nCOIN_ISSUED tracks created physical coins separately; UNKNOWN redemptions must not be added to minted money."
                +"\nIncome is observed positive wallet receipts (excluding admin), not profit; activity coins are income only when redeemed."
                +"\nActive time uses existing activity AFK rules; dead/spectator time is EXCLUDED. Period edges may include up to one minute of a presence interval."
                +"\nReport can lag queued telemetry by a few seconds. pending_at_export="+AnalyticsWriter.pending()+", dropped_this_run="+AnalyticsWriter.dropped()
                +"\nAccepted telemetry survives process restart in the disk outbox until SQL acknowledgement. This is not a guarantee against host power loss."
                +"\nReview server error logs for disk acceptance failures or blocked delivery. pending=-1 means the local outbox is unavailable."
                +"\nNPC raw history can predate telemetry and lacks historical admin flags. Do not mix it into newer summaries."
                +"\nMarket duration is blank for listings created before analytics. Historical rows are not fabricated."
                +"\nLatest wealth covers players observed since installation, not every offline player in world/playerdata."
                +"\nWealth/affordability use wallet balances. Unclaimed market proceeds are exported separately; physical coins are not inventoried."
                +"\nCSV values are quoted and formula-leading text escaped. Item/component snapshots remain in events.csv JSON payloads and original NPC audit NBT.\n",StandardCharsets.UTF_8);
        return dir;
    }
    void write(Connection c,Path dir,long start,long end)throws Exception {
        exportQuery(c,dir,"events.csv","SELECT * FROM "+table("economy_events")+" WHERE occurred_at>=? AND occurred_at<? ORDER BY occurred_at,id",start,end);
        exportQuery(c,dir,"balances_latest.csv","SELECT player_id,observed_at,player_level,admin,silver,diamond FROM "+table("economy_balances")+" ORDER BY player_id");
        exportQuery(c,dir,"market_pending_balances.csv","SELECT * FROM "+table("market_pending_balances")+" ORDER BY player_id");
        exportQuery(c,dir,"balances_daily.csv","SELECT snapshot_day,player_id,observed_at,player_level,admin,silver,diamond FROM "+table("economy_daily_balances")+" WHERE snapshot_day>=? ORDER BY snapshot_day,player_id",EconomyAnalytics.day(start));
        exportQuery(c,dir,"npc_transactions.csv","SELECT id,player_id,shop_id,offer_id,action,quantity,currency,total_amount,item_nbt,created_at FROM "+table("shop_transactions")+" WHERE created_at>=? AND created_at<? ORDER BY created_at",new Timestamp(start),new Timestamp(end));
        BigDecimal[] incomeRates=summaries(c,dir,start,end);
        npcTrades(c,dir,start,end);
        wealth(c,dir,end,incomeRates);
    }
    private String table(String suffix){return prefix+suffix;}
    private void npcTrades(Connection c,Path dir,long start,long end)throws Exception {
        try(var out=file(dir,"npc_trades.csv");var s=c.prepareStatement("SELECT * FROM "+table("economy_events")+" WHERE event_type='WALLET' AND source IN ('npc_sell','npc_buy') AND occurred_at>=? AND occurred_at<? ORDER BY occurred_at")) {
            row(out,"time_utc","audit_transaction_id","player_id","shop_offer","item_id","quantity","action","unit_price","total","currency","admin");
            s.setLong(1,start);s.setLong(2,end);s.setFetchSize(256);
            try(var r=s.executeQuery()) {while(r.next()) {
                var d=JsonParser.parseString(r.getString("detail")).getAsJsonObject();
                row(out,Instant.ofEpochMilli(r.getLong("occurred_at")),d.get("correlation").getAsString(),r.getString("player_id"),r.getString("reference_id"),
                        d.get("item_id").getAsString(),d.get("quantity").getAsInt(),d.get("action").getAsString(),d.get("unit_price").getAsLong(),
                        Math.abs(r.getLong("amount")),r.getString("currency"),r.getBoolean("admin"));
            }}
        }
    }
    static void exportQuery(Connection c,Path dir,String name,String sql,Object... args)throws Exception {
        try(var out=file(dir,name);var s=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);
            s.setFetchSize(256);
            try(var r=s.executeQuery()) {
                int count=r.getMetaData().getColumnCount(); Object[] values=new Object[count];
                for(int i=0;i<count;i++)values[i]=r.getMetaData().getColumnLabel(i+1); row(out,values);
                while(r.next()){
                    for(int i=0;i<count;i++) {
                        Object value=r.getObject(i+1);
                        if(value instanceof Clob clob)value=clob.getSubString(1,Math.toIntExact(clob.length()));
                        if(value instanceof Blob blob)value=Base64.getEncoder().encodeToString(blob.getBytes(1,Math.toIntExact(blob.length())));
                        if(value instanceof byte[] bytes)value=Base64.getEncoder().encodeToString(bytes);
                        values[i]=value;
                    }
                    row(out,values);
                }
            }
        }
    }
    private static final class Income {
        long activeMillis;
        final BigInteger[] receipts={BigInteger.ZERO,BigInteger.ZERO};
        final BigInteger[] systemIncome={BigInteger.ZERO,BigInteger.ZERO};
    }
    private static int currency(String currency){return switch(currency){case "SILVER"->0;case "DIAMOND"->1;default->-1;};}
    private static void add(Map<String,BigInteger> map,String key,long amount){map.merge(key,BigInteger.valueOf(amount),BigInteger::add);}
    private BigDecimal[] summaries(Connection c,Path dir,long start,long end)throws Exception {
        Map<String,Income> players=new TreeMap<>();Map<String,BigInteger> totals=new TreeMap<>();
        BigDecimal[] knownIncomeRates=new BigDecimal[2];
        try(var s=c.prepareStatement("SELECT * FROM "+table("economy_events")+" WHERE occurred_at>=? AND occurred_at<? ORDER BY occurred_at,id");
            var market=file(dir,"market_sales.csv")) {
            row(market,"time_utc","listing_id","seller_id","buyer_id","item_id","quantity","unit_price","total","tax","wall_seconds_to_sale","admin");
            s.setLong(1,start);s.setLong(2,end);s.setFetchSize(256);
            try(var r=s.executeQuery()) {while(r.next()) {
                String type=r.getString("event_type"),player=r.getString("player_id"),source=r.getString("source"),flow=r.getString("flow"),cur=r.getString("currency");
                long amount=r.getLong("amount");boolean admin=r.getBoolean("admin")||flow.equals("ADMIN");
                if(type.equals("MARKET_SOLD")) {
                    var d=JsonParser.parseString(r.getString("detail")).getAsJsonObject();
                    Long listedAt=listingTime(c,r.getString("reference_id"));
                    row(market,Instant.ofEpochMilli(r.getLong("occurred_at")),r.getString("reference_id"),player,d.get("buyer_id").getAsString(),
                            d.get("item_id").getAsString(),d.get("quantity").getAsInt(),d.get("unit_price").getAsLong(),d.get("total").getAsLong(),
                            d.get("tax").getAsLong(),listedAt==null?"":Math.max(0,r.getLong("occurred_at")-listedAt)/1000.0,admin);
                }
                if(admin || cur.equals("GOLD"))continue;
                if(type.equals("PRESENCE") && player!=null) {
                    String state=JsonParser.parseString(r.getString("detail")).getAsJsonObject().get("state").getAsString();
                    add(totals,"playtime_ms/"+state,amount);
                    if(state.equals("ACTIVE"))players.computeIfAbsent(player,k->new Income()).activeMillis+=amount;
                }
                if(type.equals("WALLET")) {
                    add(totals,"wallet_net/"+cur+"/"+flow,amount);
                    if(flow.equals("SOURCE")||flow.equals("SINK"))add(totals,"confirmed_system_net/"+cur,amount);
                    if(source.startsWith("npc_"))add(totals,"npc/"+cur+"/"+r.getString("reference_id")+"/"+source,amount);
                    int index=currency(cur);
                    if(amount>0&&index>=0&&player!=null) {
                        Income income=players.computeIfAbsent(player,k->new Income());income.receipts[index]=income.receipts[index].add(BigInteger.valueOf(amount));
                        if(flow.equals("SOURCE"))income.systemIncome[index]=income.systemIncome[index].add(BigInteger.valueOf(amount));
                    }
                } else if(type.equals("MARKET_TAX")){add(totals,"confirmed_system_net/SILVER",-amount);add(totals,"market_tax/SILVER",amount);}
                else if(type.equals("COIN_ISSUED"))add(totals,"physical_coin_issued/"+cur+"/"+source,amount);
            }}
        }
        try(var out=file(dir,"player_income.csv")) {
            row(out,"player_id","active_hours","currency","positive_wallet_receipts","receipts_per_active_hour","known_system_income","known_system_income_per_active_hour");
            String[] currencies={"SILVER","DIAMOND"};
            for(int i=0;i<2;i++) {
                List<BigDecimal> rates=new ArrayList<>(),knownRates=new ArrayList<>();
                for(var entry:players.entrySet()) {
                    Income p=entry.getValue();BigDecimal rate=p.activeMillis==0?null:new BigDecimal(p.receipts[i]).multiply(BigDecimal.valueOf(3600000)).divide(BigDecimal.valueOf(p.activeMillis),4,RoundingMode.HALF_UP);
                    if(rate!=null)rates.add(rate);
                    BigDecimal known=p.activeMillis==0?null:new BigDecimal(p.systemIncome[i]).multiply(BigDecimal.valueOf(3600000)).divide(BigDecimal.valueOf(p.activeMillis),4,RoundingMode.HALF_UP);
                    if(known!=null)knownRates.add(known);
                    row(out,entry.getKey(),p.activeMillis/3600000.0,currencies[i],p.receipts[i],rate,p.systemIncome[i],known);
                }
                rates.sort(Comparator.naturalOrder());
                knownRates.sort(Comparator.naturalOrder());knownIncomeRates[i]=percentile(knownRates,0.5);
                try(var summary=file(dir,"income_"+currencies[i].toLowerCase(Locale.ROOT)+".csv")) {
                    row(summary,"players_with_active_time","median_receipts_per_hour","p90_receipts_per_hour","p95_receipts_per_hour","median_known_system_income_per_hour","p90_known_system_income_per_hour");
                    row(summary,rates.size(),percentile(rates,0.5),percentile(rates,0.9),percentile(rates,0.95),knownIncomeRates[i],percentile(knownRates,0.9));
                }
            }
        }
        try(var out=file(dir,"summary.csv")){row(out,"metric","amount");for(var e:totals.entrySet())row(out,e.getKey(),e.getValue());}
        try(var out=file(dir,"npc_sources.csv")) {
            row(out,"currency","shop_offer","income","share_of_npc_minted_currency");
            Map<String,BigInteger> minted=new HashMap<>();
            for(var e:totals.entrySet())if(e.getKey().startsWith("npc/")&&e.getKey().endsWith("/npc_sell"))
                minted.merge(e.getKey().split("/",3)[1],e.getValue(),BigInteger::add);
            for(var e:totals.entrySet())if(e.getKey().startsWith("npc/")&&e.getKey().endsWith("/npc_sell")) {
                String[] key=e.getKey().split("/",3);BigInteger all=minted.get(key[1]);
                row(out,key[1],key[2].substring(0,key[2].length()-9),e.getValue(),all.signum()==0?"":new BigDecimal(e.getValue()).divide(new BigDecimal(all),6,RoundingMode.HALF_UP));
            }
        }
        return knownIncomeRates;
    }
    private Long listingTime(Connection c,String listing)throws SQLException {
        try(var s=c.prepareStatement("SELECT MIN(occurred_at) FROM "+table("economy_events")+" WHERE event_type='MARKET_LISTED' AND reference_id=?")) {
            s.setString(1,listing);try(var r=s.executeQuery()){r.next();long time=r.getLong(1);return r.wasNull()?null:time;}
        }
    }
    private void wealth(Connection c,Path dir,long now,BigDecimal[] incomeRates)throws Exception {
        String[] currencies={"silver","diamond"};
        List<List<BigDecimal>> amounts=List.of(new ArrayList<>(),new ArrayList<>());
        long stale=0;
        try(var s=c.createStatement();var r=s.executeQuery("SELECT silver,diamond,observed_at FROM "+table("economy_balances")+" WHERE admin=FALSE")) {
            while(r.next()) {
                for(int i=0;i<2;i++)amounts.get(i).add(BigDecimal.valueOf(r.getLong(i+1)));
                if(now-r.getLong(3)>86400000L)stale++;
            }
        }
        try(var out=file(dir,"wealth.csv")) {
            row(out,"currency","observed_players","stale_over_24h","total","median","p90","top_10_percent_share");
            for(int i=0;i<2;i++) {
                var values=amounts.get(i);values.sort(Comparator.naturalOrder());
                BigDecimal total=values.stream().reduce(BigDecimal.ZERO,BigDecimal::add),top=BigDecimal.ZERO;
                int count=(int)Math.ceil(values.size()*0.1);
                for(int n=values.size()-count;n<values.size();n++)top=top.add(values.get(n));
                row(out,currencies[i],values.size(),stale,total,percentile(values,0.5),percentile(values,0.9),total.signum()==0?"":top.divide(total,6,RoundingMode.HALF_UP));
            }
        }
        try(var out=file(dir,"npc_affordability.csv");var s=c.createStatement();var r=s.executeQuery("SELECT id,shop_id,currency,buy_price,sell_price FROM "+table("shop_offers")+" ORDER BY shop_id,id")) {
            row(out,"shop_id","offer_id","currency","buy_price","sell_price","observed_players","players_can_buy_one","share_can_buy_one","median_known_system_income_per_active_hour","estimated_active_hours_to_buy_one");
            while(r.next()) {
                int index=currency(r.getString("currency"));if(index<0)continue;
                long buy=r.getLong("buy_price"),can=0;
                var values=amounts.get(index);
                if(buy>0)for(var value:values)if(value.compareTo(BigDecimal.valueOf(buy))>=0)can++;
                BigDecimal rate=incomeRates[index];
                row(out,r.getLong("shop_id"),r.getLong("id"),r.getString("currency"),buy,r.getLong("sell_price"),values.size(),buy>0?can:"",buy>0&&!values.isEmpty()?can/(double)values.size():"",rate,
                        buy>0&&rate!=null&&rate.signum()>0?BigDecimal.valueOf(buy).divide(rate,4,RoundingMode.HALF_UP):"");
            }
        }
    }
    public static BigDecimal percentile(List<BigDecimal> sorted,double percentile) {
        if(sorted.isEmpty())return null;
        if(percentile==0.5 && sorted.size()%2==0)return sorted.get(sorted.size()/2-1).add(sorted.get(sorted.size()/2)).divide(BigDecimal.valueOf(2));
        int index=Math.max(0,(int)Math.ceil(sorted.size()*percentile)-1);return sorted.get(index);
    }
}
