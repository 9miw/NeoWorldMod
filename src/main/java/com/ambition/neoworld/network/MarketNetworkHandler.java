package com.ambition.neoworld.network;

import java.io.IOException;
import java.sql.SQLException;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.market.JdbcMarketRepository;
import com.ambition.neoworld.market.MarketPage;
import com.ambition.neoworld.market.MarketResult;
import com.ambition.neoworld.market.MarketService;
import com.ambition.neoworld.permission.ModPermissions;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class MarketNetworkHandler {
    private static final int PAGE_SIZE = 36;

    private MarketNetworkHandler() {}

    public static void handleQuery(MarketQueryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(), ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(MarketPagePayload.error(payload.requestId(), "ไม่มีสิทธิ์ใช้งานตลาด"));
            return;
        }
        try {
            int pageNumber = Math.max(1, payload.page());
            MarketPage page = JdbcMarketRepository.query(pageNumber, PAGE_SIZE, payload.category(), payload.search(),
                    payload.sort(), player.serverLevel().getGameTime(), player.registryAccess());
            context.reply(MarketPagePayload.fromListings(payload.requestId(), page.page(), page.totalPages(),
                    page.totalListings(), player.serverLevel().getGameTime(), page.listings()));
        } catch (SQLException | IOException exception) {
            NeoWorld.LOGGER.error("Could not query market page for {}", player.getUUID(), exception);
            context.reply(MarketPagePayload.error(payload.requestId(), "ฐานข้อมูลตลาดขัดข้อง"));
        }
    }

    public static void handleBuy(MarketBuyPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(), ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(new MarketActionResultPayload(false, "ไม่มีสิทธิ์ใช้งานตลาด"));
            return;
        }
        MarketResult result = MarketService.buy(player, payload.listingId());
        context.reply(new MarketActionResultPayload(result == MarketResult.SUCCESS, message(result)));
    }

    private static String message(MarketResult result) {
        return switch (result) {
            case SUCCESS -> "ซื้อสินค้าสำเร็จ";
            case LISTING_NOT_FOUND -> "ประกาศนี้ถูกซื้อหรือยกเลิกแล้ว";
            case LISTING_EXPIRED -> "ประกาศหมดอายุแล้ว";
            case CANNOT_BUY_OWN_LISTING -> "ไม่สามารถซื้อประกาศของตัวเองได้";
            case INSUFFICIENT_FUNDS -> "ยอด Silver ไม่เพียงพอ";
            case BALANCE_OVERFLOW -> "ยอดเงินผู้ขายเต็ม ไม่สามารถซื้อได้";
            case DATABASE_ERROR -> "ฐานข้อมูลตลาดขัดข้อง";
            default -> "ไม่สามารถทำรายการได้";
        };
    }
}
