package com.ambition.neoworld.command.dungeon;

import com.ambition.neoworld.dungeon.structure.DungeonCaptureService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import com.ambition.neoworld.dungeon.definition.DungeonDefinitionData;
import com.mojang.brigadier.context.CommandContext;

public final class DungeonCaptureCommands {
    private DungeonCaptureCommands() {}
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("structure").requires(DungeonCaptureService::allowed)
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("update").then(Commands.argument("name", ResourceLocationArgument.id())
                        .suggests(DungeonCaptureCommands::suggestMaps)
                        .executes(context -> result(context, DungeonCaptureService.get(context.getSource().getServer())
                                .update(context.getSource().getPlayerOrException(), name(context))))))
                .then(Commands.literal("delete").then(Commands.argument("name", ResourceLocationArgument.id())
                        .suggests(DungeonCaptureCommands::suggestMaps)
                        .executes(context -> result(context, DungeonCaptureService.get(context.getSource().getServer())
                                .requestDelete(context.getSource().getPlayerOrException(), name(context))))
                        .then(Commands.literal("confirm").then(Commands.argument("token", StringArgumentType.word())
                                .executes(context -> result(context, DungeonCaptureService.get(context.getSource().getServer())
                                        .confirmDelete(context.getSource().getPlayerOrException(), name(context),
                                                StringArgumentType.getString(context, "token"))))))))
                .then(Commands.literal("tool").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var tool = DungeonCaptureService.tool();
                    if (!player.getInventory().add(tool)) player.drop(tool, false);
                    player.sendSystemMessage(Component.literal("§b[Dungeon] ถือไม้: คลิกซ้ายมุม 1 / คลิกขวามุม 2 แล้วใช้ /neow dungeon admin structure save <name>"));
                    return 1;
                }))
                .then(position("pos1", true)).then(position("pos2", false))
                .then(Commands.literal("selection").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("§b[Dungeon] " + DungeonCaptureService.get(player.getServer()).selectionInfo(player)));
                    return 1;
                }))
                .then(Commands.literal("cancel").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    DungeonCaptureService.get(player.getServer()).cancel(player.getUUID());
                    player.sendSystemMessage(Component.literal("§e[Dungeon] ล้างการเลือกและยกเลิกงานบันทึกแล้ว"));
                    return 1;
                }))
                .then(Commands.literal("save").then(Commands.argument("name", ResourceLocationArgument.id()).executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            String error = DungeonCaptureService.get(player.getServer()).start(player,
                                    name(context));
                            if (error != null) { context.getSource().sendFailure(Component.literal("[Dungeon] " + error)); return 0; }
                            return 1;
                        })));
    }

    private static String name(CommandContext<CommandSourceStack> context) {
        return context.getNodes().stream().filter(node -> node.getNode().getName().equals("name"))
                .findFirst().orElseThrow().getRange().get(context.getInput());
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMaps(
            CommandContext<CommandSourceStack> context, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(DungeonDefinitionData.get(context.getSource().getServer()).mapIds()
                .stream().map(Object::toString), builder);
    }

    private static int result(CommandContext<CommandSourceStack> context, String error) {
        if (error == null) return 1;
        context.getSource().sendFailure(Component.literal("[Dungeon] " + error));
        return 0;
    }

    private static Component action(String label, String command, boolean run) {
        return Component.literal(label).withStyle(style -> style.withColor(net.minecraft.ChatFormatting.AQUA)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(run
                        ? net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND
                        : net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, command)));
    }

    private static int list(CommandSourceStack source) {
        var data = DungeonDefinitionData.get(source.getServer());
        source.sendSuccess(() -> Component.literal("§6แผนที่ดันเจี้ยน " + data.mapIds().size() + " ชุด"), false);
        data.mapIds().stream().sorted(java.util.Comparator.comparing(Object::toString)).forEach(id -> {
            var pieces = data.map(id).orElseThrow();
            String users = data.mapUsers(pieces).stream().map(definition -> definition.id())
                    .collect(java.util.stream.Collectors.joining(", "));
            source.sendSuccess(() -> Component.literal("§e" + id + " §7(" + pieces.size() + " ไฟล์) | ดัน: "
                    + (users.isEmpty() ? "ไม่มี" : users) + " ")
                    .append(action("[เชื่อม] ", "/neow dungeon admin set <id> structure " + id, false))
                    .append(action("[อัปเดต] ", "/neow dungeon admin structure update " + id, false))
                    .append(action("[ลบ]", "/neow dungeon admin structure delete " + id, true)), false);
        });
        return 1;
    }
    private static LiteralArgumentBuilder<CommandSourceStack> position(String name, boolean first) {
        return Commands.literal(name).executes(context -> {
            var player = context.getSource().getPlayerOrException();
            DungeonCaptureService.get(player.getServer()).select(player, first, player.blockPosition());
            return 1;
        });
    }
}
