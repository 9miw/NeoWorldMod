package com.ambition.neoworld.command.economy;

import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.economy.PlayerEconomyManager;
import com.ambition.neoworld.item.CoinItem;
import com.ambition.neoworld.permission.ModPermissions;
import com.ambition.neoworld.registry.ModAttachments;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import com.ambition.neoworld.economy.CurrencyMath;

// คำสั่งระบบเศรษฐกิจสำหรับเพิ่มเข้าไปใน command tree หลัก (รองรับ LuckPerms)
public final class EconomyCommand {

    private static final List<String> CURRENCY_TYPES = List.of("silver", "diamond");

    private EconomyCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand(String rootName) {
        return Commands.literal(rootName)
            .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_BALANCE, 0))
            // พิมพ์ /neow eco หรือ /neow economy เปล่าๆ -> แสดงยอดเงินของตนเองทันที
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                showBalance(context.getSource(), player);
                showHelp(context.getSource());
                return 1;
            })
            // /neow eco help
            .then(Commands.literal("help")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    showHelp(context.getSource());
                    return 1;
                })
            )
            // /neow eco balance
            .then(Commands.literal("balance")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_BALANCE, 0))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    showBalance(context.getSource(), player);
                    showHelp(context.getSource());
                    return 1;
                })
            )
            .then(buildAdminCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAdminCommand() {
        return Commands.literal("admin")
            .requires(EconomyCommand::canUseAdmin)
            .executes(context -> showAdminHelp(context.getSource()))
            .then(Commands.literal("help").executes(context -> showAdminHelp(context.getSource())))
            .then(Commands.literal("balance")
                .then(Commands.argument("target", EntityArgument.player())
                    .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_BALANCE_OTHERS, 2))
                    .executes(context -> {
                        ServerPlayer target = EntityArgument.getPlayer(context, "target");
                        showBalance(context.getSource(), target);
                        return 1;
                    })
                )
            )
            // /neow eco admin setcoin <value>
            .then(Commands.literal("setcoin")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_SETCOIN, 2))
                .then(Commands.argument("value", LongArgumentType.longArg(1))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        long value = LongArgumentType.getLong(context, "value");
                        ItemStack held = player.getMainHandItem();

                        if (held.getItem() instanceof CoinItem coinItem) {
                            if (CurrencyMath.total(value, held.getCount()) < 0) {
                                context.getSource().sendFailure(Component.literal("มูลค่ารวมของเหรียญเกินขอบเขตที่รองรับ"));
                                return 0;
                            }
                            long oldCoinValue = CoinItem.getCoinValue(held);
                            CoinItem.setCoinValue(held, value);
                            var analyticsDetail = new com.google.gson.JsonObject();
                            analyticsDetail.addProperty("quantity", held.getCount());
                            com.ambition.neoworld.economy.analytics.EconomyAnalytics.enqueue(
                                    com.ambition.neoworld.economy.analytics.EconomyAnalytics.event("COIN_VALUE_ADMIN", player,
                                            "admin_setcoin", context.getSource().getTextName(), "ADMIN", coinItem.getCoinType().name(),
                                            oldCoinValue, value, 0, analyticsDetail.toString()));
                            context.getSource().sendSuccess(() ->
                                Component.literal("§a[NeoWorld] §fกำหนดมูลค่าเหรียญในมือเป็น: " +
                                        coinItem.getCoinType().getFormat() + String.format("%,d", value) + " " + coinItem.getCoinType().getDisplayName()), false);
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("§c[NeoWorld] กรุณาถือไอเทมเหรียญ (Silver, Diamond) ในมือหลักก่อนใช้คำสั่งนี้"));
                            return 0;
                        }
                    })
                )
            )
            // /neow eco admin give <target> <type> <amount>
            .then(Commands.literal("give")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_GIVE, 2))
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(CURRENCY_TYPES, b))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(context -> {
                                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                String typeStr = StringArgumentType.getString(context, "type");
                                long amount = LongArgumentType.getLong(context, "amount");
                                return modifyBalance(context.getSource(), target, typeStr, amount, "give");
                            })
                        )
                    )
                )
            )
            // /neow eco admin take <target> <type> <amount>
            .then(Commands.literal("take")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_TAKE, 2))
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(CURRENCY_TYPES, b))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                            .executes(context -> {
                                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                String typeStr = StringArgumentType.getString(context, "type");
                                long amount = LongArgumentType.getLong(context, "amount");
                                return modifyBalance(context.getSource(), target, typeStr, amount, "take");
                            })
                        )
                    )
                )
            )
            // /neow eco admin set <target> <type> <amount>
            .then(Commands.literal("set")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_SET, 2))
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(CURRENCY_TYPES, b))
                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                            .executes(context -> {
                                ServerPlayer target = EntityArgument.getPlayer(context, "target");
                                String typeStr = StringArgumentType.getString(context, "type");
                                long amount = LongArgumentType.getLong(context, "amount");
                                return modifyBalance(context.getSource(), target, typeStr, amount, "set");
                            })
                        )
                    )
                )
            );
    }

    private static void showBalance(CommandSourceStack source, ServerPlayer player) {
        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        source.sendSuccess(() -> Component.literal("§6=== ยอดเงินของ §b" + player.getName().getString() + " §6==="), false);
        source.sendSuccess(() -> Component.literal("  §7- Silver: §f" + String.format("%,d", data.getSilver())), false);
        source.sendSuccess(() -> Component.literal("  §b- Diamond: §a" + String.format("%,d", data.getDiamond())), false);
    }

    private static boolean canUseAdmin(CommandSourceStack source) {
        return ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_BALANCE_OTHERS, 2)
                || ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_SETCOIN, 2)
                || ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_GIVE, 2)
                || ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_TAKE, 2)
                || ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_SET, 2);
    }

    private static void showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6=== คำสั่ง Economy ทั่วไป ==="), false);
        source.sendSuccess(() -> commandButton("/neow eco balance", "ดูยอดเงินของตัวเอง", true), false);
        if (canUseAdmin(source))
            source.sendSuccess(() -> commandButton("/neow eco admin help", "ดูคำสั่งแอดมิน", true), false);
    }

    private static int showAdminHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6=== คำสั่ง Economy แอดมิน ==="), false);
        if (ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_BALANCE_OTHERS, 2))
            source.sendSuccess(() -> commandButton("/neow eco admin balance <player>", "ดูยอดเงินผู้เล่น", false), false);
        if (ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_SETCOIN, 2))
            source.sendSuccess(() -> commandButton("/neow eco admin setcoin <value>", "ตั้งมูลค่าเหรียญในมือ", false), false);
        if (ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_GIVE, 2))
            source.sendSuccess(() -> commandButton("/neow eco admin give <player> <silver|diamond> <amount>", "เพิ่มเงินให้ผู้เล่น", false), false);
        if (ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_TAKE, 2))
            source.sendSuccess(() -> commandButton("/neow eco admin take <player> <silver|diamond> <amount>", "หักเงินผู้เล่น", false), false);
        if (ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_SET, 2))
            source.sendSuccess(() -> commandButton("/neow eco admin set <player> <silver|diamond> <amount>", "ตั้งยอดเงินผู้เล่น", false), false);
        return 1;
    }

    private static MutableComponent commandButton(String command, String description, boolean runOnClick) {
        ClickEvent.Action action = runOnClick ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND;
        String clickHint = runOnClick ? "คลิกเพื่อรันคำสั่ง" : "คลิกเพื่อเติมคำสั่งในช่องแชท";
        return Component.literal("  " + command)
            .withStyle(style -> style
                .withColor(ChatFormatting.YELLOW)
                .withClickEvent(new ClickEvent(action, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(clickHint + ": " + command))))
            .append(Component.literal(" §7- §f" + description));
    }

    private static CoinItem.CoinType parseCoinType(String str) {
        return switch (str.toLowerCase(Locale.ROOT)) {
            case "silver" -> CoinItem.CoinType.SILVER;
            case "diamond" -> CoinItem.CoinType.DIAMOND;
            default -> null;
        };
    }

    private static int modifyBalance(CommandSourceStack source, ServerPlayer target, String typeStr, long amount, String action) {
        try (var analyticsScope = com.ambition.neoworld.economy.analytics.EconomyAnalytics.observe(target, "admin_" + action, source.getTextName(), "ADMIN")) {
            CoinItem.CoinType coinType = parseCoinType(typeStr);
            if (coinType == null) {
                source.sendFailure(Component.literal("สกุลเงินต้องเป็น silver หรือ diamond"));
                return 0;
            }
            PlayerData data = target.getData(ModAttachments.PLAYER_DATA);


            switch (action) {
                case "give" -> {
                    if (!PlayerEconomyManager.deposit(data, coinType, amount)) {
                        source.sendFailure(Component.literal("เพิ่มเงินไม่ได้: ยอดรวมเกินขอบเขตที่รองรับ"));
                        return 0;
                    }
                    source.sendSuccess(() -> Component.literal("§a[NeoWorld] §fเพิ่ม " + coinType.getFormat() + String.format("%,d", amount) + " " + coinType.getDisplayName() +
                            " §fให้ §b" + target.getName().getString()), true);
                }
                case "take" -> {
                    boolean success = switch (coinType) {
                        case SILVER -> data.removeSilver(amount);
                        case DIAMOND -> data.removeDiamond(amount);
                    };
                    if (success) {
                        source.sendSuccess(() -> Component.literal("§a[NeoWorld] §fหัก " + coinType.getFormat() + String.format("%,d", amount) + " " + coinType.getDisplayName() +
                                " §fจาก §b" + target.getName().getString()), true);
                    } else {
                        source.sendFailure(Component.literal("§c[NeoWorld] ผู้เล่น §b" + target.getName().getString() + " §cมียอดเงินไม่เพียงพอสำหรับการหัก"));
                        return 0;
                    }
                }
                case "set" -> {
                    switch (coinType) {
                        case SILVER -> data.setSilver(amount);
                        case DIAMOND -> data.setDiamond(amount);
                    }
                    source.sendSuccess(() -> Component.literal("§a[NeoWorld] §fตั้งค่ายอดเงิน " + coinType.getFormat() + coinType.getDisplayName() +
                            " §fของ §b" + target.getName().getString() + " §fเป็น: " + String.format("%,d", amount)), true);
                }
            }

            PlayerEconomyManager.syncToClient(target);
            return 1;
        }
    }
}
