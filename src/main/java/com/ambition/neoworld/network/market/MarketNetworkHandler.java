package com.ambition.neoworld.network.market;

import java.io.IOException;
import java.sql.SQLException;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.market.storage.JdbcMarketRepository;
import com.ambition.neoworld.market.model.MarketPage;
import com.ambition.neoworld.market.MarketResult;
import com.ambition.neoworld.market.service.MarketService;
import com.ambition.neoworld.market.menu.MarketSellMenu;
import com.ambition.neoworld.permission.ModPermissions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
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
        MarketResult result = MarketService.buy(player, payload.listingId(), payload.quantity());
        context.reply(new MarketActionResultPayload(result == MarketResult.SUCCESS, message(result)));
    }

    public static void handleManagementQuery(MarketManagementQueryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(),
                        ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(MarketManagementPagePayload.error(payload.requestId(), "ไม่มีสิทธิ์ใช้งานตลาด"));
            return;
        }
        try {
            var page = MarketService.managementPage(player, Math.max(1, payload.page()), 8);
            context.reply(MarketManagementPagePayload.fromPage(payload.requestId(),
                    player.serverLevel().getGameTime(), page));
        } catch (SQLException | IOException exception) {
            NeoWorld.LOGGER.error("Could not query market management page for {}", player.getUUID(), exception);
            context.reply(MarketManagementPagePayload.error(payload.requestId(), "ฐานข้อมูลตลาดขัดข้อง"));
        }
    }

    public static void handleManagementAction(MarketManagementActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(),
                        ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(new MarketActionResultPayload(false, "ไม่มีสิทธิ์ใช้งานตลาด"));
            return;
        }
        MarketResult result = switch (payload.action()) {
            case CANCEL_LISTING -> payload.targetId() > 0
                    ? MarketService.cancel(player, payload.targetId()) : MarketResult.LISTING_NOT_FOUND;
            case CLAIM_SALE -> payload.targetId() > 0
                    ? MarketService.claimSale(player, payload.targetId()) : MarketResult.NOTHING_TO_CLAIM;
            case CLAIM_ALL -> MarketService.claim(player);
        };
        context.reply(new MarketActionResultPayload(result == MarketResult.SUCCESS,
                managementMessage(payload.action(), result)));
    }

    public static void handleSellOpen(MarketSellOpenPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(),
                        ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(new MarketActionResultPayload(false, "ไม่มีสิทธิ์ใช้งานตลาด"));
            return;
        }
        try {
            JdbcMarketRepository.MarketSettings settings = JdbcMarketRepository.settings();
            player.openMenu(new SimpleMenuProvider((containerId, inventory, owner) ->
                            new MarketSellMenu(containerId, inventory, settings.listingFeeSilver(),
                                    settings.salesTaxRateBps()),
                    Component.literal("วางขายสินค้า")), buffer -> {
                buffer.writeVarLong(settings.listingFeeSilver());
                buffer.writeVarInt(settings.salesTaxRateBps());
            });
        } catch (SQLException exception) {
            NeoWorld.LOGGER.error("Could not open market sell screen for {}", player.getUUID(), exception);
            context.reply(new MarketActionResultPayload(false, "ฐานข้อมูลตลาดขัดข้อง"));
        }
    }

    public static void handleSellSubmit(MarketSellSubmitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(),
                        ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(new MarketActionResultPayload(false, "ไม่มีสิทธิ์ใช้งานตลาด"));
            return;
        }
        if (payload.containerId() != player.containerMenu.containerId
                || !(player.containerMenu instanceof MarketSellMenu menu)) {
            context.reply(new MarketActionResultPayload(false, "หน้าวางขายหมดอายุแล้ว"));
            return;
        }
        MarketService.ListingResult result = menu.submit(
                player, payload.quantity(), payload.unitPriceSilver(), payload.durationTicks());
        context.reply(new MarketActionResultPayload(result.result() == MarketResult.SUCCESS,
                listingMessage(result.result())));
    }

    private static String message(MarketResult result) {
        return switch (result) {
            case SUCCESS -> "ซื้อสินค้าสำเร็จ";
            case LISTING_NOT_FOUND -> "ประกาศนี้ถูกซื้อหรือยกเลิกแล้ว";
            case LISTING_EXPIRED -> "ประกาศหมดอายุแล้ว";
            case CANNOT_BUY_OWN_LISTING -> "ไม่สามารถซื้อประกาศของตัวเองได้";
            case INSUFFICIENT_FUNDS -> "ยอด Silver ไม่เพียงพอ";
            case INVALID_QUANTITY -> "จำนวนซื้อไม่ถูกต้อง";
            case INSUFFICIENT_STOCK -> "สินค้าเหลือไม่พอตามจำนวนที่เลือก";
            case BALANCE_OVERFLOW -> "ยอดเงินผู้ขายเต็ม ไม่สามารถซื้อได้";
            case DATABASE_ERROR -> "ฐานข้อมูลตลาดขัดข้อง";
            default -> "ไม่สามารถทำรายการได้";
        };
    }

    private static String managementMessage(MarketManagementActionPayload.Action action, MarketResult result) {
        if (result == MarketResult.SUCCESS) {
            return switch (action) {
                case CANCEL_LISTING -> "ยกเลิกรายการและส่งสินค้าคืนคลังแล้ว";
                case CLAIM_SALE -> "รับเงินจากรายการสำเร็จ";
                case CLAIM_ALL -> "รับเงินที่รอทั้งหมดแล้ว";
            };
        }
        return switch (result) {
            case LISTING_NOT_FOUND -> "ไม่พบรายการ หรือรายการนี้ไม่ใช่ของคุณ";
            case NOTHING_TO_CLAIM -> "ไม่มีเงินจากรายการนี้ให้รับ";
            case BALANCE_OVERFLOW -> "ยอด Silver เต็ม ยังเก็บเงินไว้ในตลาด";
            case DATABASE_ERROR -> "ฐานข้อมูลตลาดขัดข้อง";
            default -> "ไม่สามารถทำรายการได้";
        };
    }

    private static String listingMessage(MarketResult result) {
        return switch (result) {
            case SUCCESS -> "เพิ่มสินค้าเข้าตลาดแล้ว";
            case INVALID_ITEM -> "กรุณาวางสินค้าที่ต้องการขาย";
            case INVALID_PRICE -> "ราคาต่อชิ้นไม่ถูกต้อง";
            case INVALID_QUANTITY, INVENTORY_CHANGED -> "จำนวนสินค้าไม่ถูกต้อง หรือสินค้าเปลี่ยนไปแล้ว";
            case INVALID_DURATION -> "ระยะเวลาขายไม่ถูกต้อง";
            case INSUFFICIENT_FUNDS -> "ยอด Silver ไม่พอสำหรับค่าลงขาย";
            case MARKET_CAPACITY_REACHED -> "รายการขายเต็มแล้ว";
            case DATABASE_ERROR -> "ฐานข้อมูลตลาดขัดข้อง สินค้ายังอยู่ในช่องขาย";
            default -> "ไม่สามารถวางขายสินค้าได้";
        };
    }
}
