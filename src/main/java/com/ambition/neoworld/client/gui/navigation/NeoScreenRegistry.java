package com.ambition.neoworld.client.gui.navigation;

import com.ambition.neoworld.client.gui.fashion.FashionScreenController;
import com.ambition.neoworld.client.gui.ranking.RankingScreen;

import java.util.List;
import net.minecraft.client.Minecraft;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NeoScreenRegistry {
    private static final List<NeoScreenDestination> DESTINATIONS = List.of(
            enabled("profile", "โปรไฟล์ผู้เล่น", "ข้อมูลและค่าสถานะผู้เล่น", () -> new ItemStack(Items.PLAYER_HEAD),
                    () -> {
                        var player = net.minecraft.client.Minecraft.getInstance().player;
                        if (player != null) net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new com.ambition.neoworld.network.player.PlayerActionPayload(
                                        com.ambition.neoworld.network.PlayerProfilePayload.COMMAND_REQUEST,
                                        player.getUUID(), com.ambition.neoworld.network.player.PlayerActionPayload.Action.PROFILE));
                    }),
            enabled("titles", "ฉายา", "จัดการฉายาของผู้เล่น", () -> new ItemStack(Items.NAME_TAG),
                    com.ambition.neoworld.client.gui.title.TitleScreenController::open),
            enabled("market", "ตลาด", "ซื้อขายและจัดการรายการสินค้า", () -> new ItemStack(Items.CHEST),
                    com.ambition.neoworld.client.gui.market.MarketScreenController::open),
            enabled("fashion", "แฟชั่น", "จัดการเครื่องแต่งกาย", () -> new ItemStack(Items.LEATHER_CHESTPLATE),
                    FashionScreenController::open),
            enabled("party", "ปาร์ตี้", "สมาชิกและคำเชิญปาร์ตี้", () -> new ItemStack(Items.PLAYER_HEAD),
                    com.ambition.neoworld.client.gui.party.PartyScreenController::open),
            enabled("dungeon", "ดันเจี้ยน", "ค้นหาและจัดการห้องดันเจี้ยน", () -> new ItemStack(Items.TRIAL_KEY),
                    com.ambition.neoworld.client.gui.dungeon.DungeonBrowserScreenController::open),
            enabled("pvp", "PvP", "ห้องดวลและจัดอันดับ 1vs1", () -> new ItemStack(Items.DIAMOND_SWORD),
                    com.ambition.neoworld.client.gui.pvp.PvpScreenController::open),
            enabled("activity", "กิจกรรม", "เช็กอินรายวัน รายเดือน และรางวัลออนไลน์", () -> new ItemStack(Items.CHEST),
                    com.ambition.neoworld.client.gui.activity.ActivityScreen::open),
            enabled("ranking", "อันดับ", "ดูตารางจัดอันดับผู้เล่น", () -> new ItemStack(Items.GOLD_INGOT),
                    () -> RankingScreen.open(Minecraft.getInstance().screen))
    );

    private NeoScreenRegistry() {
    }

    public static List<NeoScreenDestination> destinations() {
        return DESTINATIONS;
    }

    private static NeoScreenDestination enabled(String id, String title, String description,
                                                java.util.function.Supplier<ItemStack> icon, Runnable opener) {
        return new NeoScreenDestination(id, Component.literal(title), Component.literal(description), icon, true, opener);
    }

}
