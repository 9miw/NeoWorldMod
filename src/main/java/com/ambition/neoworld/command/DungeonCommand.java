package com.ambition.neoworld.command;

import java.util.EnumSet;
import java.util.List;

import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.dungeon.DungeonInstance;
import com.ambition.neoworld.dungeon.DungeonRuntime;
import com.ambition.neoworld.dungeon.definition.DungeonDefinition;
import com.ambition.neoworld.dungeon.definition.DungeonDefinitionData;
import com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus;
import com.ambition.neoworld.dungeon.room.DungeonRoom;
import com.ambition.neoworld.dungeon.room.DungeonRoomManager;
import com.ambition.neoworld.dungeon.room.DungeonRoomMember;
import com.ambition.neoworld.dungeon.room.DungeonRoomType;
import com.ambition.neoworld.dungeon.structure.DungeonPlacementResult;
import com.ambition.neoworld.dungeon.structure.DungeonRemovalManager;
import com.ambition.neoworld.dungeon.structure.DungeonStructurePlacer;
import com.ambition.neoworld.dungeon.structure.DungeonTemplateLayouts;
import com.ambition.neoworld.permission.ModPermissions;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.registry.ModDungeonRegistries;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonCommand {
    private static final List<String> ROOM_TYPES = List.of("solo", "trio", "raid");

    private DungeonCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("dungeon")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_DUNGEON_USE, 0))
                .then(Commands.literal("list")
                        .executes(context -> listEnabledDefinitions(context.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        enabledDefinitionIds(context.getSource()), builder))
                                .executes(context -> showDefinition(
                                        context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(buildRoomCommand())
                .then(buildAdminCommand())
                .then(Commands.literal("instances")
                        .requires(DungeonCommand::isDungeonAdmin)
                        .executes(context -> listInstances(context.getSource())))
                .then(Commands.literal("place")
                        .requires(DungeonCommand::isDungeonAdmin)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        definitionIds(context.getSource()), builder))
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                        .executes(context -> placeDungeon(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                IntegerArgumentType.getInteger(context, "slot"))))))
                .then(Commands.literal("remove")
                        .requires(DungeonCommand::isDungeonAdmin)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        definitionIds(context.getSource()), builder))
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                        .executes(context -> removeDungeon(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                IntegerArgumentType.getInteger(context, "slot"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoomCommand() {
        return Commands.literal("room")
                .then(Commands.literal("create")
                        .then(Commands.argument("dungeon", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        enabledDefinitionIds(context.getSource()), builder))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                ROOM_TYPES, builder))
                                        .executes(context -> createRoom(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "dungeon"),
                                                StringArgumentType.getString(context, "type"))))))
                .then(Commands.literal("list").executes(context -> listRooms(context.getSource())))
                .then(Commands.literal("join")
                        .then(Commands.argument("roomId", IntegerArgumentType.integer(1))
                                .executes(context -> joinRoom(
                                        context.getSource(), IntegerArgumentType.getInteger(context, "roomId")))))
                .then(Commands.literal("leave").executes(context -> leaveRoom(context.getSource())))
                .then(Commands.literal("start").executes(context -> startRoom(context.getSource())))
                .then(Commands.literal("disband").executes(context -> disbandRoom(context.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAdminCommand() {
        return Commands.literal("admin")
                .requires(DungeonCommand::isDungeonAdmin)
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> createDefinition(
                                        context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("list").executes(context -> listAllDefinitions(context.getSource())))
                .then(Commands.literal("set")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        definitionIds(context.getSource()), builder))
                                .then(Commands.literal("name")
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(context -> setDefinitionName(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "value")))))
                                .then(Commands.literal("min_level")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 100))
                                                .executes(context -> setDefinitionMinimumLevel(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        IntegerArgumentType.getInteger(context, "value")))))
                                .then(Commands.literal("time_limit")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                                .executes(context -> setDefinitionTimeLimit(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        IntegerArgumentType.getInteger(context, "seconds")))))
                                .then(Commands.literal("modes")
                                        .then(Commands.argument("values", StringArgumentType.greedyString())
                                                .executes(context -> setDefinitionModes(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        StringArgumentType.getString(context, "values")))))))
                .then(Commands.literal("structure")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        definitionIds(context.getSource()), builder))
                                .then(Commands.argument("resource", StringArgumentType.word())
                                        .executes(context -> linkStructure(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "resource"))))))
                .then(Commands.literal("validate")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        definitionIds(context.getSource()), builder))
                                .executes(context -> validateDefinition(
                                        context.getSource(), StringArgumentType.getString(context, "id"), false))))
                .then(Commands.literal("enable")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        definitionIds(context.getSource()), builder))
                                .executes(context -> validateDefinition(
                                        context.getSource(), StringArgumentType.getString(context, "id"), true))))
                .then(Commands.literal("disable")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        definitionIds(context.getSource()), builder))
                                .executes(context -> disableDefinition(
                                        context.getSource(), StringArgumentType.getString(context, "id")))));
    }

    private static int createDefinition(CommandSourceStack source, String id) {
        DungeonDefinition definition = definitions(source).create(id).orElse(null);
        if (definition == null) {
            source.sendFailure(Component.literal(
                    "§c[NeoWorld] ID ต้องใช้ a-z, 0-9, _ และต้องไม่ซ้ำกับดันเจี้ยนเดิม"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "§a[NeoWorld] สร้างดันเจี้ยน " + id + " สถานะ DRAFT แล้ว"), true);
        return 1;
    }

    private static int setDefinitionName(CommandSourceStack source, String id, String value) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        if (value.isBlank()) {
            source.sendFailure(Component.literal("§c[NeoWorld] ชื่อดันเจี้ยนห้ามว่าง"));
            return 0;
        }
        definitions(source).updateName(definition, value.trim());
        return definitionUpdated(source, definition, "name=" + value.trim());
    }

    private static int setDefinitionMinimumLevel(CommandSourceStack source, String id, int value) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        definitions(source).updateMinimumLevel(definition, value);
        return definitionUpdated(source, definition, "min_level=" + value);
    }

    private static int setDefinitionTimeLimit(CommandSourceStack source, String id, int seconds) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        definitions(source).updateTimeLimit(definition, seconds);
        return definitionUpdated(source, definition, "time_limit=" + seconds + "s");
    }

    private static int setDefinitionModes(CommandSourceStack source, String id, String values) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        EnumSet<DungeonRoomType> modes = EnumSet.noneOf(DungeonRoomType.class);
        for (String value : values.split("[\\s,]+")) {
            DungeonRoomType type = DungeonRoomType.parse(value).orElse(null);
            if (type == null) {
                source.sendFailure(Component.literal("§c[NeoWorld] โหมดต้องเป็น solo, trio หรือ raid"));
                return 0;
            }
            modes.add(type);
        }
        if (modes.isEmpty()) {
            source.sendFailure(Component.literal("§c[NeoWorld] ต้องกำหนดอย่างน้อยหนึ่งโหมด"));
            return 0;
        }
        definitions(source).updateRoomTypes(definition, modes);
        return definitionUpdated(source, definition, "modes=" + roomTypeNames(definition));
    }

    private static int linkStructure(CommandSourceStack source, String id, String resource) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        ResourceLocation structureId = ResourceLocation.tryParse(resource);
        if (structureId == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] Structure resource ไม่ถูกต้อง: " + resource));
            return 0;
        }
        definitions(source).updateStructure(definition, structureId);
        return definitionUpdated(source, definition, "structure=" + structureId);
    }

    private static int validateDefinition(CommandSourceStack source, String id, boolean enable) {
        DungeonDefinition definition = requireLinkedDefinition(source, id);
        if (definition == null) return 0;
        ServerLevel dungeonLevel = dungeonLevel(source);
        if (dungeonLevel == null) return 0;

        DungeonPlacementResult validation = DungeonStructurePlacer.validate(
                dungeonLevel, DungeonTemplateLayouts.from(definition));
        if (!validation.successful()) {
            source.sendFailure(Component.literal("§c[NeoWorld] Validation ไม่ผ่าน: " + validation.error()));
            return 0;
        }
        int requiredPlayerSpawns = definition.roomTypes().stream()
                .mapToInt(DungeonRoomType::capacity)
                .max()
                .orElse(0);
        int playerSpawnCount = validation.markers().getOrDefault("player_spawn", List.of()).size();
        if (playerSpawnCount < requiredPlayerSpawns) {
            source.sendFailure(Component.literal(String.format(
                    "§c[NeoWorld] Validation ไม่ผ่าน: ต้องมี player_spawn อย่างน้อย %d จุด (พบ %d)",
                    requiredPlayerSpawns,
                    playerSpawnCount
            )));
            return 0;
        }
        DungeonDefinitionStatus status = enable
                ? DungeonDefinitionStatus.ENABLED
                : DungeonDefinitionStatus.READY;
        definitions(source).updateStatus(definition, status);
        source.sendSuccess(() -> Component.literal(String.format(
                "§a[NeoWorld] %s ผ่าน validation: %d marker types | %s",
                id, validation.markers().size(), status
        )), true);
        return 1;
    }

    private static int disableDefinition(CommandSourceStack source, String id) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        definitions(source).updateStatus(definition, DungeonDefinitionStatus.DISABLED);
        source.sendSuccess(() -> Component.literal("§e[NeoWorld] ปิดใช้งาน " + id + " แล้ว"), true);
        return 1;
    }

    private static int definitionUpdated(CommandSourceStack source, DungeonDefinition definition, String change) {
        source.sendSuccess(() -> Component.literal(String.format(
                "§a[NeoWorld] อัปเดต %s: %s | สถานะกลับเป็น DRAFT",
                definition.id(), change
        )), true);
        return 1;
    }

    private static int listEnabledDefinitions(CommandSourceStack source) {
        var enabled = definitions(source).all().stream()
                .filter(definition -> definition.status() == DungeonDefinitionStatus.ENABLED)
                .toList();
        source.sendSuccess(() -> Component.literal(
                "§6=== Enabled Dungeons: §f" + enabled.size() + " §6==="), false);
        enabled.forEach(definition -> sendDefinitionLine(source, definition));
        return enabled.size();
    }

    private static int listAllDefinitions(CommandSourceStack source) {
        var all = definitions(source).all();
        source.sendSuccess(() -> Component.literal(
                "§6=== All Dungeon Definitions: §f" + all.size() + " §6==="), false);
        all.forEach(definition -> sendDefinitionLine(source, definition));
        return all.size();
    }

    private static void sendDefinitionLine(CommandSourceStack source, DungeonDefinition definition) {
        source.sendSuccess(() -> Component.literal(String.format(
                "§e%s §7| §f%s §7| Lv.%d | %ds | %s | §f%s §7| %s",
                definition.id(), definition.displayName(), definition.minimumLevel(),
                definition.timeLimitSeconds(), roomTypeNames(definition),
                definition.structureId() == null ? "unlinked" : definition.structureId(),
                definition.status()
        )), false);
    }

    private static int showDefinition(CommandSourceStack source, String id) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        source.sendSuccess(() -> Component.literal("§6=== " + definition.displayName() + " ==="), false);
        sendDefinitionLine(source, definition);
        return 1;
    }

    private static int createRoom(CommandSourceStack source, String dungeonId, String typeName)
            throws CommandSyntaxException {
        DungeonRoomType type = DungeonRoomType.parse(typeName).orElse(null);
        if (type == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] ประเภทห้องต้องเป็น solo, trio หรือ raid"));
            return 0;
        }
        DungeonDefinition definition = definitions(source).get(dungeonId).orElse(null);
        if (definition == null || definition.status() != DungeonDefinitionStatus.ENABLED) {
            source.sendFailure(Component.literal("§c[NeoWorld] ดันเจี้ยนนี้ยังไม่เปิดใช้งาน"));
            return 0;
        }
        if (!definition.supports(type)) {
            source.sendFailure(Component.literal("§c[NeoWorld] ดันเจี้ยนนี้ไม่รองรับโหมด " + typeName));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        PlayerData playerData = player.getData(ModAttachments.PLAYER_DATA);
        if (playerData.getLevel() < definition.minimumLevel()) {
            source.sendFailure(Component.literal(String.format(
                    "§c[NeoWorld] ต้องมีเลเวลอย่างน้อย %d", definition.minimumLevel())));
            return 0;
        }
        return sendRoomResult(source, DungeonRoomManager.get(source.getServer()).create(player, dungeonId, type));
    }

    private static int listRooms(CommandSourceStack source) {
        var rooms = DungeonRoomManager.get(source.getServer()).rooms();
        source.sendSuccess(() -> Component.literal("§6=== Dungeon Rooms: §f" + rooms.size() + " §6==="), false);
        for (DungeonRoom room : rooms) {
            String ownerName = room.members().stream()
                    .filter(member -> member.playerId().equals(room.ownerId()))
                    .map(DungeonRoomMember::playerName)
                    .findFirst()
                    .orElse("unknown");
            source.sendSuccess(() -> Component.literal(String.format(
                    "§e#%d §7| §f%s §7| %s §7| §f%d/%d §7| owner=§f%s §7| %s",
                    room.id(), room.dungeonId(), room.type(), room.memberCount(),
                    room.type().capacity(), ownerName, room.state()
            )), false);
        }
        return rooms.size();
    }

    private static int joinRoom(CommandSourceStack source, int roomId) throws CommandSyntaxException {
        DungeonRoomManager roomManager = DungeonRoomManager.get(source.getServer());
        DungeonRoom room = roomManager.find(roomId).orElse(null);
        if (room == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] ไม่พบห้องหมายเลข " + roomId));
            return 0;
        }
        DungeonDefinition definition = definitions(source).get(room.dungeonId()).orElse(null);
        if (definition == null
                || definition.status() != DungeonDefinitionStatus.ENABLED
                || !definition.supports(room.type())) {
            source.sendFailure(Component.literal("§c[NeoWorld] ดันเจี้ยนหรือโหมดของห้องนี้ถูกปิดใช้งาน"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        PlayerData playerData = player.getData(ModAttachments.PLAYER_DATA);
        if (playerData.getLevel() < definition.minimumLevel()) {
            source.sendFailure(Component.literal(String.format(
                    "§c[NeoWorld] ต้องมีเลเวลอย่างน้อย %d", definition.minimumLevel())));
            return 0;
        }
        return sendRoomResult(source, roomManager.join(player, roomId));
    }

    private static int leaveRoom(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return sendRoomResult(source, DungeonRoomManager.get(source.getServer()).leave(player.getUUID()));
    }

    private static int startRoom(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return sendRoomResult(source, DungeonRoomManager.get(source.getServer()).start(player.getUUID()));
    }

    private static int disbandRoom(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return sendRoomResult(source, DungeonRoomManager.get(source.getServer()).disband(player.getUUID()));
    }

    private static int sendRoomResult(CommandSourceStack source, DungeonRoomManager.RoomActionResult result) {
        if (!result.successful()) {
            source.sendFailure(Component.literal("§c[NeoWorld] " + result.message()));
            return 0;
        }
        DungeonRoom room = result.room();
        source.sendSuccess(() -> Component.literal(String.format(
                "§a[NeoWorld] %s | ห้อง #%d | %s | %d/%d คน | %s",
                result.message(), room.id(), room.type(), room.memberCount(),
                room.type().capacity(), room.state()
        )), false);
        return 1;
    }

    private static int listInstances(CommandSourceStack source) {
        var instances = DungeonRuntime.get(source.getServer()).activeInstances();
        source.sendSuccess(() -> Component.literal(
                "§6=== Active Dungeon Instances: §f" + instances.size() + " §6==="), false);
        for (DungeonInstance instance : instances) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "§e%s §7| §f%s §7| state=§f%s §7| slot=§f%d §7| players=§f%d",
                    instance.id(), instance.dungeonId(), instance.state(),
                    instance.slotIndex(), instance.members().size()
            )), false);
        }
        return instances.size();
    }

    private static int placeDungeon(CommandSourceStack source, String dungeonId, int slotIndex) {
        DungeonDefinition definition = requireLinkedDefinition(source, dungeonId);
        if (definition == null) return 0;
        ServerLevel dungeonLevel = dungeonLevel(source);
        if (dungeonLevel == null) return 0;
        int originX;
        try {
            originX = DungeonRuntime.get(source.getServer()).slotOriginX(slotIndex);
        } catch (ArithmeticException exception) {
            source.sendFailure(Component.literal("§c[NeoWorld] หมายเลข slot สูงเกินไป"));
            return 0;
        }
        BlockPos origin = new BlockPos(originX, 64, 0);
        DungeonPlacementResult result = DungeonStructurePlacer.place(
                dungeonLevel, DungeonTemplateLayouts.from(definition), origin);
        if (!result.successful()) {
            source.sendFailure(Component.literal("§c[NeoWorld] วางดันเจี้ยนไม่สำเร็จ: " + result.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "§a[NeoWorld] วาง %s ที่ slot %d (%s) สำเร็จ: %d template, %d marker types",
                dungeonId, slotIndex, origin.toShortString(),
                result.placedTemplateCount(), result.markers().size()
        )), true);
        return 1;
    }

    private static int removeDungeon(CommandSourceStack source, String dungeonId, int slotIndex) {
        DungeonDefinition definition = requireLinkedDefinition(source, dungeonId);
        if (definition == null) return 0;
        ServerLevel dungeonLevel = dungeonLevel(source);
        if (dungeonLevel == null) return 0;
        int originX;
        try {
            originX = DungeonRuntime.get(source.getServer()).slotOriginX(slotIndex);
        } catch (ArithmeticException exception) {
            source.sendFailure(Component.literal("§c[NeoWorld] หมายเลข slot สูงเกินไป"));
            return 0;
        }
        DungeonRemovalManager.StartResult result = DungeonRemovalManager.get(source.getServer()).start(
                dungeonLevel,
                DungeonTemplateLayouts.from(definition),
                slotIndex,
                new BlockPos(originX, 64, 0),
                message -> source.sendSuccess(() -> message, true)
        );
        if (!result.started()) {
            source.sendFailure(Component.literal("§c[NeoWorld] เริ่มล้างดันเจี้ยนไม่สำเร็จ: " + result.error()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "§e[NeoWorld] เริ่มล้าง %s:%d จำนวน %,d บล็อก (ลบ entity แล้ว %d ตัว)",
                dungeonId, slotIndex, result.blockCount(), result.removedEntityCount()
        )), true);
        return 1;
    }

    private static DungeonDefinition requireDefinition(CommandSourceStack source, String id) {
        DungeonDefinition definition = definitions(source).get(id).orElse(null);
        if (definition == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] ไม่พบ Dungeon Definition: " + id));
        }
        return definition;
    }

    private static DungeonDefinition requireLinkedDefinition(CommandSourceStack source, String id) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition != null && definition.structureId() == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] ดันเจี้ยนยังไม่ได้เชื่อม Structure"));
            return null;
        }
        return definition;
    }

    private static DungeonDefinitionData definitions(CommandSourceStack source) {
        return DungeonDefinitionData.get(source.getServer());
    }

    private static ServerLevel dungeonLevel(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(ModDungeonRegistries.DUNGEON_LEVEL);
        if (level == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] ไม่พบมิติ neoworld:dungeon"));
        }
        return level;
    }

    private static List<String> definitionIds(CommandSourceStack source) {
        return definitions(source).all().stream().map(DungeonDefinition::id).sorted().toList();
    }

    private static List<String> enabledDefinitionIds(CommandSourceStack source) {
        return definitions(source).all().stream()
                .filter(definition -> definition.status() == DungeonDefinitionStatus.ENABLED)
                .map(DungeonDefinition::id)
                .sorted()
                .toList();
    }

    private static String roomTypeNames(DungeonDefinition definition) {
        return definition.roomTypes().stream()
                .map(DungeonRoomType::commandName)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static boolean isDungeonAdmin(CommandSourceStack source) {
        return ModPermissions.hasPermission(source, ModPermissions.COMMAND_DUNGEON_ADMIN, 2);
    }
}
