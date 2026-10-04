package com.ambition.neoworld.client.gui.navigation;

import com.ambition.neoworld.client.gui.fashion.FashionScreenController;
import com.ambition.neoworld.client.gui.market.MarketScreenController;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NeoScreenRegistry {
    private static final List<NeoScreenDestination> DESTINATIONS = List.of(
            enabled("market", "Market", "Browse listings and buy items", () -> new ItemStack(Items.CHEST),
                    MarketScreenController::open),
            enabled("fashion", "Fashion", "Equip cosmetic items", () -> new ItemStack(Items.LEATHER_CHESTPLATE),
                    FashionScreenController::open),
            disabled("party", "Party", "Party tools and invites", () -> new ItemStack(Items.PLAYER_HEAD)),
            disabled("dungeon", "Dungeon", "Rooms, runs, and recovery", () -> new ItemStack(Items.TRIAL_KEY)),
            disabled("level_economy", "Level / Economy", "Progress and balances", () -> new ItemStack(Items.EXPERIENCE_BOTTLE)),
            disabled("future", "Future Systems", "Reserved expansion slot", () -> new ItemStack(Items.NETHER_STAR))
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
