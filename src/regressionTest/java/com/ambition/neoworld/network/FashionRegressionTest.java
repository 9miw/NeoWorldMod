package com.ambition.neoworld.network;

import java.util.Map;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.ambition.neoworld.network.fashion.FashionEquipPayload;
import com.ambition.neoworld.network.fashion.SyncFashionPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class FashionRegressionTest {
    public static void main(String[] args) {
        var request = new FashionEquipPayload(FashionSlot.BACK, ResourceLocation.parse("neoworld:example_back"));
        var buffer = buffer();
        try {
            FashionEquipPayload.STREAM_CODEC.encode(buffer, request);
            check(FashionEquipPayload.STREAM_CODEC.decode(buffer).equals(request), "Equip request must preserve wardrobe ID");
        } finally { buffer.release(); }
        for (boolean self : new boolean[] {false, true}) {
            buffer = buffer();
            try {
                var payload = new SyncFashionPayload(37, Map.of(), self, Map.of());
                SyncFashionPayload.STREAM_CODEC.encode(buffer, payload);
                check(SyncFashionPayload.STREAM_CODEC.decode(buffer).equals(payload), "Owner and observer sync must round trip");
            } finally { buffer.release(); }
        }
        for (int count : new int[] {-1, 4097}) {
            buffer = buffer();
            try {
                buffer.writeVarInt(37);
                buffer.writeVarInt(0);
                buffer.writeBoolean(true);
                buffer.writeVarInt(count);
                boolean rejected = false;
                try { SyncFashionPayload.STREAM_CODEC.decode(buffer); }
                catch (IllegalArgumentException expected) { rejected = true; }
                check(rejected, "Invalid wardrobe lengths must be rejected");
            } finally { buffer.release(); }
        }
        System.out.println("Fashion network regression checks passed");
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
