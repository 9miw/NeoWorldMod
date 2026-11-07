package com.ambition.neoworld.command;

import com.ambition.neoworld.world.structure.StructureCaptureService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import com.ambition.neoworld.world.structure.StructureCatalogData;
import com.mojang.brigadier.context.CommandContext;

public final class WorldStructureCommand {
    private WorldStructureCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("structure").requires(source -> source.hasPermission(2))
                .then(Commands.literal("scan").then(Commands.argument("name", ResourceLocationArgument.id()).executes(context -> scan(context))))
                .then(Commands.literal("place").then(Commands.argument("name", ResourceLocationArgument.id())
                        .suggests(WorldStructureCommand::suggestMaps)
                        .then(Commands.literal("confirm").executes(context -> place(context)))))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("update").then(Commands.argument("name", ResourceLocationArgument.id())
                        .suggests(WorldStructureCommand::suggestMaps)
                        .executes(context -> result(context, StructureCaptureService.get(context.getSource().getServer())
                                .update(context.getSource().getPlayerOrException(), name(context))))))
                .then(Commands.literal("delete").then(Commands.argument("name", ResourceLocationArgument.id())
                        .suggests(WorldStructureCommand::suggestMaps)
                        .executes(context -> result(context, StructureCaptureService.get(context.getSource().getServer())
                                .requestDelete(context.getSource().getPlayerOrException(), name(context))))
                        .then(Commands.literal("confirm").then(Commands.argument("token", StringArgumentType.word())
                                .executes(context -> result(context, StructureCaptureService.get(context.getSource().getServer())
                                        .confirmDelete(context.getSource().getPlayerOrException(), name(context),
                                                StringArgumentType.getString(context, "token"))))))))
                .then(Commands.literal("tool").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    var tool = StructureCaptureService.tool();
                    if (!player.getInventory().add(tool)) player.drop(tool, false);
                    player.sendSystemMessage(Component.literal("§b[Structure] ถือไม้: คลิกซ้ายมุม 1 / คลิกขวามุม 2 แล้วใช้ /neow world structure save <name>"));
                    return 1;
                }))
                .then(position("pos1", true)).then(position("pos2", false))
                .then(Commands.literal("selection").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("§b[Structure] " + StructureCaptureService.get(player.getServer()).selectionInfo(player)));
                    return 1;
                }))
                .then(Commands.literal("cancel").executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    StructureCaptureService.get(player.getServer()).cancel(player.getUUID());
                    player.sendSystemMessage(Component.literal("§e[Structure] ล้างการเลือกและยกเลิกงานบันทึกแล้ว"));
                    return 1;
                }))
                .then(Commands.literal("save").then(Commands.argument("name", ResourceLocationArgument.id()).executes(context -> {
                            var player = context.getSource().getPlayerOrException();
                            String error = StructureCaptureService.get(player.getServer()).start(player,
                                    name(context));
                            if (error != null) { context.getSource().sendFailure(Component.literal("[Structure] " + error)); return 0; }
                            return 1;
                        })));
    }

    private static String name(CommandContext<CommandSourceStack> context) {
        return context.getNodes().stream().filter(node -> node.getNode().getName().equals("name"))
                .findFirst().orElseThrow().getRange().get(context.getInput());
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMaps(
            CommandContext<CommandSourceStack> context, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(StructureCatalogData.get(context.getSource().getServer()).mapIds()
                .stream().map(Object::toString), builder);
    }

    private static int result(CommandContext<CommandSourceStack> context, String error) {
        if (error == null) return 1;
        context.getSource().sendFailure(Component.literal("[Structure] " + error));
        return 0;
    }

    private static Component action(String label, String command, boolean run) {
        return Component.literal(label).withStyle(style -> style.withColor(net.minecraft.ChatFormatting.AQUA)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(run
                        ? net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND
                        : net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, command)));
    }

    private static int list(CommandSourceStack source) {
        com.ambition.neoworld.integration.WorldStructureIntegration.ensure(source.getServer());
        var data = StructureCatalogData.get(source.getServer());
        source.sendSuccess(() -> Component.literal("§6แผนที่ Structure " + data.mapIds().size() + " ชุด"), false);
        data.mapIds().stream().sorted(java.util.Comparator.comparing(Object::toString)).forEach(id -> {
            var pieces = data.map(id).orElseThrow();
            source.sendSuccess(() -> Component.literal("§e" + id + " §7(" + pieces.size() + " ไฟล์) ")
                    .append(action("[วาง] ", "/neow world structure place " + id + " confirm", false))
                    .append(action("[อัปเดต] ", "/neow world structure update " + id, false))
                    .append(action("[ลบ]", "/neow world structure delete " + id, true)), false);
        });
        return 1;
    }
    private static LiteralArgumentBuilder<CommandSourceStack> position(String name, boolean first) {
        return Commands.literal(name).executes(context -> {
            var player = context.getSource().getPlayerOrException();
            StructureCaptureService.get(player.getServer()).select(player, first, player.blockPosition());
            return 1;
        });
    }
    private static int scan(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=context.getSource().getPlayerOrException();
        com.ambition.neoworld.integration.WorldStructureIntegration.ensure(player.getServer());
        var hit=player.pick(8D,1F,false);
        if(!(hit instanceof net.minecraft.world.phys.BlockHitResult blockHit))return result(context,"เล็ง Structure Block _01 ภายใน 8 บล็อก");
        try {
            var pieces=com.ambition.neoworld.world.structure.StructureBlockScanner.scan(player.serverLevel(),blockHit.getBlockPos(),256);
            if(StructureCaptureService.get(player.getServer()).isReserved(pieces))return result(context,"ไฟล์ยังอยู่ระหว่างบันทึก");
            StructureCatalogData.get(player.getServer()).addMap(StructureCaptureService.mapId(name(context)),pieces);
            context.getSource().sendSuccess(() -> Component.literal("บันทึกรายการ Structure จากการสแกนแล้ว: "+pieces.size()+" ไฟล์"),false);
            return 1;
        } catch(RuntimeException exception) { return result(context,exception.getMessage()); }
    }
    private static int place(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=context.getSource().getPlayerOrException();
        com.ambition.neoworld.integration.WorldStructureIntegration.ensure(player.getServer());
        var id=StructureCaptureService.mapId(name(context));
        var pieces=StructureCatalogData.get(player.getServer()).map(id).orElse(null);
        if(pieces==null)return result(context,"ไม่พบแผนที่ "+id);
        var placed=com.ambition.neoworld.world.structure.StructurePlacer.place(player.serverLevel(),
                new com.ambition.neoworld.world.structure.StructureLayout(id.toString(),pieces,java.util.Set.of()),player.blockPosition());
        if(!placed.successful())return result(context,placed.error());
        context.getSource().sendSuccess(() -> Component.literal("วาง Structure "+id+" แล้ว ("+placed.placedTemplateCount()+" ไฟล์)"),false);
        return 1;
    }

}
