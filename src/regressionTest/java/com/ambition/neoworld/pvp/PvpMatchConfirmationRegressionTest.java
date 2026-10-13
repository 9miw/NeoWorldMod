package com.ambition.neoworld.pvp;

import java.util.List;
import java.util.UUID;
import com.ambition.neoworld.pvp.ranked.MatchConfirmation;
import com.ambition.neoworld.pvp.ranked.PvpQueuePenaltyData;
import com.ambition.neoworld.network.pvp.PvpMatchOfferPayload;
import com.ambition.neoworld.network.pvp.PvpMatchConfirmPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;

public final class PvpMatchConfirmationRegressionTest {
    public static void run() {
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),outsider=UUID.randomUUID();
        var offer=new MatchConfirmation(a,b,100);
        check(offer.remaining(100)==30 && offer.status(100)==MatchConfirmation.Status.WAITING,"Initial offer waits for 30 seconds");
        offer.accept(a,101); offer.accept(a,102);
        check(offer.ready(a) && !offer.ready(b) && offer.status(699)==MatchConfirmation.Status.WAITING,"Repeated acceptance never readies the opponent");
        check(offer.status(700)==MatchConfirmation.Status.EXPIRED,"One accepted player does not start at timeout");
        check(!offer.accept(b,700) && !offer.ready(b),"Acceptance at exact deadline is rejected");
        rejects(() -> offer.accept(outsider,102));
        var accepted=new MatchConfirmation(a,b,0); accepted.accept(a,1); accepted.accept(b,599);
        check(accepted.status(600)==MatchConfirmation.Status.ACCEPTED,"Both accept before deadline starts the match");
        var neither=new MatchConfirmation(a,b,0);
        check(neither.status(600)==MatchConfirmation.Status.EXPIRED && !neither.ready(a) && !neither.ready(b),"Both absent confirmations time out");

        var penalty=new PvpQueuePenaltyData(); penalty.penalize(b,10_000);
        check(penalty.remaining(a,10_000)==0 && penalty.remaining(b,10_000)==60,"Only offender receives 60-second queue penalty");
        var restored=PvpQueuePenaltyData.load(penalty.save(new CompoundTag(),null),null);
        check(restored.remaining(b,40_000)==30,"Penalty survives save/load and wall-clock time");
        check(restored.remaining(b,70_000)==0,"Penalty expires exactly at 60 seconds");

        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            var closed=PvpMatchOfferPayload.closed(offer.id); PvpMatchOfferPayload.STREAM_CODEC.encode(buffer,closed);
            check(PvpMatchOfferPayload.STREAM_CODEC.decode(buffer).equals(closed),"Closed packet identifies only its own offer");
            var confirm=new PvpMatchConfirmPayload(offer.id); PvpMatchConfirmPayload.STREAM_CODEC.encode(buffer,confirm);
            check(PvpMatchConfirmPayload.STREAM_CODEC.decode(buffer).equals(confirm),"Acceptance carries only offer UUID");
        } finally { buffer.release(); }
        rejects(() -> new PvpMatchOfferPayload(offer.id,true,31,null,null,false,false));
        rejects(() -> new PvpMatchOfferPayload.Player(a,"Owner",1000,0,List.of()));
        for(int invalid:new int[]{-1,31}) {
            var bad=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try { bad.writeUUID(offer.id); bad.writeBoolean(true); bad.writeVarInt(invalid); rejects(() -> PvpMatchOfferPayload.STREAM_CODEC.decode(bad)); }
            finally { bad.release(); }
        }
        System.out.println("PvP match confirmation and penalty regression checks passed");
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run(); } catch(IllegalArgumentException expected) { return; } throw new AssertionError("Invalid confirmation accepted"); }
}
