package com.ambition.neoworld.command;

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

    private static final List<String> CURRENCY_TYPES = List.of("silver", "gold", "diamond");

    private EconomyCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildCommand(String rootName) {
        return Commands.literal(rootName)
            .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_BALANCE, 0))
            // พิมพ์ /neoworld eco หรือ /neoworld economy เปล่าๆ -> แสดงยอดเงินของตนเองทันที
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                showBalance(context.getSource(), player);
                showHelp(player);
                return 1;
            })
            // /neoworld eco help
            .then(Commands.literal("help")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    showHelp(player);
                    return 1;
                })
            )
            // /eco balance [player]
            .then(Commands.literal("balance")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_BALANCE, 0))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    showBalance(context.getSource(), player);
                    showHelp(player);
                    return 1;
                })
                .then(Commands.argument("target", EntityArgument.player())
                    .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_ECO_BALANCE_OTHERS, 2))
                    .executes(context -> {
                        ServerPlayer target = EntityArgument.getPlayer(context, "target");
                        showBalance(context.getSource(), target);
                        return 1;
                    })
                )
            )
            // /neoworld eco setcoin <value>
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
                            CoinItem.setCoinValue(held, value);
                            context.getSource().sendSuccess(() ->
                                Component.literal("§a[NeoWorld] §fกำหนดมูลค่าเหรียญในมือเป็น: " +
                                        coinItem.getCoinType().getFormat() + String.format("%,d", value) + " " + coinItem.getCoinType().getDisplayName()), false);
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("§c[NeoWorld] กรุณาถือไอเทมเหรียญ (Silver, Gold, Diamond) ในมือหลักก่อนใช้คำสั่งนี้"));
                            return 0;
                        }
                    })
                )
            )
            // /neoworld eco give <target> <type> <amount>
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
            // /neoworld eco take <target> <type> <amount>
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
            // /neoworld eco set <target> <type> <amount>
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
        source.sendSuccess(() -> Component.literal("  §6- Gold: §e" + String.format("%,d", data.getGold())), false);
        source.sendSuccess(() -> Component.literal("  §b- Diamond: §a" + String.format("%,d", data.getDiamond())), false);
    }

    private static void showHelp(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6=== คำสั่ง Economy ที่คลิกได้ ==="));
        player.sendSystemMessage(commandButton("/neoworld eco balance", "ดูยอดเงินของตัวเอง", true));
        player.sendSystemMessage(commandButton("/neoworld eco balance " + player.getGameProfile().getName(), "ดูยอดเงินผู้เล่น", false));
        player.sendSystemMessage(commandButton("/neoworld eco setcoin 100", "ตั้งมูลค่าเหรียญในมือ", false));
        player.sendSystemMessage(commandButton("/neoworld eco give " + player.getGameProfile().getName() + " silver 100", "เพิ่มเงินให้ผู้เล่น", false));
        player.sendSystemMessage(commandButton("/neoworld eco take " + player.getGameProfile().getName() + " silver 100", "หักเงินผู้เล่น", false));
        player.sendSystemMessage(commandButton("/neoworld eco set " + player.getGameProfile().getName() + " silver 0", "ตั้งยอดเงินผู้เล่น", false));
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
            case "gold" -> CoinItem.CoinType.GOLD;
            case "diamond" -> CoinItem.CoinType.DIAMOND;
            default -> null;
        };
    }

    private static int modifyBalance(CommandSourceStack source, ServerPlayer target, String typeStr, long amount, String action) {
        CoinItem.CoinType coinType = parseCoinType(typeStr);
        if (coinType == null) {
            source.sendFailure(Component.literal("สกุลเงินต้องเป็น silver, gold หรือ diamond"));
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
                    case GOLD -> data.removeGold(amount);
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
                    case GOLD -> data.setGold(amount);
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
