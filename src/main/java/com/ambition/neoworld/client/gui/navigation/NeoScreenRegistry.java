package com.ambition.neoworld.client.gui.navigation;

import com.ambition.neoworld.client.gui.fashion.FashionScreenController;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NeoScreenRegistry {
    private static final List<NeoScreenDestination> DESTINATIONS = List.of(
            enabled("pvp", "PvP", "ห้องดวลและจัดอันดับ 1vs1", () -> new ItemStack(Items.DIAMOND_SWORD),
                    com.ambition.neoworld.client.gui.pvp.PvpScreenController::open),
            enabled("fashion", "Fashion", "Equip cosmetic items", () -> new ItemStack(Items.LEATHER_CHESTPLATE),
                    FashionScreenController::open),
            disabled("party", "Party", "Party tools and invites", () -> new ItemStack(Items.PLAYER_HEAD)),
            disabled("dungeon", "Dungeon", "Rooms, runs, and recovery", () -> new ItemStack(Items.TRIAL_KEY)),
            disabled("level_economy", "Level / Economy", "Progress and balances", () -> new ItemStack(Items.EXPERIENCE_BOTTLE)),
            enabled("activity", "กิจกรรม", "เช็กอินรายวัน รายเดือน และรางวัลออนไลน์", () -> new ItemStack(Items.CHEST),
                    com.ambition.neoworld.client.gui.activity.ActivityScreen::open)
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

    private static NeoScreenDestination disabled(String id, String title, String description,
                                                 java.util.function.Supplier<ItemStack> icon) {
        return new NeoScreenDestination(id, Component.literal(title), Component.literal(description), icon, false, () -> {});
    }
}
