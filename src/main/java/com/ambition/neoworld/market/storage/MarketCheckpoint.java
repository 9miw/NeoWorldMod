package com.ambition.neoworld.market.storage;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** NeoForge copies PlayerPersisted across death/End-return replacement players. */
public final class MarketCheckpoint {
    private static final String KEY = "neoworld:market_checkpoint";
    private MarketCheckpoint() {}

    public static void captureWallet(CompoundTag tag, com.ambition.neoworld.data.PlayerData wallet) {
        tag.putLong("silver", wallet.getSilver());
        tag.putLong("gold", wallet.getLegacyRetiredBalance());
        tag.putLong("diamond", wallet.getDiamond());
    }

    public static com.ambition.neoworld.data.PlayerData restoreWallet(CompoundTag tag, com.ambition.neoworld.data.PlayerData current) {
        return new com.ambition.neoworld.data.PlayerData(current.getLevel(), current.getExp(), tag.getLong("silver"),
                tag.contains("gold") ? tag.getLong("gold") : current.getLegacyRetiredBalance(),
                tag.contains("diamond") ? tag.getLong("diamond") : current.getDiamond(), current.getTotalExp());
    }

    public static boolean matches(CompoundTag persistent, UUID operation) {
        return operation.toString().equals(persistent.getCompound(Player.PERSISTED_NBT_TAG).getString(KEY));
    }

    public static void write(CompoundTag persistent, UUID operation) {
        CompoundTag retained = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        retained.putString(KEY, operation.toString());
        persistent.put(Player.PERSISTED_NBT_TAG, retained);
    }

    /** Locate the NeoForge persistent-data compound without assuming its outer save key. */
    public static boolean inPlayerSave(CompoundTag tag, UUID operation) {
        return inPlayerSave(tag, operation, 0);
    }

    private static boolean inPlayerSave(CompoundTag tag, UUID operation, int depth) {
        if (matches(tag, operation)) return true;
        if (depth >= 8) return false;
        for (String key : tag.getAllKeys())
            if (tag.get(key) instanceof CompoundTag nested && inPlayerSave(nested, operation, depth + 1)) return true;
        return false;
    }
}
