package com.ambition.neoworld.registry;

import java.util.function.Supplier;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.trade.menu.TradeMenu;
import com.ambition.neoworld.market.menu.MarketSellMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, NeoWorld.MODID);
    public static final Supplier<MenuType<TradeMenu>> TRADE = MENUS.register("trade",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new TradeMenu(id, inventory, data.readUUID())));
    public static final Supplier<MenuType<MarketSellMenu>> MARKET_SELL = MENUS.register("market_sell",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new MarketSellMenu(
                    id, inventory, data.readVarLong(), data.readVarInt())));
    private ModMenus() {}
}
