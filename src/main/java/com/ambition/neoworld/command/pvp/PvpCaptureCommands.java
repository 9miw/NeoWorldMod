package com.ambition.neoworld.command.pvp;

import com.ambition.neoworld.pvp.arena.PvpArenaData;
import com.ambition.neoworld.pvp.arena.PvpCaptureService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class PvpCaptureCommands {
    private PvpCaptureCommands() { }
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("structure").requires(PvpCaptureService::allowed)
                .then(Commands.literal("tool").executes(ctx -> {
                    var player=ctx.getSource().getPlayerOrException(); var tool=PvpCaptureService.tool();
                    if(!player.getInventory().add(tool)) player.drop(tool,false);
                    player.sendSystemMessage(Component.literal("[PvP] ถือไม้: คลิกซ้ายมุม 1 / คลิกขวามุม 2 แล้วใช้ /neow pvp admin structure save <name>")); return 1;
                }))
                .then(position("pos1",true)).then(position("pos2",false))
                .then(Commands.literal("selection").executes(ctx -> {
                    var player=ctx.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("[PvP] "+PvpCaptureService.get(player.getServer()).selectionInfo(player))); return 1;
                }))
                .then(Commands.literal("cancel").executes(ctx -> {
                    var player=ctx.getSource().getPlayerOrException(); PvpCaptureService.get(player.getServer()).cancel(player.getUUID());
                    player.sendSystemMessage(Component.literal("[PvP] ล้างการเลือกและยกเลิกงานบันทึกแล้ว")); return 1;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    var data=PvpArenaData.get(ctx.getSource().getServer());
                    data.mapIds().stream().sorted().forEach(id -> ctx.getSource().sendSuccess(() -> Component.literal("[PvP] "+id+" | "+data.map(id).size()+" files | arenas: "+data.mapUsers(data.map(id))),false));
                    return data.mapIds().size();
                }))
                .then(Commands.literal("save").then(mapArgument().executes(ctx -> action(ctx,"save"))))
                .then(Commands.literal("update").then(mapArgument().executes(ctx -> action(ctx,"update"))))
                .then(Commands.literal("delete").then(mapArgument().executes(ctx -> action(ctx,"delete"))
                        .then(Commands.literal("confirm").then(Commands.argument("token",StringArgumentType.word()).executes(ctx -> action(ctx,"confirm"))))));
    }
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,net.minecraft.resources.ResourceLocation> mapArgument() {
        return Commands.argument("name",net.minecraft.commands.arguments.ResourceLocationArgument.id())
                .suggests((ctx,builder) -> SharedSuggestionProvider.suggest(PvpArenaData.get(ctx.getSource().getServer()).mapIds().stream().map(Object::toString),builder));
    }
    public static String raw(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,String argument) {
        return ctx.getNodes().stream().filter(node -> node.getNode().getName().equals(argument)).findFirst().orElseThrow().getRange().get(ctx.getInput());
    }
    private static int action(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,String action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player=ctx.getSource().getPlayerOrException(); var service=PvpCaptureService.get(player.getServer()); var name=raw(ctx,"name");
        String error=switch(action) {
            case "save" -> service.start(player,name);
            case "update" -> service.update(player,name);
            case "delete" -> service.requestDelete(player,name);
            default -> service.confirmDelete(player,name,StringArgumentType.getString(ctx,"token"));
        };
        if(error!=null) { ctx.getSource().sendFailure(Component.literal("[PvP] "+error)); return 0; } return 1;
    }
    private static LiteralArgumentBuilder<CommandSourceStack> position(String name,boolean first) {
        return Commands.literal(name).executes(ctx -> {
            var player=ctx.getSource().getPlayerOrException(); PvpCaptureService.get(player.getServer()).select(player,first,player.blockPosition()); return 1;
        });
    }
}
