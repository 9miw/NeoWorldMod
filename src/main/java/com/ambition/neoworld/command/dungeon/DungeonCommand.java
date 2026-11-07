package com.ambition.neoworld.command.dungeon;

import java.util.EnumSet;
import java.util.List;

import com.ambition.neoworld.data.PlayerData;
import com.ambition.neoworld.dungeon.instance.DungeonInstance;
import com.ambition.neoworld.dungeon.instance.DungeonRuntime;
import com.ambition.neoworld.dungeon.definition.DungeonDefinition;
import com.ambition.neoworld.dungeon.definition.DungeonDefinitionData;
import com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus;
import com.ambition.neoworld.dungeon.definition.DungeonBossConfig;
import com.ambition.neoworld.dungeon.definition.DungeonMonsterWave;
import com.ambition.neoworld.dungeon.definition.DungeonRewardConfig;
import com.ambition.neoworld.dungeon.room.DungeonRoom;
import com.ambition.neoworld.dungeon.room.DungeonRoomManager;
import com.ambition.neoworld.dungeon.room.DungeonRoomMember;
import com.ambition.neoworld.dungeon.room.DungeonRoomType;
import com.ambition.neoworld.dungeon.structure.DungeonPlacementResult;
import com.ambition.neoworld.dungeon.structure.DungeonRemovalManager;
import com.ambition.neoworld.dungeon.structure.DungeonStructurePlacer;
import com.ambition.neoworld.dungeon.structure.DungeonTemplatePiece;
import com.ambition.neoworld.dungeon.structure.DungeonTemplateLayouts;
import com.ambition.neoworld.party.Party;
import com.ambition.neoworld.party.PartyManager;
import com.ambition.neoworld.party.PartyMember;
import com.ambition.neoworld.permission.ModPermissions;
import com.ambition.neoworld.network.dungeon.DungeonBrowserDataPayload;
import com.ambition.neoworld.network.dungeon.OpenDungeonBrowserPayload;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.registry.ModDimensionRegistries;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.controllers.ServerCloneController;

public final class DungeonCommand {
    private static final List<String> ROOM_TYPES = List.of("solo", "trio", "raid");
    private static final int STRUCTURE_SCAN_RADIUS = 256;

