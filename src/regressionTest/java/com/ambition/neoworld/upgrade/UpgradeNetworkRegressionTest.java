package com.ambition.neoworld.upgrade;

import com.ambition.neoworld.network.upgrade.UpgradeSubmitPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class UpgradeNetworkRegressionTest {
    public static void main(String[] args) {
        for (var value : new UpgradeSubmitPayload[] {
                new UpgradeSubmitPayload(1, 0, 0),
                new UpgradeSubmitPayload(127, Long.MIN_VALUE, Integer.MIN_VALUE),
                new UpgradeSubmitPayload(255, Long.MAX_VALUE, Integer.MAX_VALUE)}) {
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try {
                UpgradeSubmitPayload.STREAM_CODEC.encode(buffer, value);
                if (!value.equals(UpgradeSubmitPayload.STREAM_CODEC.decode(buffer)) || buffer.isReadable()) {
                    throw new AssertionError("Upgrade payload lost container/session/revision data");
                }
            } finally { buffer.release(); }
        }
        var shortBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            shortBuffer.writeVarInt(1);
            try {
                UpgradeSubmitPayload.STREAM_CODEC.decode(shortBuffer);
                throw new AssertionError("Truncated request accepted");
            } catch (IndexOutOfBoundsException expected) {}
        } finally { shortBuffer.release(); }
        UpgradeRulesRegressionTest.main(args);
        UpgradeBonusRegressionTest.main(args);
        System.out.println("Upgrade payload round-trip and truncation checks passed");
    }
}
