package com.ambition.neoworld.command;

import java.util.Comparator;
import java.util.List;

import com.ambition.neoworld.market.MarketListing;
import com.ambition.neoworld.market.MarketResult;
import com.ambition.neoworld.market.MarketService;
import com.ambition.neoworld.permission.ModPermissions;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import com.ambition.neoworld.network.OpenMarketPayload;

/** Player-facing commands for exercising the market transaction core. */
public final class MarketCommand {
    private static final int PAGE_SIZE = 10;
    private static final int DEFAULT_COUNT = 1;
    private static final int DEFAULT_DURATION_MINUTES = 48 * 60;
    private static final int MAX_DURATION_MINUTES = 30 * 24 * 60;
    private static final long TICKS_PER_MINUTE = 20L * 60L;

    private MarketCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("market")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_MARKET_USE, 0))
                .executes(context -> openMarket(context.getSource().getPlayerOrException()))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource().getPlayerOrException())))
                .then(Commands.literal("list")
                        .executes(context -> showListings(context.getSource(), 1, false))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> showListings(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page"), false))))
                .then(Commands.literal("my")
                        .executes(context -> showListings(context.getSource(), 1, true))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> showListings(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page"), true))))
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", LongArgumentType.longArg(1))
                                .executes(context -> sell(context.getSource(),
                                        LongArgumentType.getLong(context, "price"), DEFAULT_COUNT, DEFAULT_DURATION_MINUTES))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                        .executes(context -> sell(context.getSource(),
                                                LongArgumentType.getLong(context, "price"),
                                                IntegerArgumentType.getInteger(context, "count"),
                                                DEFAULT_DURATION_MINUTES))
                                        .then(Commands.argument("durationMinutes",
                                                        IntegerArgumentType.integer(1, MAX_DURATION_MINUTES))
                                                .executes(context -> sell(context.getSource(),
                                                        LongArgumentType.getLong(context, "price"),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        IntegerArgumentType.getInteger(context, "durationMinutes")))))))
                .then(Commands.literal("buy")
                        .then(Commands.argument("listingId", LongArgumentType.longArg(1))
                                .executes(context -> buy(context.getSource(),
                                        LongArgumentType.getLong(context, "listingId")))))
                .then(Commands.literal("cancel")
                        .then(Commands.argument("listingId", LongArgumentType.longArg(1))
                                .executes(context -> cancel(context.getSource(),
                                        LongArgumentType.getLong(context, "listingId")))))
                .then(Commands.literal("claim")
                        .executes(context -> claim(context.getSource())));
    }

    private static int openMarket(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, OpenMarketPayload.INSTANCE);
        return 1;
    }

    private static int sell(CommandSourceStack source, long price, int count, int durationMinutes)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("[Market] กรุณาถือไอเทมที่ต้องการขายในมือหลัก"));
            return 0;
        }
        if (count > held.getCount()) {
            source.sendFailure(Component.literal("[Market] จำนวนที่ระบุมากกว่าไอเทมในมือ"));
            return 0;
        }

        Component itemName = held.getHoverName().copy();

        MarketService.ListingResult result = MarketService.createListing(player, player.getInventory().selected,
                count, price, MarketCommandRules.durationTicks(durationMinutes));
        if (result.result() != MarketResult.SUCCESS) return sendFailure(source, result.result());

        long id = result.listingId().orElseThrow();
        source.sendSuccess(() -> Component.literal("[Market] ลงขาย ")
                .withStyle(ChatFormatting.GREEN)
                .append(itemName.copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" x" + count + " ราคา " + formatSilver(price)
                        + " Silver (ID " + id + ")").withStyle(ChatFormatting.GREEN)), false);
        return 1;
    }

    private static int buy(CommandSourceStack source, long listingId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MarketResult result = MarketService.buy(source.getPlayerOrException(), listingId);
        if (result != MarketResult.SUCCESS) return sendFailure(source, result);
        source.sendSuccess(() -> Component.literal("[Market] ซื้อประกาศ ID " + listingId + " สำเร็จ")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int cancel(CommandSourceStack source, long listingId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MarketResult result = MarketService.cancel(source.getPlayerOrException(), listingId);
        if (result != MarketResult.SUCCESS) return sendFailure(source, result);
        source.sendSuccess(() -> Component.literal("[Market] ยกเลิกประกาศ ID " + listingId + " แล้ว")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int claim(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        try {
            long pendingSilver = MarketService.pendingSilver(player);
            int pendingStacks = MarketService.pendingItemCount(player);
            MarketResult result = MarketService.claim(player);
            if (result != MarketResult.SUCCESS) return sendFailure(source, result);
            source.sendSuccess(() -> Component.literal("[Market] รับเงิน " + formatSilver(pendingSilver)
                    + " Silver และสินค้า " + pendingStacks + " กองแล้ว").withStyle(ChatFormatting.GREEN), false);
            if (MarketService.pendingItemCount(player) > 0) {
                source.sendSuccess(() -> Component.literal("[Market] กระเป๋าเต็ม สินค้าที่ยังรับไม่ได้ถูกเก็บไว้ในตลาด")
                        .withStyle(ChatFormatting.YELLOW), false);
            }
            return 1;
        } catch (java.sql.SQLException | java.io.IOException exception) {
            return sendFailure(source, MarketResult.DATABASE_ERROR);
        }
    }

    private static int showListings(CommandSourceStack source, int requestedPage, boolean ownOnly)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long now = player.serverLevel().getGameTime();
        List<MarketListing> listings;
        try {
            listings = MarketService.listings(source.getServer()).stream()
                    .filter(listing -> !listing.isExpired(now))
                    .filter(listing -> !ownOnly || listing.sellerId().equals(player.getUUID()))
                    .sorted(Comparator.comparingLong(MarketListing::id))
                    .toList();
        } catch (java.sql.SQLException | java.io.IOException exception) {
            return sendFailure(source, MarketResult.DATABASE_ERROR);
        }
        int totalPages = MarketCommandRules.totalPages(listings.size(), PAGE_SIZE);
        int page = MarketCommandRules.clampPage(requestedPage, totalPages);
        String title = ownOnly ? "ประกาศของฉัน" : "ตลาดกลาง";
        source.sendSuccess(() -> Component.literal("=== " + title + " หน้า " + page + "/" + totalPages + " ===")
                .withStyle(ChatFormatting.GOLD), false);

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, listings.size());
        if (start >= end) {
            source.sendSuccess(() -> Component.literal("ยังไม่มีประกาศขาย").withStyle(ChatFormatting.GRAY), false);
        } else {
            for (MarketListing listing : listings.subList(start, end)) {
                source.sendSuccess(() -> listingLine(listing, player, now), false);
            }
        }
        showPageControls(player, page, totalPages, ownOnly);
        return listings.size();
    }

    private static MutableComponent listingLine(MarketListing listing, ServerPlayer viewer, long now) {
        ItemStack item = listing.item();
        long minutes = Math.max(0L, (listing.expiresAtGameTime() - now + TICKS_PER_MINUTE - 1) / TICKS_PER_MINUTE);
        MutableComponent line = Component.literal("#" + listing.id() + " ").withStyle(ChatFormatting.YELLOW)
                .append(item.getHoverName().copy().withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" x" + item.getCount() + " | " + formatSilver(listing.priceSilver())
                        + " Silver | " + listing.sellerName() + " | " + minutes + " นาที")
                        .withStyle(ChatFormatting.GRAY));
        String command = listing.sellerId().equals(viewer.getUUID())
                ? "/market cancel " + listing.id()
                : "/market buy " + listing.id();
        return line.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("คลิกเพื่อเติมคำสั่ง: " + command))));
    }

    private static void showPageControls(ServerPlayer player, int page, int totalPages, boolean ownOnly) {
        if (totalPages <= 1) return;
        String base = ownOnly ? "/market my " : "/market list ";
        MutableComponent controls = Component.literal("หน้า: ").withStyle(ChatFormatting.GRAY);
        if (page > 1) controls.append(pageButton(base + (page - 1), "[ก่อนหน้า]"));
        if (page > 1 && page < totalPages) controls.append(Component.literal(" "));
        if (page < totalPages) controls.append(pageButton(base + (page + 1), "[ถัดไป]"));
        player.sendSystemMessage(controls);
    }

    private static MutableComponent pageButton(String command, String label) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    private static int showHelp(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("=== Market Commands ===").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(helpButton("/market list", "ดูประกาศทั้งหมด"));
        player.sendSystemMessage(helpButton("/market sell 100 1 2880", "ขายของในมือ"));
        player.sendSystemMessage(helpButton("/market my", "ดูประกาศของฉัน"));
        player.sendSystemMessage(helpButton("/market claim", "รับเงินและสินค้าค้าง"));
        return 1;
    }

    private static MutableComponent helpButton(String command, String description) {
        return Component.literal("  " + command).withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))))
                .append(Component.literal(" - " + description).withStyle(ChatFormatting.WHITE));
    }

    private static int sendFailure(CommandSourceStack source, MarketResult result) {
        source.sendFailure(Component.literal("[Market] " + switch (result) {
            case INVALID_ITEM -> "ไอเทมไม่ถูกต้อง";
            case INVALID_PRICE -> "ราคาต้องมากกว่า 0";
            case INVALID_DURATION -> "ระยะเวลาประกาศไม่ถูกต้อง";
            case INVALID_RATE -> "การตั้งค่าภาษีไม่ถูกต้อง";
            case LISTING_NOT_FOUND -> "ไม่พบประกาศ หรือประกาศนี้ไม่ใช่ของคุณ";
            case LISTING_EXPIRED -> "ประกาศหมดอายุแล้ว กรุณารอระบบคืนสินค้า";
            case CANNOT_BUY_OWN_LISTING -> "ไม่สามารถซื้อประกาศของตัวเองได้";
            case INSUFFICIENT_FUNDS -> "ยอด Silver ไม่เพียงพอ";
            case INVENTORY_CHANGED -> "ไอเทมในมือไม่ตรงกับจำนวนที่ระบุ";
            case BALANCE_OVERFLOW -> "ยอดเงินปลายทางเต็ม ไม่สามารถทำรายการได้";
            case MARKET_CAPACITY_REACHED -> "ตลาดไม่สามารถสร้างประกาศเพิ่มได้";
            case DATABASE_ERROR -> "ฐานข้อมูลขัดข้อง กรุณาลองใหม่หรือติดต่อผู้ดูแล";
            case NOTHING_TO_CLAIM -> "ไม่มีเงินหรือสินค้าค้างรับ";
            case SUCCESS -> "สำเร็จ";
        }));
        return 0;
    }

    private static String formatSilver(long amount) {
        return String.format("%,d", amount);
    }
}
