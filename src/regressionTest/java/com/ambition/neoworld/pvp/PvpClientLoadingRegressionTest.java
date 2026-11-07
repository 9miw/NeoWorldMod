package com.ambition.neoworld.pvp;

import java.util.List;
import java.util.UUID;
import com.ambition.neoworld.pvp.room.PvpClientLoadBarrier;
import com.ambition.neoworld.network.pvp.PvpArenaLoadPayload;
import com.ambition.neoworld.network.pvp.PvpArenaReadyPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class PvpClientLoadingRegressionTest {
    public static void run() {
        var a=UUID.randomUUID(); var b=UUID.randomUUID(); var outsider=UUID.randomUUID();
        var gate=new PvpClientLoadBarrier(List.of(a,b),100);
        check(!gate.ready(100),"Placement alone cannot start countdown");
        check(!gate.accept(UUID.randomUUID(),a,101) && !gate.accept(gate.token(),outsider,101),"Stale nonce and outsider cannot acknowledge");
        check(gate.accept(gate.token(),a,102) && !gate.ready(102),"One loaded player is insufficient");
        check(!gate.accept(gate.token(),a,103) && !gate.ready(103),"Repeated first-player acknowledgement cannot count twice");
        check(gate.accept(gate.token(),b,104) && gate.ready(104),"Both distinct players unlock countdown");
        var retry=new PvpClientLoadBarrier(List.of(a,b),200);
        check(!retry.accept(gate.token(),a,201),"Prior acknowledgement cannot unlock a fresh gate");
        check(!retry.expired(1399) && retry.expired(1400),"Timeout occurs exactly 60 server seconds after teleport");
        check(!retry.accept(retry.token(),b,1400) && !retry.ready(1400),"Acknowledgements at the timeout boundary are rejected");
        var partial=new PvpClientLoadBarrier(List.of(a,b),0); partial.accept(partial.token(),a,1);
        check(partial.expired(1200) && !partial.ready(1200),"A missing client cannot leave a gate open indefinitely");
        for(var payload:List.of(new PvpArenaLoadPayload(gate.token(),true,new BlockPos(1_000_048,32,1_000_001)),PvpArenaLoadPayload.closed(gate.token()))) {
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try {
                PvpArenaLoadPayload.STREAM_CODEC.encode(buffer,payload);
                check(PvpArenaLoadPayload.STREAM_CODEC.decode(buffer).equals(payload),"Loading challenge preserves nonce, spawn and closure");
            } finally { buffer.release(); }
        }
        var ack=new PvpArenaReadyPayload(gate.token());
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            PvpArenaReadyPayload.STREAM_CODEC.encode(buffer,ack);
            check(PvpArenaReadyPayload.STREAM_CODEC.decode(buffer).equals(ack),"Readiness codec preserves nonce without client-supplied identity");
        } finally { buffer.release(); }
        reject(() -> new PvpArenaReadyPayload(null));
        reject(() -> new PvpArenaLoadPayload(null,true,BlockPos.ZERO));
        reject(() -> new PvpClientLoadBarrier(List.of(a,a),0));
        System.out.println("PvP client loading gate, timeout, replay protection and codec regression checks passed");
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid client-loading data accepted");
    }
}
