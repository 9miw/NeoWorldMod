package com.ambition.neoworld.command;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.item.CoinItem;
import com.ambition.neoworld.shop.*;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class ShopCommand {
    private static final String NPC_KEY = "neoworld_shop";
    private ShopCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("shop")
                .executes(c -> run(c.getSource(), () -> { ShopMenu.open(c.getSource().getPlayerOrException(), 0); return 1; }))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("open")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(c -> openForTargets(c, null))
                                        .then(shopArgument().executes(c -> openForTargets(c, LongArgumentType.getLong(c,"shop"))))))
                        .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(c -> run(c.getSource(), () -> {
                                    var shop = ShopRepository.create(StringArgumentType.getString(c,"name"));
                                    c.getSource().sendSuccess(() -> Component.literal("สร้างร้าน " + shop.name() + " สำเร็จ รหัสอัตโนมัติ: " + ShopRules.code(shop.id())), false); return 1;
                                }))))
                        .then(Commands.literal("rename").then(shopArgument()
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> run(c.getSource(), () -> {
                                            long id = resolve(LongArgumentType.getLong(c,"shop"));
                                            String name = StringArgumentType.getString(c,"name");
                                            ShopRepository.rename(id, name);
                                            c.getSource().sendSuccess(() -> Component.literal("เปลี่ยนชื่อร้าน ID " + id + " เป็น " + name.strip() + " แล้ว — เปิดหน้าร้านใหม่เพื่อดูชื่อใหม่"), false);
                                            return 1;
                                        })))))
                        .then(Commands.literal("list").executes(c -> run(c.getSource(), () -> {
                            var shops = ShopRepository.shops();
                            if (shops.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("ยังไม่มีร้าน ใช้ /neow shop admin create <ชื่อร้าน>"), false);
                            for (var shop : shops) c.getSource().sendSuccess(() -> Component.literal("ID " + shop.id() + " — " + shop.name()), false);
                            return shops.size();
                        })))
                        .then(Commands.literal("add").then(shopArgument()
                                .then(Commands.argument("buy", LongArgumentType.longArg(0))
                                        .then(Commands.argument("sell", LongArgumentType.longArg(0))
                                                .executes(c -> add(c, "ทั่วไป"))
                                                .then(currencyOption(ShopCurrency.SILVER))
                                                .then(currencyOption(ShopCurrency.DIAMOND))
                                                .then(Commands.argument("category", StringArgumentType.greedyString())
                                                        .executes(c -> add(c, StringArgumentType.getString(c,"category"))))))))
                        .then(Commands.literal("remove").then(shopArgument()
                                .then(Commands.argument("offer", LongArgumentType.longArg(1))
                                        .executes(c -> run(c.getSource(), () -> {
                                            boolean removed = ShopRepository.remove(resolve(LongArgumentType.getLong(c,"shop")), LongArgumentType.getLong(c,"offer"));
                                            if (!removed) throw new IllegalArgumentException("ไม่พบสินค้าในร้านนี้");
                                            c.getSource().sendSuccess(() -> Component.literal("นำสินค้าออกจากร้านแล้ว"), false); return 1;
                                        })))))
                        .then(Commands.literal("bind").then(shopArgument()
                                .then(Commands.argument("npc", EntityArgument.entity()).executes(c -> run(c.getSource(), () -> {
                                    var entity = EntityArgument.getEntity(c,"npc");
                                    if (!(entity instanceof LivingEntity) || entity instanceof Player) throw new IllegalArgumentException("เลือก NPC หรือสิ่งมีชีวิตที่ไม่ใช่ผู้เล่น");
                                    long shop = resolve(LongArgumentType.getLong(c,"shop")); entity.getPersistentData().putLong(NPC_KEY, shop);
                                    c.getSource().sendSuccess(() -> Component.literal("ผูก NPC กับ " + ShopRules.code(shop) + " แล้ว"), false); return 1;
                                })))))
                        .then(Commands.literal("unbind").then(Commands.argument("npc", EntityArgument.entity()).executes(c -> run(c.getSource(), () -> {
                            EntityArgument.getEntity(c,"npc").getPersistentData().remove(NPC_KEY);
                            c.getSource().sendSuccess(() -> Component.literal("ยกเลิกการผูกร้านกับ NPC แล้ว"), false); return 1;
                        })))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Long> shopArgument() {
        return Commands.argument("shop", LongArgumentType.longArg(1)).suggests((c,b) -> {
            try {
                for (var shop : ShopRepository.shops()) {
                    b.suggest(ShopRules.code(shop.id()), Component.literal("ID " + shop.id() + " — " + shop.name()));
                }
            } catch (Exception e) { NeoWorld.LOGGER.warn("Shop suggestions unavailable", e); }
            return b.buildFuture();
        });
    }

    private static long resolve(long id) throws Exception {
        if (ShopRepository.shop(id) == null)
            throw new IllegalArgumentException("ไม่พบร้านค้า ID " + id + " ใช้ /neow shop admin list เพื่อดู ID และชื่อร้าน");
        return id;
    }

    private static int openForTargets(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, Long shop) {
        return run(c.getSource(), () -> {
            long shopId = shop == null ? 0 : resolve(shop);
            int opened = 0;
            for (ServerPlayer player : EntityArgument.getPlayers(c,"players")) {
                if (!player.isAlive() || player.isSpectator()) continue;
                var previous = player.containerMenu;
                ShopMenu.open(player, shopId);
                if (player.containerMenu != previous && player.containerMenu instanceof ShopMenu) opened++;
            }
            if (opened == 0) {
                c.getSource().sendFailure(Component.literal("ไม่มีผู้เล่นที่สามารถเปิดหน้าร้านได้"));
                return 0;
            }
            int count = opened;
            c.getSource().sendSuccess(() -> Component.literal("เปิดหน้าร้านให้ผู้เล่น " + count + " คนแล้ว"), false);
            return count;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> currencyOption(ShopCurrency currency) {
        return Commands.literal(currency.commandName())
                .executes(c -> add(c,currency,"ทั่วไป"))
                .then(Commands.argument("category",StringArgumentType.greedyString())
                        .executes(c -> add(c,currency,StringArgumentType.getString(c,"category"))));
    }

    private static int add(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String category) {
        return add(c,ShopCurrency.SILVER,category);
    }

    private static int add(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, ShopCurrency currency, String category) {
        return run(c.getSource(), () -> {
            ServerPlayer player = c.getSource().getPlayerOrException(); var held = player.getMainHandItem();
            if (held.isEmpty() || held.getItem() instanceof CoinItem) throw new IllegalArgumentException("กรุณาถือสินค้าที่ไม่ใช่เหรียญเงิน");
            long offer = ShopRepository.add(resolve(LongArgumentType.getLong(c,"shop")), held,
                    LongArgumentType.getLong(c,"buy"), LongArgumentType.getLong(c,"sell"), category, currency, player.registryAccess());
            c.getSource().sendSuccess(() -> Component.literal("เพิ่มสินค้า #" + offer + " แล้ว (ราคาต่อ 1 ชิ้น ใช้ " + currency.label() + ")"), false); return 1;
        });
    }

    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract event) {
        long id = event.getTarget().getPersistentData().getLong(NPC_KEY);
        if (id <= 0 || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getEntity() instanceof ServerPlayer player) run(player.createCommandSourceStack(), () -> {
            if (ShopRepository.shop(id) == null) throw new IllegalArgumentException("ไม่พบร้านค้าที่ผูกกับ NPC");
            ShopMenu.open(player, id); return 1;
        });
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ShopService.reviewLegacyOnLogin(player);
        }
    }

    private interface Action { int run() throws Exception; }
    private static int run(CommandSourceStack source, Action action) {
        try { return action.run(); }
        catch (IllegalArgumentException e) { source.sendFailure(Component.literal(e.getMessage())); }
        catch (Exception e) { NeoWorld.LOGGER.error("Shop command failed", e); source.sendFailure(Component.literal("ดำเนินการร้านค้าไม่สำเร็จ กรุณาติดต่อผู้ดูแล")); }
        return 0;
    }
}
