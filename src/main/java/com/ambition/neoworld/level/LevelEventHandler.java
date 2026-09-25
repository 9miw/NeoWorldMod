package com.ambition.neoworld.level;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.economy.PlayerEconomyManager;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// คลาสจัดการ Event ของระบบเลเวลและการซิงค์ข้อมูลผู้เล่น
@EventBusSubscriber(modid = NeoWorld.MODID)
public class LevelEventHandler {

    /**
     * ซิงค์ข้อมูลเลเวลและยอดเงินเมื่อผู้เล่นล็อกอินเข้าเซิร์ฟเวอร์
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerLevelManager.syncToClient(player);
            PlayerEconomyManager.syncToClient(player);
        }
    }

    /**
     * ซิงค์ข้อมูลเลเวลและยอดเงินเมื่อผู้เล่นเกิดใหม่ (Respawn)
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerLevelManager.syncToClient(player);
            PlayerEconomyManager.syncToClient(player);
        }
    }

    /**
     * ซิงค์ข้อมูลเลเวลและยอดเงินเมื่อผู้เล่นเปลี่ยนมิติ (Dimension Change)
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerLevelManager.syncToClient(player);
            PlayerEconomyManager.syncToClient(player);
        }
    }
}
