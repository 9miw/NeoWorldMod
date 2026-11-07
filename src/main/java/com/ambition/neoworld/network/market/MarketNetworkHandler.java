package com.ambition.neoworld.network.market;

import java.io.IOException;
import java.sql.SQLException;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.market.storage.JdbcMarketRepository;
import com.ambition.neoworld.market.model.MarketPage;
import com.ambition.neoworld.market.MarketResult;
import com.ambition.neoworld.market.service.MarketDatabaseExecutor;
import com.ambition.neoworld.market.service.MarketService;
import com.ambition.neoworld.market.menu.MarketSellMenu;
import com.ambition.neoworld.permission.ModPermissions;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class MarketNetworkHandler {
    private static final int PAGE_SIZE = 36;
    private static final int MANAGEMENT_PAGE_SIZE = 6;

    private MarketNetworkHandler() {}

    public static void handleQuery(MarketQueryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(), ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(MarketPagePayload.error(payload.requestId(), "ไม่มีสิทธิ์เปิดหน้าตลาด"));
            return;
        }
        long gameTime = player.serverLevel().getGameTime();
        var registryAccess = player.registryAccess();
        var playerId = player.getUUID();
        MarketDatabaseExecutor.execute(() -> {
            try {
                int pageNumber = Math.max(1, payload.page());
                MarketPage page = JdbcMarketRepository.query(pageNumber, PAGE_SIZE, payload.category(), payload.search(),
                        payload.sort(), gameTime, registryAccess);
                player.server.execute(() -> {
                    context.reply(MarketPagePayload.fromListings(payload.requestId(), page.page(), page.totalPages(),
                            page.totalListings(), gameTime, page.listings()));
                });
            } catch (SQLException | IOException exception) {
                NeoWorld.LOGGER.error("Could not query market page for {}", playerId, exception);
                player.server.execute(() -> {
                    context.reply(MarketPagePayload.error(payload.requestId(), "ฐานข้อมูลตลาดขัดข้อง"));
                });
            }
        });
    }

    public static void handleBuy(MarketBuyPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(), ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(new MarketActionResultPayload(false, "ไม่มีสิทธิ์ซื้อสินค้าผ่านหน้าตลาด"));
            return;
        }
        if (payload.listingId() <= 0 || payload.quantity() <= 0) {
            context.reply(new MarketActionResultPayload(false, "ข้อมูลการซื้อไม่ถูกต้อง"));
            return;
        }
        MarketService.buyAsync(player, payload.operationId(), payload.listingId(), payload.quantity(), result -> {
            context.reply(new MarketActionResultPayload(result == MarketResult.SUCCESS, message(result)));
        });
    }

    public static void handleManagementQuery(MarketManagementQueryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(),
                        ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(MarketManagementPagePayload.error(payload.requestId(), "ไม่มีสิทธิ์จัดการแผงขาย"));
            return;
        }
        long gameTime = player.serverLevel().getGameTime();
        var registryAccess = player.registryAccess();
        var playerId = player.getUUID();
        MarketDatabaseExecutor.execute(() -> {
            try {
                var settings = JdbcMarketRepository.settings();
                var page = JdbcMarketRepository.managementPage(playerId, Math.max(1, payload.page()), MANAGEMENT_PAGE_SIZE,
                        settings.salesTaxRateBps(), registryAccess);
                player.server.execute(() -> {
                    context.reply(MarketManagementPagePayload.fromPage(payload.requestId(),
                            gameTime, page));
                });
            } catch (SQLException | IOException exception) {
                NeoWorld.LOGGER.error("Could not query market management page for {}", playerId, exception);
                player.server.execute(() -> {
                    context.reply(MarketManagementPagePayload.error(payload.requestId(), "ฐานข้อมูลตลาดขัดข้อง"));
                });
            }
        });
    }

    public static void handleManagementAction(MarketManagementActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(),
                        ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(new MarketActionResultPayload(false, "ไม่มีสิทธิ์จัดการแผงขาย"));
            return;
        }
        switch (payload.action()) {
            case CANCEL_LISTING -> {
                if (payload.targetId() <= 0) {
                    context.reply(new MarketActionResultPayload(false, "ไม่พบรายการ"));
                    return;
                }
                MarketService.cancelAsync(player, payload.operationId(), payload.targetId(), result -> {
                    context.reply(new MarketActionResultPayload(result == MarketResult.SUCCESS,
                            managementMessage(payload.action(), result)));
                });
            }
            case CLAIM_SALE -> {
                if (payload.targetId() <= 0) {
                    context.reply(new MarketActionResultPayload(false, "ไม่มีเงินจากรายการนี้ให้รับ"));
                    return;
                }
                MarketService.claimSaleAsync(player, payload.operationId(), payload.targetId(), result -> {
                    context.reply(new MarketActionResultPayload(result == MarketResult.SUCCESS,
                            managementMessage(payload.action(), result)));
                });
            }
            case CLAIM_ALL -> {
                MarketService.claimAsync(player, payload.operationId(), result -> {
                    context.reply(new MarketActionResultPayload(result == MarketResult.SUCCESS,
                            managementMessage(payload.action(), result)));
                });
            }
            case DELETE_HISTORY -> {
                if (payload.targetId() <= 0) {
                    context.reply(new MarketActionResultPayload(false, "ไม่พบประวัติ"));
                    return;
                }
                MarketService.deleteClaimedSaleAsync(player, payload.targetId(), result -> {
                    context.reply(new MarketActionResultPayload(result == MarketResult.SUCCESS,
                            managementMessage(payload.action(), result)));
                });
            }
        }
    }

    public static void handleSellOpen(MarketSellOpenPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(),
                        ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(new MarketActionResultPayload(false, "ไม่มีสิทธิ์เปิดหน้าวางขาย"));
            return;
        }
        MarketDatabaseExecutor.execute(() -> {
            try {
                JdbcMarketRepository.MarketSettings settings = JdbcMarketRepository.settings();
                player.server.execute(() -> {
                    player.openMenu(new SimpleMenuProvider((containerId, inventory, owner) ->
                                    new MarketSellMenu(containerId, inventory, settings.listingFeeSilver(),
                                            settings.salesTaxRateBps()),
                                    Component.literal("วางขายสินค้า")), buffer -> {
                        buffer.writeVarLong(settings.listingFeeSilver());
                        buffer.writeVarInt(settings.salesTaxRateBps());
                    });
                });
            } catch (SQLException exception) {
                NeoWorld.LOGGER.error("Could not open market sell screen for {}", player.getUUID(), exception);
                player.server.execute(() -> {
                    context.reply(new MarketActionResultPayload(false, "ฐานข้อมูลตลาดขัดข้อง"));
                });
            }
        });
    }

    public static void handleSellSubmit(MarketSellSubmitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !ModPermissions.hasPermission(player.createCommandSourceStack(),
                        ModPermissions.COMMAND_MARKET_USE, 0)) {
            context.reply(new MarketActionResultPayload(false, "ไม่มีสิทธิ์วางขายสินค้า"));
            return;
        }
        if (payload.containerId() != player.containerMenu.containerId
                || !(player.containerMenu instanceof MarketSellMenu menu)) {
            context.reply(new MarketActionResultPayload(false, "หน้าวางขายหมดอายุแล้ว"));
            return;
        }
        if (payload.quantity() <= 0 || payload.unitPriceSilver() <= 0
                || payload.durationTicks() <= 0 || payload.durationTicks() > MarketSellMenu.MAX_DURATION_TICKS) {
            context.reply(new MarketActionResultPayload(false, "ข้อมูลการวางขายไม่ถูกต้อง"));
            return;
        }
        ItemStack source = menu.getListingItem();
        if (source.isEmpty() || source.getCount() < payload.quantity()) {
            context.reply(new MarketActionResultPayload(false, "จำนวนสินค้าไม่ถูกต้อง หรือสินค้าเปลี่ยนไปแล้ว"));
            return;
        }
        MarketService.createListingAsync(player, payload.operationId(), source, payload.quantity(),
                payload.unitPriceSilver(), payload.durationTicks(), result -> {
                    if (result.result() == MarketResult.SUCCESS) {
                        menu.broadcastChanges();
                    }
                    context.reply(new MarketActionResultPayload(result.result() == MarketResult.SUCCESS,
                            listingMessage(result.result())));
                });
    }

    private static String message(MarketResult result) {
        return switch (result) {
            case SUCCESS -> "ซื้อสินค้าสำเร็จ ได้รับสินค้าเข้าสู่กล่องตลาดแล้ว กรุณากดรับสินค้า";
            case LISTING_NOT_FOUND -> "ประกาศนี้ถูกซื้อหรือยกเลิกแล้ว";
            case LISTING_EXPIRED -> "ประกาศหมดอายุแล้ว";
            case CANNOT_BUY_OWN_LISTING -> "ไม่สามารถซื้อประกาศของตัวเองได้";
            case INSUFFICIENT_FUNDS -> "ยอด Silver ไม่เพียงพอ";
            case INVALID_QUANTITY -> "จำนวนซื้อไม่ถูกต้อง";
            case INSUFFICIENT_STOCK -> "สินค้าเหลือไม่พอตามจำนวนที่เลือก";
            case BALANCE_OVERFLOW -> "ยอดเงินผู้ขายเต็ม ไม่สามารถซื้อได้";
            case MARKET_BUSY -> "มีรายการกำลังดำเนินการ กรุณารอสักครู่";
            case DATABASE_ERROR -> "ฐานข้อมูลตลาดขัดข้อง";
            default -> "ไม่สามารถทำรายการได้";
        };
    }

    private static String managementMessage(MarketManagementActionPayload.Action action, MarketResult result) {
        if (result == MarketResult.SUCCESS) {
            return switch (action) {
                case CANCEL_LISTING -> "ยกเลิกรายการและส่งสินค้าคืนกล่องตลาดแล้ว";
                case CLAIM_SALE -> "รับเงินจากรายการสำเร็จ";
                case CLAIM_ALL -> "รับเงินและสินค้าที่รอทั้งหมดแล้ว";
                case DELETE_HISTORY -> "ลบประวัติการขายแล้ว";
            };
        }
        return switch (result) {
            case LISTING_NOT_FOUND -> "ไม่พบรายการ หรือรายการนี้ไม่ใช่ของคุณ";
            case NOTHING_TO_CLAIM -> "ไม่มีเงินหรือสินค้าให้รับ";
            case BALANCE_OVERFLOW -> "ยอด Silver เต็ม ยังเก็บเงินไว้ในตลาด";
            case MARKET_BUSY -> "มีรายการกำลังดำเนินการ กรุณารอสักครู่";
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
            case MARKET_BUSY -> "มีรายการกำลังดำเนินการ กรุณารอสักครู่";
            case DATABASE_ERROR -> "ฐานข้อมูลตลาดขัดข้อง สินค้ายังอยู่ในช่องขาย";
            default -> "ไม่สามารถวางขายสินค้าได้";
        };
    }
}
