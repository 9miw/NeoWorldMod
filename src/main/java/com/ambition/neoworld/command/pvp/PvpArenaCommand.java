package com.ambition.neoworld.command.pvp;

import com.ambition.neoworld.pvp.PvpDimensions;
import com.ambition.neoworld.pvp.arena.PvpArenaData;
import com.ambition.neoworld.pvp.arena.PvpArenaPiece;
import com.ambition.neoworld.pvp.arena.PvpArenaTemplates;
import com.ambition.neoworld.pvp.arena.PvpCaptureService;
import com.ambition.neoworld.pvp.room.PvpRoomManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class PvpArenaCommand {
    private PvpArenaCommand() { }
    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("pvp")
                .executes(ctx -> {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(ctx.getSource().getPlayerOrException(),com.ambition.neoworld.network.pvp.OpenPvpPayload.INSTANCE);
                    return 1;
                })
                .then(PvpRankedCommand.queue()).then(PvpRankedCommand.unqueue())
                .then(PvpRankedCommand.rank()).then(PvpRankedCommand.top()).then(PvpRoomCommand.buildCommand())
                .then(Commands.literal("admin").requires(PvpCaptureService::allowed)
                        .then(Commands.literal("create").then(Commands.argument("id",StringArgumentType.word()).executes(ctx -> {
                            String id=StringArgumentType.getString(ctx,"id");
                            if(!PvpArenaData.get(ctx.getSource().getServer()).create(id)) return fail(ctx.getSource(),"Invalid or existing arena ID: "+id);
                            ctx.getSource().sendSuccess(() -> Component.literal("[PvP] สร้างสนาม "+id+" แล้ว ใช้ /neow pvp admin structure save <name> เพื่อบันทึกพื้นที่"),true);
                            return 1;
                        })))
                        .then(PvpCaptureCommands.build())
                        .then(Commands.literal("delete").then(Commands.argument("id",StringArgumentType.word())
                                .suggests((ctx,builder) -> SharedSuggestionProvider.suggest(PvpArenaData.get(ctx.getSource().getServer()).all().keySet(),builder))
                                .executes(ctx -> delete(ctx.getSource(),StringArgumentType.getString(ctx,"id")))))
                        .then(Commands.literal("set").then(Commands.argument("id",StringArgumentType.word())
                                .suggests((ctx,builder) -> SharedSuggestionProvider.suggest(PvpArenaData.get(ctx.getSource().getServer()).all().keySet(),builder))
                                .then(Commands.literal("structure").then(Commands.argument("resource",ResourceLocationArgument.id())
                                        .suggests((ctx,builder) -> SharedSuggestionProvider.suggest(PvpArenaData.get(ctx.getSource().getServer()).mapIds().stream().map(Object::toString),builder))
                                        .executes(ctx -> link(ctx.getSource(),StringArgumentType.getString(ctx,"id"),PvpCaptureService.mapId(PvpCaptureCommands.raw(ctx,"resource"))))))))
                        .then(Commands.literal("validate").then(Commands.argument("id",StringArgumentType.word())
                                .suggests((ctx,builder) -> SharedSuggestionProvider.suggest(PvpArenaData.get(ctx.getSource().getServer()).all().keySet(),builder))
                                .executes(ctx -> validate(ctx.getSource(),StringArgumentType.getString(ctx,"id")))))
                        .then(Commands.literal("list").executes(ctx -> {
                            var arenas=PvpArenaData.get(ctx.getSource().getServer()).all();
                            arenas.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry ->
                                    ctx.getSource().sendSuccess(() -> Component.literal("[PvP] "+entry.getKey()+" | "+entry.getValue().pieces().size()+" structures"
                                            +(entry.getValue().validated() ? " | READY" : " | DRAFT")),false));
                            if(arenas.isEmpty()) ctx.getSource().sendSuccess(() -> Component.literal("[PvP] ยังไม่มีสนาม"),false);
                            return arenas.size();
                        })));
    }
    private static int delete(CommandSourceStack source,String id) {
        var server=source.getServer(); var data=PvpArenaData.get(server);
        if(data.get(id)==null) return fail(source,"ไม่พบทะเบียนสนาม: "+id);
        if(PvpRoomManager.get(server).arenaInUse(id) || PvpCaptureService.isSaving(server,id))
            return fail(source,"สนามมีห้อง กำลังยืนยันคู่ หรือกำลังบันทึกแมพ กรุณารอให้เสร็จก่อนลบ");
        if(!data.removeArena(id)) return fail(source,"ไม่พบทะเบียนสนาม: "+id);
        source.sendSuccess(() -> Component.literal("[PvP] ลบทะเบียนสนาม "+id+" แล้ว เก็บแมพและไฟล์ Structure ไว้"),true);
        return 1;
    }
    private static int link(CommandSourceStack source,String id,net.minecraft.resources.ResourceLocation template) {
        var data=PvpArenaData.get(source.getServer()); var arena=data.get(id);
        if(arena==null) return fail(source,"Unknown arena: "+id);
        if(PvpCaptureService.isSaving(source.getServer(),id) || PvpRoomManager.get(source.getServer()).arenaInUse(id)) return fail(source,"สนามกำลังบันทึกหรือมีผู้เล่นอยู่");
        var pieces=data.map(template);
        if(pieces==null) pieces=List.of(new PvpArenaPiece(template,BlockPos.ZERO));
        if(!inspect(source,pieces)) return 0;
        data.update(id,arena,pieces);
        source.sendSuccess(() -> Component.literal("[PvP] Linked "+id+" -> "+template),true); return 1;
    }
    private static int validate(CommandSourceStack source,String id) {
        var pieces=PvpArenaData.get(source.getServer()).pieces(id);
        if(pieces.isEmpty()) return fail(source,"Unknown arena or no structures saved: "+id);
        if(PvpCaptureService.isSaving(source.getServer(),id)) return fail(source,"สนามกำลังบันทึก กรุณารอให้เสร็จ");
        if(!inspect(source,pieces)) return 0;
        PvpArenaData.get(source.getServer()).validated(id);
        source.sendSuccess(() -> Component.literal("[PvP] "+id+": "+pieces.size()+" structures, spawn markers and combined bounds valid"),false); return 1;
    }
    private static boolean inspect(CommandSourceStack source,List<PvpArenaPiece> pieces) {
        var level=source.getServer().getLevel(PvpDimensions.LEVEL);
        if(level==null) { fail(source,"PvP dimension is unavailable"); return false; }
        try { PvpArenaTemplates.inspect(level,pieces); return true; }
        catch(IllegalArgumentException exception) { fail(source,exception.getMessage()); return false; }
    }
    private static int fail(CommandSourceStack source,String message) { source.sendFailure(Component.literal(message)); return 0; }
}
