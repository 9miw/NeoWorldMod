package com.ambition.neoworld.pvp;

import java.util.List;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import com.ambition.neoworld.network.pvp.PvpGuiRequestPayload;
import com.ambition.neoworld.network.pvp.PvpGuiDataPayload;
import com.ambition.neoworld.network.pvp.PvpQueueStatePayload;
import com.ambition.neoworld.network.pvp.PvpQueueCancelPayload;
import com.ambition.neoworld.client.state.ClientPvpQueueData;

public final class PvpGuiRegressionTest {
    public static void run() {
        var gain=com.ambition.neoworld.pvp.room.PvpTitles.ratingChange(1000,1016);
        var loss=com.ambition.neoworld.pvp.room.PvpTitles.ratingChange(1000,984);
        var unchanged=com.ambition.neoworld.pvp.room.PvpTitles.ratingChange(1000,1000);
        check(gain.getString().equals("Rating \uE408 +16 (1,016)"),"Rating gain shows up arrow and actual positive delta");
        check(loss.getString().equals("Rating \uE409 -16 (984)"),"Rating loss shows down arrow and actual negative delta");
        check(unchanged.getString().equals("Rating ±0 (1,000)"),"Unchanged rating has no directional arrow");
        for(var result:List.of(gain,loss)) {
            var icon=result.getSiblings().getFirst();
            check(com.ambition.neoworld.pvp.room.PvpTitles.RATING_FONT.equals(icon.getStyle().getFont())
                    && icon.getStyle().getColor().getValue()==0xFFFFFF,"Arrow uses small named bitmap font without tint");
            check(!com.ambition.neoworld.pvp.room.PvpTitles.RATING_FONT.equals(result.getStyle().getFont()),"Rating text retains normal font");
        }
        UUID request=UUID.randomUUID();
        var action=new PvpGuiRequestPayload(request,PvpGuiRequestPayload.Action.CREATE,"arena_1");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            PvpGuiRequestPayload.STREAM_CODEC.encode(buffer,action);
            check(PvpGuiRequestPayload.STREAM_CODEC.decode(buffer).equals(action),"Request preserves action and correlation ID");
        } finally { buffer.release(); }
        rejects(() -> new PvpGuiRequestPayload(request,PvpGuiRequestPayload.Action.CREATE,"../invalid"));
        rejects(() -> new PvpGuiRequestPayload(request,PvpGuiRequestPayload.Action.QUEUE,"arena_1"));
        var globalQueue=new PvpGuiRequestPayload(request,PvpGuiRequestPayload.Action.QUEUE,"");
        buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { PvpGuiRequestPayload.STREAM_CODEC.encode(buffer,globalQueue); check(PvpGuiRequestPayload.STREAM_CODEC.decode(buffer).equals(globalQueue),"Ranked queue carries no client-selected arena"); }
        finally { buffer.release(); }
        var top=new PvpGuiRequestPayload(request,PvpGuiRequestPayload.Action.TOP,"");
        buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { PvpGuiRequestPayload.STREAM_CODEC.encode(buffer,top); check(PvpGuiRequestPayload.STREAM_CODEC.decode(buffer).equals(top),"Leaderboard requests are separate from room polling"); }
        finally { buffer.release(); }
        for(var kind:List.of(PvpGuiRequestPayload.Action.INVITE,PvpGuiRequestPayload.Action.ACCEPT_INVITE,PvpGuiRequestPayload.Action.DECLINE_INVITE)) {
            var inviteAction=new PvpGuiRequestPayload(request,kind,request.toString());
            buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try { PvpGuiRequestPayload.STREAM_CODEC.encode(buffer,inviteAction); check(PvpGuiRequestPayload.STREAM_CODEC.decode(buffer).equals(inviteAction),"Invitation action preserves server token/target"); }
            finally { buffer.release(); }
            rejects(() -> new PvpGuiRequestPayload(request,kind,"not-a-uuid"));
        }
        var invitation=new com.ambition.neoworld.network.pvp.PvpInvitationPayload(request,12,"Host");
        buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            com.ambition.neoworld.network.pvp.PvpInvitationPayload.STREAM_CODEC.encode(buffer,invitation);
            check(com.ambition.neoworld.network.pvp.PvpInvitationPayload.STREAM_CODEC.decode(buffer).equals(invitation),"Invitation preserves recipient token, room and host");
        } finally { buffer.release(); }
        rejects(() -> new com.ambition.neoworld.network.pvp.PvpInvitationPayload(request,0,"Host"));
        var data=new PvpGuiDataPayload(request,"",true,1248,12,false,1,"WAITING",true,true,true,false,
                List.of("arena_1"),List.of(new PvpGuiDataPayload.Member("Owner",true,true),new PvpGuiDataPayload.Member("Guest",false,true)),
                List.of(new PvpGuiDataPayload.Leader("Leader",1600)),"arena_1");
        buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            PvpGuiDataPayload.STREAM_CODEC.encode(buffer,data);
            check(PvpGuiDataPayload.STREAM_CODEC.decode(buffer).equals(data),"Snapshot preserves score, actual rank and room state");
        } finally { buffer.release(); }
        var unavailable=new PvpGuiDataPayload(request,"offline",false,0,0,false,0,"",false,false,false,false,List.of(),List.of(),List.of());
        buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { PvpGuiDataPayload.STREAM_CODEC.encode(buffer,unavailable); check(PvpGuiDataPayload.STREAM_CODEC.decode(buffer).equals(unavailable),"Unavailable data is explicit"); }
        finally { buffer.release(); }
        rejects(() -> new PvpGuiDataPayload(request,"",true,1000,0,false,0,"",false,false,false,false,
                java.util.Collections.nCopies(129,"arena"),List.of(),List.of()));
        var queue=new PvpQueueStatePayload(true,1248,42);
        buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            PvpQueueStatePayload.STREAM_CODEC.encode(buffer,queue);
            check(PvpQueueStatePayload.STREAM_CODEC.decode(buffer).equals(queue),"Queue HUD preserves server score and elapsed time");
            PvpQueueStatePayload.STREAM_CODEC.encode(buffer,PvpQueueStatePayload.EMPTY);
            check(PvpQueueStatePayload.STREAM_CODEC.decode(buffer).equals(PvpQueueStatePayload.EMPTY),"Queue removal clears HUD");
            PvpQueueCancelPayload.STREAM_CODEC.encode(buffer,PvpQueueCancelPayload.INSTANCE);
            check(PvpQueueCancelPayload.STREAM_CODEC.decode(buffer).equals(PvpQueueCancelPayload.INSTANCE),"Cancel request carries no player identity or room action");
        } finally { buffer.release(); }
        rejects(() -> new PvpQueueStatePayload(true,1000,-1));
        rejects(() -> new PvpQueueStatePayload(false,1248,42));
        ClientPvpQueueData.update(queue); check(ClientPvpQueueData.state().queued(),"HUD active while queued");
        ClientPvpQueueData.update(PvpQueueStatePayload.EMPTY); check(!ClientPvpQueueData.state().queued(),"Matched/cancelled queue hides HUD");
        ClientPvpQueueData.update(queue); ClientPvpQueueData.reset(); check(!ClientPvpQueueData.state().queued(),"Disconnect clears cached queue");
        System.out.println("PvP GUI codec regression checks passed");
    }
    private static void check(boolean valid,String message) { if(!valid) throw new AssertionError(message); }
    private static void rejects(Runnable action) { try { action.run(); } catch(IllegalArgumentException expected) { return; } throw new AssertionError("Invalid input accepted"); }
}
