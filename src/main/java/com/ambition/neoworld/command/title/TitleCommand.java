package com.ambition.neoworld.command.title;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.title.TitleRegistry;
import com.ambition.neoworld.title.TitlePlayerState;
import com.ambition.neoworld.title.service.TitleService;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.concurrent.CompletableFuture;

/** Players manage their own unlocked titles; administration requires OP level 2. */
public final class TitleCommand {
    private TitleCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("title")
            .then(Commands.literal("gui").requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(),
                            com.ambition.neoworld.network.title.OpenTitleGuiPayload.INSTANCE);
                    return 1;
                }))
            .then(Commands.literal("list").requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> list(context.getSource(), context.getSource().getPlayerOrException())))
            .then(Commands.literal("equip").requires(source -> source.getEntity() instanceof ServerPlayer)
                .then(Commands.argument("title", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        var player = context.getSource().getPlayerOrException();
                        var state = TitleService.isRunning() ? TitleService.get().cached(player.getUUID()) : null;
                        return SharedSuggestionProvider.suggest(TitleRegistry.all().stream()
                                .filter(title -> state != null && state.unlocked().containsKey(title.id()))
                                .map(title -> title.id()), builder);
                    })
                    .executes(context -> equip(context.getSource(), context.getSource().getPlayerOrException(),
                            StringArgumentType.getString(context, "title")))))
            .then(Commands.literal("clear").requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> equip(context.getSource(), context.getSource().getPlayerOrException(), "")))
            .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload").executes(context -> {
                    try {
                        TitleRegistry.load(TitleService.cataloguePath());
                        context.getSource().sendSuccess(() -> Component.literal("โหลดรายการฉายาแล้ว: "
                                + TitleRegistry.all().size() + " ฉายา"), true);
                        return 1;
                    } catch (java.io.IOException exception) {
                        context.getSource().sendFailure(Component.literal("โหลดฉายาไม่สำเร็จ ยังคงใช้รายการเดิม"));
                        NeoWorld.LOGGER.error("Could not reload title catalogue", exception);
                        return 0;
                    }
                }))
                .then(Commands.literal("unlockall")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> unlockAll(context.getSource(), EntityArgument.getPlayer(context, "player"))))));
    }
    private static TitleService prepare(CommandSourceStack source, ServerPlayer player) {
        if (!TitleService.isRunning()) {
            source.sendFailure(Component.literal("ระบบฉายายังไม่พร้อม"));
            return null;
        }
        var service = TitleService.get();
        service.load(player);
        return service;
    }
    private static int list(CommandSourceStack source, ServerPlayer player) {
        var service = prepare(source, player);
        if (service == null) return 0;
        service.inspect(player.getUUID()).whenComplete((state, error) -> {
            if (error != null) {
                source.sendFailure(Component.literal("โหลดรายการฉายาไม่สำเร็จ กรุณาลองใหม่"));
                return;
            }
            if (state.unlocked().isEmpty()) {
                source.sendSuccess(() -> Component.literal("คุณยังไม่มีฉายาที่ปลดล็อก"), false);
                return;
            }
            source.sendSuccess(() -> Component.literal("ฉายาที่ปลดล็อก: " + state.unlocked().size()
                    + " — ใส่ด้วย /neow title equip <ID>"), false);
            state.unlocked().keySet().stream().sorted().forEach(id -> {
                var name = TitleRegistry.find(id).map(title -> title.name()).orElse("ไม่พร้อมใช้งาน");
                source.sendSuccess(() -> Component.literal(name + " [" + id + "]"
                        + (state.equipped().equals(id) ? " • กำลังใส่" : "")), false);
            });
        });
        return 1;
    }
    private static int equip(CommandSourceStack source, ServerPlayer player, String titleId) {
        if (!titleId.isEmpty() && TitleRegistry.find(titleId).isEmpty()) {
            source.sendFailure(Component.literal("ไม่พบ ID ฉายานี้ ใช้ /neow title list เพื่อดูรายการ"));
            return 0;
        }
        var service = prepare(source, player);
        if (service == null) return 0;
        CompletableFuture<TitlePlayerState> action = service.inspect(player.getUUID()).thenCompose(state -> {
            if (!titleId.isEmpty() && !state.unlocked().containsKey(titleId))
                return CompletableFuture.failedFuture(new IllegalArgumentException("คุณยังไม่ปลดล็อกฉายานี้"));
            return service.equip(player.getUUID(), titleId);
        });
        action.whenComplete((state, error) -> {
            if (error != null) {
                var cause = error;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null)
                    cause = cause.getCause();
                source.sendFailure(Component.literal(cause instanceof IllegalArgumentException
                        ? "ไม่สามารถใส่ฉายานี้ได้: ฉายาไม่พร้อมใช้งานหรือยังไม่ปลดล็อก"
                        : "บันทึกฉายาไม่สำเร็จ กรุณาลองใหม่"));
                return;
            }
            var name = TitleRegistry.find(state.equipped()).map(title -> title.name()).orElse(titleId);
            source.sendSuccess(() -> Component.literal(titleId.isEmpty() ? "ถอดฉายาแล้ว" : "ใส่ฉายาแล้ว: " + name), false);
        });
        return 1;
    }
    private static int unlockAll(CommandSourceStack source, ServerPlayer target) {
        if (!TitleService.isRunning()) {
            source.sendFailure(Component.literal("ระบบฉายายังไม่พร้อม"));
            return 0;
        }
        var id = target.getUUID();
        var name = target.getGameProfile().getName();
        var service = TitleService.get();
        service.load(target); // Queue legacy import first if this player has just joined.
        service.grantAll(id).whenComplete((state, error) -> source.getServer().execute(() -> {
            if (error != null) {
                source.sendFailure(Component.literal("ปลดล็อกฉายาไม่สำเร็จให้ " + name));
                NeoWorld.LOGGER.error("Could not unlock all titles for {}", id, error);
                return;
            }
            source.sendSuccess(() -> Component.literal("ปลดล็อกฉายาทั้งหมดให้ " + name + " แล้ว"), true);
            NeoWorld.LOGGER.info("{} unlocked all titles for {} ({})", source.getTextName(), name, id);
        }));
        return 1;
    }
}
