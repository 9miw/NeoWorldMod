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
        UUID request=UUID.randomUUID();
        var action=new PvpGuiRequestPayload(request,PvpGuiRequestPayload.Action.CREATE,"arena_1");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            PvpGuiRequestPayload.STREAM_CODEC.encode(buffer,action);
            check(PvpGuiRequestPayload.STREAM_CODEC.decode(buffer).equals(action),"Request preserves action and correlation ID");
        } finally { buffer.release(); }
        rejects(() -> new PvpGuiRequestPayload(request,PvpGuiRequestPayload.Action.CREATE,"../invalid"));
        rejects(() -> new PvpGuiRequestPayload(request,PvpGuiRequestPayload.Action.QUEUE,""));
        var top=new PvpGuiRequestPayload(request,PvpGuiRequestPayload.Action.TOP,"");
        buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try { PvpGuiRequestPayload.STREAM_CODEC.encode(buffer,top); check(PvpGuiRequestPayload.STREAM_CODEC.decode(buffer).equals(top),"Leaderboard requests are separate from room polling"); }
        finally { buffer.release(); }
        var data=new PvpGuiDataPayload(request,"",true,1248,12,false,1,"WAITING",true,true,true,false,
                List.of("arena_1"),List.of(new PvpGuiDataPayload.Member("Owner",true,true),new PvpGuiDataPayload.Member("Guest",false,true)),
                List.of(new PvpGuiDataPayload.Leader("Leader",1600)));
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