    private DungeonCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("dungeon")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_DUNGEON_USE, 0))
                .executes(context -> openDungeonBrowser(context.getSource().getPlayerOrException()))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource(), 1)))
                .then(Commands.literal("list")
                        .executes(context -> listEnabledDefinitions(context.getSource())))
                .then(Commands.literal("info")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        enabledDefinitionIds(context.getSource()), builder))
                                .executes(context -> showDefinition(
                                        context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("exit")
                        .executes(context -> exitDungeon(context.getSource())))
                .then(buildRoomCommand())
                .then(buildAdminCommand());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAdminCommand() {
        return Commands.literal("admin")
                .requires(DungeonCommand::isDungeonAdmin)
                .executes(context -> showHelp(context.getSource(), 2))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource(), 2))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1, 3))
                                .executes(context -> showHelp(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "page") + 1))))
                .then(DungeonCaptureCommands.build())
                .then(DungeonLootCommands.build())
                .then(buildCreateDefinitionCommand())
                .then(buildDefinitionsCommand())
                .then(buildSetCommand())
                .then(buildStatusCommand("validate", false))
                .then(buildStatusCommand("enable", true))
                .then(Commands.literal("disable")
                        .requires(DungeonCommand::isDungeonAdmin)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        definitionIds(context.getSource()), builder))
                                .executes(context -> disableDefinition(
                                        context.getSource(), StringArgumentType.getString(context, "id")))))
                .then(Commands.literal("instances")
                        .requires(DungeonCommand::isDungeonAdmin)
                        .executes(context -> listInstances(context.getSource())))
                .then(Commands.literal("build")
                        .requires(DungeonCommand::isDungeonAdmin)
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        definitionIds(context.getSource()), builder))
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                        .executes(context -> placeDungeon(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                IntegerArgumentType.getInteger(context, "slot"))))))
                .then(Commands.literal("clear")
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

    private static int openDungeonBrowser(ServerPlayer player) {
        if (DungeonRoomManager.get(player.getServer()).findForPlayer(player.getUUID()).isPresent()) {
            com.ambition.neoworld.network.dungeon.DungeonRoomNetworkHandler.send(player, true);
            return 1;
        }
        PacketDistributor.sendToPlayer(player, OpenDungeonBrowserPayload.INSTANCE);
        PacketDistributor.sendToPlayer(player, DungeonBrowserDataPayload.fromDefinitions(
                player.getServer(), DungeonDefinitionData.get(player.getServer()).all()));
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoomCommand() {
        return Commands.literal("room")
                .executes(context -> {
                    com.ambition.neoworld.network.dungeon.DungeonRoomNetworkHandler.send(context.getSource().getPlayerOrException(), true);
                    return 1;
                })
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
                .then(Commands.literal("ready").executes(context -> sendRoomResult(context.getSource(),
                        DungeonRoomManager.get(context.getSource().getServer())
                                .toggleReady(context.getSource().getPlayerOrException().getUUID()))))
                .then(Commands.literal("start").executes(context -> startRoom(context.getSource())))
                .then(Commands.literal("disband").executes(context -> disbandRoom(context.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCreateDefinitionCommand() {
        return Commands.literal("create")
                .requires(DungeonCommand::isDungeonAdmin)
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> createDefinition(
                                context.getSource(), StringArgumentType.getString(context, "id"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDefinitionsCommand() {
        return Commands.literal("definitions")
                .requires(DungeonCommand::isDungeonAdmin)
                .executes(context -> listAllDefinitions(context.getSource()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetCommand() {
        return Commands.literal("set")
                .requires(DungeonCommand::isDungeonAdmin)
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                definitionIds(context.getSource()), builder))
                        .then(Commands.literal("name")
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(context -> setDefinitionName(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "value")))))
                        .then(Commands.literal("image")
                                .then(Commands.literal("clear").executes(context -> setDefinitionImage(context.getSource(),
                                        StringArgumentType.getString(context, "id"), null)))
                                .then(Commands.argument("resource", ResourceLocationArgument.id())
                                        .executes(context -> setDefinitionImage(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                ResourceLocationArgument.getId(context, "resource")))))
                        .then(Commands.literal("level")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> setDefinitionMinimumLevel(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                IntegerArgumentType.getInteger(context, "value")))))
                        .then(Commands.literal("time")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                                        .executes(context -> setDefinitionTimeLimit(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                IntegerArgumentType.getInteger(context, "seconds")))))
                        .then(Commands.literal("modes")
                                .then(Commands.argument("values", StringArgumentType.greedyString())
                                        .executes(context -> setDefinitionModes(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "values")))))
                        .then(Commands.literal("structure")
                                .then(Commands.literal("scan")
                                        .executes(context -> scanStructureBlocks(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "id"))))
                                .then(Commands.argument("resource", StringArgumentType.greedyString())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.stream.Stream.concat(
                                                        definitions(context.getSource()).mapIds().stream().map(map ->
                                                                map.getNamespace().equals("neoworld") && map.getPath().startsWith("dungeon/")
                                                                        ? map.getPath().substring(8) : map.toString()),
                                                        structureIds(context.getSource()).stream()), builder))
                                        .executes(context -> linkStructure(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                StringArgumentType.getString(context, "resource")))))
                        .then(Commands.literal("boss")
                                .then(Commands.argument("clone_tab", IntegerArgumentType.integer(1, 9))
                                        .then(Commands.argument("clone_name", StringArgumentType.greedyString())
                                                .executes(context -> setDefinitionBoss(context.getSource(),
                                                        StringArgumentType.getString(context, "id"),
                                                        IntegerArgumentType.getInteger(context, "clone_tab"),
                                                        StringArgumentType.getString(context, "clone_name"))))))
                        .then(Commands.literal("reward")
                                .then(Commands.argument("loot_table", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                definitions(context.getSource()).lootPresetIds().stream().flatMap(id ->
                                                        id.getNamespace().equals("neoworld") && id.getPath().startsWith("dungeon/rewards/")
                                                                ? java.util.stream.Stream.of(id.getPath().substring(16), id.toString())
                                                                : java.util.stream.Stream.of(id.toString())), builder))
                                        .executes(context -> setDefinitionReward(context.getSource(),
                                                                StringArgumentType.getString(context, "id"),
                                                                context.getNodes().stream().filter(node -> node.getNode().getName().equals("loot_table"))
                                                                        .findFirst().orElseThrow().getRange().get(context.getInput())))))
                        .then(Commands.literal("wave")
                        .then(buildWaveSpawnEditCommand())
                        .then(Commands.literal("add")
                                .then(Commands.argument("clone_tab", IntegerArgumentType.integer(1, 9))
                                        .then(Commands.argument("clone_name", StringArgumentType.greedyString())
                                                        .executes(context -> addDefinitionMonsterWave(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "id"),
                                                                IntegerArgumentType.getInteger(context, "clone_tab"),
                                                                StringArgumentType.getString(context, "clone_name"))))))
                        .then(Commands.literal("clear")
                                .executes(context -> clearDefinitionMonsterWaves(
                                        context.getSource(), StringArgumentType.getString(context, "id"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildStatusCommand(String name, boolean enable) {
        return Commands.literal(name)
                .requires(DungeonCommand::isDungeonAdmin)
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                definitionIds(context.getSource()), builder))
                        .executes(context -> validateDefinition(
                                context.getSource(), StringArgumentType.getString(context, "id"), enable)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildWaveSpawnEditCommand() {
        var spawn = Commands.literal("spawn");
        for (DungeonMonsterWave.SpawnMode mode : DungeonMonsterWave.SpawnMode.values()) {
            spawn.then(Commands.literal(mode.id())
                    .then(Commands.argument("spawn_count", IntegerArgumentType.integer(1, 1024))
                            .executes(context -> setWaveSpawn(context.getSource(),
                                    StringArgumentType.getString(context, "id"),
                                    IntegerArgumentType.getInteger(context, "wave_index"), mode,
                                    IntegerArgumentType.getInteger(context, "spawn_count")))));
        }
        return Commands.literal("edit")
                .then(Commands.argument("wave_index", IntegerArgumentType.integer(1)).then(spawn));
    }

    private static int setWaveSpawn(CommandSourceStack source, String id, int waveNumber,
            DungeonMonsterWave.SpawnMode mode, int count) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        if (waveNumber > definition.monsterWaves().size()) {
            source.sendFailure(Component.literal("ไม่พบ Wave " + waveNumber));
            return 0;
        }
        DungeonMonsterWave old = definition.monsterWaves().get(waveNumber - 1);
        definitions(source).updateMonsterWave(definition, waveNumber - 1,
                new DungeonMonsterWave(old.cloneTab(), old.cloneName(), count, mode));
        return definitionUpdated(source, definition,
                "wave=" + waveNumber + " spawn=" + mode.id() + " count=" + count);
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

    private static int setDefinitionImage(CommandSourceStack source, String id, ResourceLocation image) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        if (image != null && !DungeonDefinition.isImageResource(image)) {
            source.sendFailure(Component.literal("§c[NeoWorld] รูปต้องเป็น resource textures/...png ความยาวไม่เกิน 256 ตัวอักษร"));
            return 0;
        }
        definitions(source).updateImage(definition, image);
        source.sendSuccess(() -> Component.literal("§a[NeoWorld] รูปดัน " + id + ": "
                + (image == null ? "ใช้รูปเริ่มต้น" : image.toString())), true);
        return 1;
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

    private static int linkStructure(CommandSourceStack source, String id, String name) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        if (com.ambition.neoworld.dungeon.instance.DungeonRuntime.get(source.getServer()).activeInstances()
                .stream().anyMatch(instance -> instance.dungeonId().equals(id))) {
            source.sendFailure(Component.literal("ดันนี้กำลังมีผู้เล่น กรุณารอให้จบรอบก่อนเปลี่ยนแผนที่"));
            return 0;
        }
        ResourceLocation structureId = ResourceLocation.tryParse(name.contains(":") ? name.trim() : "neoworld:dungeon/" + name.trim());
        if (structureId == null) {
            source.sendFailure(Component.literal("ชื่อแผนที่ไม่ถูกต้อง"));
            return 0;
        }
        var pieces = definitions(source).map(structureId).orElse(null);
        if (pieces != null) {
            ServerLevel level = dungeonLevel(source);
            if (level == null) return 0;
            for (DungeonTemplatePiece piece : pieces) {
                if (level.getStructureManager().get(piece.templateId()).isEmpty()) {
                    source.sendFailure(Component.literal("ไม่พบไฟล์ของแผนที่: " + piece.templateId()));
                    return 0;
                }
            }
            definitions(source).updateStructurePieces(definition, pieces);
        } else {
            ServerLevel level = dungeonLevel(source);
            if (level == null) return 0;
            if (level.getStructureManager().get(structureId).isEmpty()) {
                source.sendFailure(Component.literal("ไม่พบแผนที่ " + name + " กรุณาบันทึกด้วย admin structure save ก่อน"));
                return 0;
            }
            definitions(source).updateStructure(definition, structureId);
        }
        return definitionUpdated(source, definition, "structure=" + structureId);
    }

    private static int scanStructureBlocks(CommandSourceStack source, String id) throws CommandSyntaxException {
        DungeonDefinition definition=requireDefinition(source,id);
        if(definition==null)return 0;
        var player=source.getPlayerOrException();var hit=player.pick(8D,1F,false);
        if(!(hit instanceof BlockHitResult blockHit)) { source.sendFailure(Component.literal("เล็ง Structure Block _01 ภายใน 8 บล็อก"));return 0; }
        try {
            var pieces=com.ambition.neoworld.world.structure.StructureBlockScanner.scan(player.serverLevel(),blockHit.getBlockPos(),STRUCTURE_SCAN_RADIUS);
            if(com.ambition.neoworld.world.structure.StructureCaptureService.get(player.getServer()).isReserved(pieces))
                throw new IllegalArgumentException("ไฟล์ยังอยู่ระหว่างบันทึก");
            definitions(source).updateStructurePieces(definition,DungeonDefinitionData.dungeonPieces(pieces));
            return definitionUpdated(source,definition,"structure_layout="+pieces.size()+" pieces, radius="+STRUCTURE_SCAN_RADIUS);
        } catch(RuntimeException exception) { source.sendFailure(Component.literal(exception.getMessage()));return 0; }
    }

    private static int setDefinitionBoss(
            CommandSourceStack source,
            String id,
            int cloneTab,
            String cloneName
    ) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        cloneName = cloneName.trim();
        if (!cloneExists(cloneTab, cloneName)) {
            source.sendFailure(Component.literal(String.format(
                    "§c[NeoWorld] ไม่พบ CustomNPC clone '%s' ใน tab %d", cloneName, cloneTab)));
            return 0;
        }
        definitions(source).updateBoss(definition,
                new DungeonBossConfig(cloneTab, cloneName));
        return definitionUpdated(source, definition,
                "boss=tab" + cloneTab + "/" + cloneName);
    }

    private static int addDefinitionMonsterWave(
            CommandSourceStack source,
            String id,
            int cloneTab,
            String cloneName
    ) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        cloneName = cloneName.trim();
        if (!cloneExists(cloneTab, cloneName)) {
            source.sendFailure(Component.literal(String.format(
                    "§c[NeoWorld] ไม่พบ CustomNPC clone '%s' ใน tab %d", cloneName, cloneTab)));
            return 0;
        }
        definitions(source).addMonsterWave(definition, new DungeonMonsterWave(cloneTab, cloneName, 1));
        return definitionUpdated(source, definition,
                "monster_wave=tab" + cloneTab + "/" + cloneName + " | spawn=round_robin count=1"
                        + " (#" + definition.monsterWaves().size() + ")");
    }

    private static int clearDefinitionMonsterWaves(CommandSourceStack source, String id) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        definitions(source).clearMonsterWaves(definition);
        return definitionUpdated(source, definition, "monster_waves=cleared");
    }

    private static int setDefinitionReward(
            CommandSourceStack source,
            String id,
            String name
    ) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        ResourceLocation preset = DungeonLootCommands.presetId(name);
        boolean saved = definitions(source).lootPresetIds().contains(preset);
        if (!saved && !name.contains(":")) {
            source.sendFailure(Component.literal("ไม่พบชุดรางวัล " + name + " ใช้ admin loot save ก่อน หรือระบุ Loot Table แบบ namespace:path"));
            return 0;
        }
        definitions(source).updateReward(definition, saved
                ? new DungeonRewardConfig(null, preset) : new DungeonRewardConfig(ResourceLocation.parse(name)));
        return definitionUpdated(source, definition, "reward=" + (saved ? preset : name));
    }

    private static int validateDefinition(CommandSourceStack source, String id, boolean enable) {
        DungeonDefinition definition = requireLinkedDefinition(source, id);
        if (definition == null) return 0;
        if (definition.boss() == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] Validation ไม่ผ่าน: ยังไม่ได้ตั้งค่าบอส"));
            return 0;
        }
        if (definition.reward() == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] Validation ไม่ผ่าน: ยังไม่ได้ตั้งค่ารางวัล"));
            return 0;
        }
        if (definition.reward().presetId() != null) {
            try { definitions(source).lootPresetItems(definition.reward().presetId(), source.registryAccess()); }
            catch (IllegalArgumentException exception) {
                source.sendFailure(Component.literal("ชุดรางวัลใช้ไม่ได้: " + exception.getMessage()));
                return 0;
            }
        }
        if (!cloneExists(definition.boss().cloneTab(), definition.boss().cloneName())) {
            source.sendFailure(Component.literal(String.format(
                    "§c[NeoWorld] Validation ไม่ผ่าน: ไม่พบ boss clone '%s' ใน tab %d",
                    definition.boss().cloneName(), definition.boss().cloneTab())));
            return 0;
        }
        for (int i = 0; i < definition.monsterWaves().size(); i++) {
            DungeonMonsterWave wave = definition.monsterWaves().get(i);
            if (!cloneExists(wave.cloneTab(), wave.cloneName())) {
                source.sendFailure(Component.literal(String.format(
                        "§c[NeoWorld] Validation ไม่ผ่าน: ไม่พบ wave %d clone '%s' ใน tab %d",
                        i + 1, wave.cloneName(), wave.cloneTab())));
                return 0;
            }
        }
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
        for (int i = 0; i < definition.monsterWaves().size(); i++) {
            int markers = validation.markers().getOrDefault("mob_spawn_" + (i + 1), List.of()).size();
            try {
                definition.monsterWaves().get(i).totalCount(markers);
            } catch (IllegalArgumentException exception) {
                source.sendFailure(Component.literal("Wave " + (i + 1) + ": " + exception.getMessage()));
                return 0;
            }
        }
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
                definition.structurePieces().isEmpty()
                        ? "unlinked"
                        : definition.structureId() + " (" + definition.structurePieces().size() + " pieces)",
                definition.status()
        )), false);
    }

    private static int showDefinition(CommandSourceStack source, String id) {
        DungeonDefinition definition = requireDefinition(source, id);
        if (definition == null) return 0;
        source.sendSuccess(() -> Component.literal("§6=== " + definition.displayName() + " ==="), false);
        sendDefinitionLine(source, definition);
        source.sendSuccess(() -> Component.literal("§7Image: §f" + (definition.imageId() == null ? "default" : definition.imageId())), false);
        source.sendSuccess(() -> Component.literal(definition.boss() == null
                ? "§7Boss: §funset"
                : "§7Boss: §ftab" + definition.boss().cloneTab() + "/" + definition.boss().cloneName()), false);
        source.sendSuccess(() -> Component.literal("§7Monster waves: §f" + definition.monsterWaves().size()), false);
        for (int i = 0; i < definition.monsterWaves().size(); i++) {
            int waveNumber = i + 1;
            DungeonMonsterWave wave = definition.monsterWaves().get(i);
            source.sendSuccess(() -> Component.literal(String.format(
                    "§7  Wave %d: §ftab%d/%s x%d | spawn=%s%s",
                    waveNumber, wave.cloneTab(), wave.cloneName(), wave.count(), wave.spawnMode().id(),
                    wave.spawnMode() == DungeonMonsterWave.SpawnMode.EACH ? " (ต่อจุด)" : " (รวม)")), false);
        }
        source.sendSuccess(() -> Component.literal(definition.reward() == null
                ? "§7Reward: §funset"
                : "§7Reward: §f" + definition.reward().sourceId()), false);
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
        DungeonRoomManager roomManager = DungeonRoomManager.get(source.getServer());
        if (type == DungeonRoomType.SOLO) {
            return sendRoomResult(source, roomManager.create(player, dungeonId, type));
        }

        Party party = PartyManager.get(source.getServer()).findForPlayer(player.getUUID()).orElse(null);
        if (party == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] ต้องสร้างปาร์ตี้ก่อนเข้าโหมด trio หรือ raid"));
            return 0;
        }
        if (!party.leaderId().equals(player.getUUID())) {
            source.sendFailure(Component.literal("§c[NeoWorld] เฉพาะหัวหน้าปาร์ตี้เท่านั้นที่สร้างห้องได้"));
            return 0;
        }
        if (party.capacity() != type.capacity()) {
            source.sendFailure(Component.literal("§c[NeoWorld] ขนาดปาร์ตี้ไม่ตรงกับโหมดดันเจี้ยน"));
            return 0;
        }
        for (PartyMember member : party.members()) {
            ServerPlayer memberPlayer = source.getServer().getPlayerList().getPlayer(member.playerId());
            if (memberPlayer == null) {
                source.sendFailure(Component.literal("§c[NeoWorld] สมาชิก " + member.playerName() + " ไม่ได้ออนไลน์"));
                return 0;
            }
            if (memberPlayer.getData(ModAttachments.PLAYER_DATA).getLevel() < definition.minimumLevel()) {
                source.sendFailure(Component.literal(String.format(
                        "§c[NeoWorld] สมาชิก %s ต้องมีเลเวลอย่างน้อย %d",
                        member.playerName(), definition.minimumLevel())));
                return 0;
            }
        }
        return sendRoomResult(source, roomManager.createFromParty(player, dungeonId, type, party));
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
        return sendRoomResult(source, DungeonRoomManager.get(source.getServer()).start(player));
    }

    private static int disbandRoom(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return sendRoomResult(source, DungeonRoomManager.get(source.getServer()).disband(player.getUUID()));
    }

    private static int exitDungeon(CommandSourceStack source) throws CommandSyntaxException {
        DungeonRoomManager.ExitResult result = DungeonRoomManager.get(source.getServer())
                .exitDungeon(source.getPlayerOrException());
        if (!result.successful()) {
            source.sendFailure(Component.literal("§c[NeoWorld] " + result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a[NeoWorld] " + result.message()), false);
        return 1;
    }

    private static int sendRoomResult(CommandSourceStack source, DungeonRoomManager.RoomActionResult result) {
        if (!result.successful()) {
            source.sendFailure(Component.literal("§c[NeoWorld] " + result.message()));
            return 0;
        }
        DungeonRoom room = result.room();
        if (source.getEntity() instanceof ServerPlayer player
                && room.state() == com.ambition.neoworld.dungeon.room.DungeonRoomState.WAITING
                && DungeonRoomManager.get(source.getServer()).findForPlayer(player.getUUID()).isPresent()) {
            com.ambition.neoworld.network.dungeon.DungeonRoomNetworkHandler.send(player, true);
        }
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
        if (definition != null && definition.structurePieces().isEmpty()) {
            source.sendFailure(Component.literal("§c[NeoWorld] ดันเจี้ยนยังไม่ได้เชื่อม Structure"));
            return null;
        }
        return definition;
    }

    private static DungeonDefinitionData definitions(CommandSourceStack source) {
        return DungeonDefinitionData.get(source.getServer());
    }

    private static ServerLevel dungeonLevel(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(ModDimensionRegistries.DUNGEON_LEVEL);
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

    private static List<String> structureIds(CommandSourceStack source) {
        return source.getServer().getResourceManager()
                .listResources("structure", location -> location.getPath().endsWith(".nbt"))
                .keySet().stream()
                .filter(location -> location.getNamespace().equals("neoworld"))
                .map(location -> ResourceLocation.fromNamespaceAndPath(
                        location.getNamespace(),
                        location.getPath().substring("structure/".length(), location.getPath().length() - 4)
                ).toString())
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

    private static boolean cloneExists(int cloneTab, String cloneName) {
        return !cloneName.isBlank()
                && ServerCloneController.Instance != null
                && ServerCloneController.Instance.hasClone(cloneTab, cloneName);
    }

    private static int showHelp(CommandSourceStack source, int page) {
        source.sendSuccess(() -> Component.literal(page == 1 ? "§6=== Dungeon Help ==="
                : "§6=== Dungeon Admin Help §f" + (page - 1) + "/3 §6==="), false);
        switch (page) {
            case 1 -> {
                sendHelpCommand(source, "/neow dungeon list", "ดูดันเจี้ยนที่เปิดใช้งาน", true);
                sendHelpCommand(source, "/neow dungeon info dung01", "ดูรายละเอียดดันเจี้ยน", false);
                sendHelpCommand(source, "/neow dungeon room create dung01 solo", "สร้างห้อง solo/trio/raid", false);
                sendHelpCommand(source, "/neow dungeon room list", "ดูห้องที่เปิดอยู่", true);
                sendHelpCommand(source, "/neow dungeon room join 1", "เข้าร่วมห้องตามหมายเลข", false);
                sendHelpCommand(source, "/neow dungeon room leave", "ออกจากห้อง", true);
                sendHelpCommand(source, "/neow dungeon room ready", "สลับสถานะพร้อม", true);
                sendHelpCommand(source, "/neow dungeon room start", "เริ่มดันเจี้ยน", true);
                sendHelpCommand(source, "/neow dungeon room disband", "ยุบห้อง", true);
                sendHelpCommand(source, "/neow dungeon exit", "ออกจากดันเจี้ยนทันที", true);
            }
            case 2 -> {
                sendHelpCommand(source, "/neow dungeon create dung01", "สร้าง Dungeon Definition", false);
                sendHelpCommand(source, "/neow dungeon definitions", "ดู Definition ทั้งหมด", true);
                sendHelpCommand(source, "/neow dungeon set dung01 name Forgotten Mine", "ตั้งชื่อ", false);
                sendHelpCommand(source, "/neow dungeon set dung01 level 1", "ตั้งเลเวลขั้นต่ำ", false);
                sendHelpCommand(source, "/neow dungeon set dung01 image neoworld:textures/gui/dungeon/previews/forgotten_mine.png", "ตั้งรูป PNG รวมถึงภาพ F2", false);
                sendHelpCommand(source, "/neow dungeon set dung01 image clear", "ใช้รูปเริ่มต้น", false);
                sendHelpCommand(source, "/neow dungeon set dung01 time 1200", "ตั้งเวลาเป็นวินาที", false);
                sendHelpCommand(source, "/neow dungeon set dung01 modes solo trio raid", "ตั้งโหมดที่รองรับ", false);
                sendHelpCommand(source, "/neow dungeon set dung01 structure neoworld:dungeon/forgotten_mine",
                        "เชื่อม Structure Template", false);
                sendHelpCommand(source, "/neow dungeon set dung01 structure scan",
                        "สแกน Structure Block prefix เดียวกันในรัศมี 256 บล็อก", false);
            }
            case 3 -> {
                sendHelpCommand(source, "/neow dungeon admin loot save slime_reward", "บันทึกไอเทมจากกล่องที่เล็ง (ไม่ล้างกล่อง)", false);
                sendHelpCommand(source, "/neow dungeon admin loot chance slime_reward 1 25", "ช่อง 1 มีโอกาสดรอป 25%", false);
                sendHelpCommand(source, "/neow dungeon admin loot amount slime_reward 1 2 5", "ช่อง 1 สุ่มจำนวน 2–5 ชิ้น", false);
                sendHelpCommand(source, "/neow dungeon admin set dung01 reward slime_reward", "ใช้ชุดรางวัลจากกล่อง", false);
                sendHelpCommand(source, "/neow dungeon set dung01 boss 1 Mine Boss",
                        "ตั้ง CustomNPC clone เป็นบอส", false);
                sendHelpCommand(source, "/neow dungeon set dung01 wave add 1 Cave Zombie",
                        "เพิ่ม Wave เลือก Clone (เริ่มต้น 1 ตัว ตั้งจำนวนด้วย wave edit)", false);
                sendHelpCommand(source, "/neow dungeon set dung01 wave clear",
                        "ล้าง Monster Waves", false);
                sendHelpCommand(source, "/neow dungeon set dung01 wave edit 1 spawn each 5",
                        "เกิด 5 ตัวต่อจุด mob_spawn_1", false);
                sendHelpCommand(source, "/neow dungeon set dung01 wave edit 1 spawn random 20",
                        "สุ่มกระจายรวม 20 ตัวตามจุด mob_spawn_1", false);
                sendHelpCommand(source,
                        "/neow dungeon set dung01 reward neoworld:chests/dungeon/test_reward",
                        "ตั้ง Loot Table รางวัลไอเทม", false);
            }
            case 4 -> {
                sendHelpCommand(source, "/neow dungeon structure tool", "รับไม้เลือกพื้นที่: คลิกซ้ายมุม 1 / ขวามุม 2", true);
                sendHelpCommand(source, "/neow dungeon structure selection", "ดูมุมที่เลือก", true);
                sendHelpCommand(source, "/neow dungeon admin structure list", "ดูแผนที่และปุ่มจัดการ", true);
                sendHelpCommand(source, "/neow dungeon admin structure update neoworld:dungeon/silme_map", "บันทึกพื้นที่ใหม่แทนชุดเดิม พร้อมสำรอง", false);
                sendHelpCommand(source, "/neow dungeon admin structure delete neoworld:dungeon/silme_map", "ขอยืนยันลบแผนที่ที่ไม่มีดันใช้งาน", false);
                sendHelpCommand(source, "/neow dungeon structure save forgotten_mine", "บันทึกแผนที่อย่างเดียว ไม่เชื่อมดัน", false);
                sendHelpCommand(source, "/neow dungeon set dung01 structure forgotten_mine", "เชื่อมแผนที่ทั้งชุดด้วยชื่อเดียว", false);
                sendHelpCommand(source, "/neow dungeon structure cancel", "ล้างการเลือกและยกเลิกงานบันทึก", true);
                sendHelpCommand(source, "/neow dungeon validate dung01", "ตรวจความพร้อม", false);
                sendHelpCommand(source, "/neow dungeon enable dung01", "ตรวจและเปิดใช้งาน", false);
                sendHelpCommand(source, "/neow dungeon disable dung01", "ปิดใช้งาน", false);
                sendHelpCommand(source, "/neow dungeon instances", "ดู Instance ที่กำลังทำงาน", true);
                sendHelpCommand(source, "/neow dungeon build dung01 0", "วาง Structure ใน slot", false);
                sendHelpCommand(source, "/neow dungeon clear dung01 0", "ล้าง Structure จาก slot", false);
            }
            default -> throw new IllegalArgumentException("Unknown help page: " + page);
        }
        if (page > 1) source.sendSuccess(() -> helpNavigation(page), false);
        else if (isDungeonAdmin(source)) sendHelpCommand(source, "/neow dungeon admin help", "คำสั่งแอดมิน", true);
        return 1;
    }

    private static void sendHelpCommand(
            CommandSourceStack source,
            String command,
            String description,
            boolean runOnClick
    ) {
        source.sendSuccess(() -> commandButton(command, description, runOnClick), false);
    }

    private static MutableComponent commandButton(String command, String description, boolean runOnClick) {
        String prefix = "/neow dungeon ";
        String subcommand = command.startsWith(prefix) ? command.substring(prefix.length()).split(" ", 2)[0] : "";
        String actual = java.util.Set.of("create", "definitions", "set", "structure", "validate", "enable",
                "disable", "instances", "build", "clear").contains(subcommand)
                ? prefix + "admin " + command.substring(prefix.length()) : command;
        ClickEvent.Action action = runOnClick
                ? ClickEvent.Action.RUN_COMMAND
                : ClickEvent.Action.SUGGEST_COMMAND;
        String hint = runOnClick ? "คลิกเพื่อรันคำสั่ง" : "คลิกเพื่อเติมคำสั่งในช่องแชท";
        return Component.literal("  " + actual)
                .withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent(action, actual))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal(hint))))
                .append(Component.literal(" §7- §f" + description));
    }

    private static MutableComponent helpNavigation(int page) {
        MutableComponent navigation = Component.literal("");
        if (page > 2) navigation.append(helpPageButton("§e[ก่อนหน้า]", page - 1));
        if (page > 2 && page < 4) navigation.append(Component.literal("  "));
        if (page < 4) navigation.append(helpPageButton("§e[ถัดไป]", page + 1));
        return navigation;
    }

    private static MutableComponent helpPageButton(String label, int page) {
        String command = "/neow dungeon admin help " + (page - 1);
        return Component.literal(label).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.literal("ไปหน้า " + page))));
    }
}
