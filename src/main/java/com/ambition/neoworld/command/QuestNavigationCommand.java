package com.ambition.neoworld.command;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.navigation.NavigationAim;
import com.ambition.neoworld.navigation.NavigationEvents;
import com.ambition.neoworld.navigation.NavigationGraph;
import com.ambition.neoworld.navigation.NavigationSavedData;
import com.ambition.neoworld.navigation.NavigationWalk;
import com.ambition.neoworld.navigation.QuestNavigationService;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.UUID;

/** Quest navigation command tree registered centrally by NeoWorldCommand. */
public final class QuestNavigationCommand {
    private static final int ADMIN_PERMISSION_LEVEL = 2;
    private static final double NPC_SELECTION_RANGE = 16;

    private QuestNavigationCommand() {}

    /** Uses the same server-validated route as typed player commands. */
    public static String trackingCommand(int questId) {
        if (questId < 0) throw new IllegalArgumentException("Invalid quest ID");
        return "neow questnav track " + questId;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("questnav")
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("help").executes(context -> help(context.getSource())))
                .then(Commands.literal("track").then(questArgument().executes(QuestNavigationCommand::track)))
                .then(Commands.literal("stop").executes(QuestNavigationCommand::stop))
                .then(buildAdminCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAdminCommand() {
        return Commands.literal("admin")
                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                .executes(context -> help(context.getSource()))
                .then(Commands.literal("help").executes(context -> help(context.getSource())))
                .then(Commands.literal("view").executes(QuestNavigationCommand::view))
                .then(Commands.literal("radius")
                        .then(Commands.argument("blocks", DoubleArgumentType.doubleArg(1, 16))
                                .executes(QuestNavigationCommand::radius)))
                .then(Commands.literal("rebuild").executes(QuestNavigationCommand::rebuild))
                .then(Commands.literal("unbind").then(questArgument().executes(QuestNavigationCommand::unbind)))
                .then(buildLinkCommand("link", true))
                .then(buildLinkCommand("unlink", false))
                .then(buildBindingCommand("bind", false))
                .then(buildBindingCommand("bindnpc", true));
    }

    /** Retain legacy flat setup commands while reusing the canonical admin handlers. */
    private static RequiredArgumentBuilder<CommandSourceStack, Integer> questArgument() {
        return Commands.argument("quest", IntegerArgumentType.integer(0));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildLinkCommand(String name, boolean enabled) {
        return Commands.literal(name)
                .then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(context -> link(context, enabled))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildBindingCommand(String name, boolean npc) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> quest = questArgument();
        for (boolean turnIn : new boolean[]{false, true}) {
            LiteralArgumentBuilder<CommandSourceStack> stage = Commands.literal(turnIn ? "turnin" : "objective");
            if (npc) {
                stage.executes(context -> bindLookedNpc(context, turnIn))
                        .then(Commands.argument("npc", EntityArgument.entity())
                                .executes(context -> bindSelectedNpc(context, turnIn)));
            } else {
                stage.then(Commands.argument("radiusHere", DoubleArgumentType.doubleArg(1, 32))
                                .executes(context -> bindStandingPosition(context, turnIn)))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, 32))
                                        .executes(context -> bindPosition(context, turnIn))));
            }
            quest.then(stage);
        }
        return Commands.literal(name).then(quest);
    }

    private static int track(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return run(context, () -> QuestNavigationService.track(context.getSource().getPlayerOrException(),
                IntegerArgumentType.getInteger(context, "quest")), "เริ่มนำทางเควสต์แล้ว");
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return run(context, () -> QuestNavigationService.stop(context.getSource().getPlayerOrException()), "หยุดนำทางแล้ว");
    }

    private static int view(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return run(context, () -> QuestNavigationService.preview(context.getSource().getPlayerOrException()),
                "แสดงเส้นเชื่อมใกล้ตัวชั่วคราว");
    }

    private static int radius(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return run(context, () -> {
            NavigationSavedData.get(context.getSource().getServer()).radius(DoubleArgumentType.getDouble(context, "blocks"));
            for (var level : context.getSource().getServer().getAllLevels()) NavigationEvents.rebuild(level);
        }, "ปรับระยะเชื่อมแล้ว กำลังอัปเดตจุดที่โหลดอยู่ทีละชุด");
    }

    private static int rebuild(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return run(context, () -> NavigationEvents.rebuild(context.getSource().getLevel()),
                "กำลังตรวจและเชื่อมจุดใน chunk ที่โหลดแล้วทีละชุด");
    }

    private static int unbind(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return run(context, () -> NavigationSavedData.get(context.getSource().getServer())
                .unbind(IntegerArgumentType.getInteger(context, "quest")), "ลบจุดหมายเควสต์แล้ว");
    }

    private static int link(CommandContext<CommandSourceStack> context, boolean enabled) throws CommandSyntaxException {
        return run(context, () -> {
            var level = context.getSource().getLevel();
            NavigationSavedData data = NavigationSavedData.get(context.getSource().getServer());
            var from = BlockPosArgument.getLoadedBlockPos(context, "from");
            var to = BlockPosArgument.getLoadedBlockPos(context, "to");
            String fromId = NavigationSavedData.id(level, from), toId = NavigationSavedData.id(level, to);
            var a = data.graph().node(fromId);
            var b = data.graph().node(toId);
            if (a == null || b == null) throw new IllegalArgumentException("ต้องเลือกพิกัดบล็อกนำทางสองจุด");
            if (enabled && (a.distance(b) > 16 || !NavigationWalk.between(level, a, b)))
                throw new IllegalArgumentException("เชื่อมไม่ได้: ระยะเกิน 16 บล็อกหรือไม่มีทางเดินผ่าน");
            data.graph().override(fromId, toId, enabled);
            data.setDirty();
        }, enabled ? "เพิ่มเส้นเชื่อมแล้ว" : "ตัดเส้นเชื่อมแล้ว");
    }

    private static int bindLookedNpc(CommandContext<CommandSourceStack> context, boolean turnIn) throws CommandSyntaxException {
        return run(context, () -> {
            var player = context.getSource().getPlayerOrException();
            var eye = player.getEyePosition();
            var direction = player.getViewVector(1);
            var end = eye.add(direction.scale(NPC_SELECTION_RANGE));
            var block = player.serverLevel().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            double distance = block.getType() == HitResult.Type.MISS ? NPC_SELECTION_RANGE : eye.distanceTo(block.getLocation());
            var candidates = player.serverLevel().getEntities(player, new AABB(eye, end).inflate(1),
                            entity -> entity.isPickable() && !entity.isSpectator())
                    .stream().map(entity -> new NavigationAim.Candidate<>(entity, entity.getBoundingBox(),
                            entity instanceof EntityNPCInterface)).toList();
            var selected = NavigationAim.pick(eye, direction, distance, candidates);
            if (selected == null || !selected.npc())
                throw new IllegalArgumentException("กรุณาหันมอง NPC ของ Custom NPCs ภายใน 16 บล็อก โดยไม่มีสิ่งกีดขวาง");
            var entity = selected.entity();
            bind(context, turnIn, entity.level().dimension().location().toString(),
                    entity.getX(), entity.getY(), entity.getZ(), 3, entity.getUUID());
        }, "บันทึก NPC ที่มองอยู่เป็นจุดหมายเควสต์แล้ว");
    }

    private static int bindSelectedNpc(CommandContext<CommandSourceStack> context, boolean turnIn) throws CommandSyntaxException {
        return run(context, () -> {
            var entity = EntityArgument.getEntity(context, "npc");
            if (!(entity instanceof EntityNPCInterface)) throw new IllegalArgumentException("ต้องเลือก NPC ของ Custom NPCs");
            bind(context, turnIn, entity.level().dimension().location().toString(),
                    entity.getX(), entity.getY(), entity.getZ(), 3, entity.getUUID());
        }, "บันทึก NPC เป็นจุดหมายเควสต์แล้ว");
    }

    private static int bindStandingPosition(CommandContext<CommandSourceStack> context, boolean turnIn) throws CommandSyntaxException {
        return run(context, () -> {
            var player = context.getSource().getPlayerOrException();
            bind(context, turnIn, player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ(),
                    DoubleArgumentType.getDouble(context, "radiusHere"), null);
        }, "บันทึกตำแหน่งที่ยืนเป็นพื้นที่เป้าหมายเควสต์แล้ว");
    }

    private static int bindPosition(CommandContext<CommandSourceStack> context, boolean turnIn) throws CommandSyntaxException {
        return run(context, () -> {
            var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
            bind(context, turnIn, context.getSource().getLevel().dimension().location().toString(),
                    pos.getX() + .5, pos.getY(), pos.getZ() + .5, DoubleArgumentType.getDouble(context, "radius"), null);
        }, "บันทึกพื้นที่เป้าหมายเควสต์แล้ว");
    }

    private static void bind(CommandContext<CommandSourceStack> context, boolean turnIn, String dimension,
            double x, double y, double z, double radius, UUID npc) {
        NavigationSavedData data = NavigationSavedData.get(context.getSource().getServer());
        var target = new NavigationGraph.Node("@target", dimension, x, y, z);
        var level = context.getSource().getLevel();
        boolean reachable = data.graph().nearby(dimension, x, y, z, 16).stream()
                .anyMatch(node -> NavigationWalk.between(level, node, target));
        if (!reachable) throw new IllegalArgumentException("กรุณาวางบล็อกนำทางใกล้จุดหมายให้มีทางเดินเชื่อมถึงภายใน 16 บล็อก");
        data.bind(IntegerArgumentType.getInteger(context, "quest"), turnIn,
                new NavigationSavedData.Target(dimension, x, y, z, radius, npc));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("นำทางเควสต์: /neow questnav track <questId> | stop"), false);
        if (source.hasPermission(ADMIN_PERMISSION_LEVEL)) {
            source.sendSuccess(() -> Component.literal("ตั้งค่า: /neow questnav admin bindnpc <questId> objective (มอง NPC) | bind <questId> objective <รัศมี> (ยืนที่จุดหมาย)"), false);
            source.sendSuccess(() -> Component.literal("คำสั่งทั้งหมด: /neow help questnav"), false);
        }
        return 1;
    }

    @FunctionalInterface
    private interface Action { void run() throws CommandSyntaxException; }

    private static int run(CommandContext<CommandSourceStack> context, Action action, String message) throws CommandSyntaxException {
        try {
            action.run();
            context.getSource().sendSuccess(() -> Component.literal(message), false);
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
        } catch (CommandSyntaxException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            NeoWorld.LOGGER.error("Quest navigation command failed", exception);
            context.getSource().sendFailure(Component.literal("ดำเนินการนำทางไม่สำเร็จ กรุณาติดต่อผู้ดูแล"));
        }
        return 0;
    }
}
