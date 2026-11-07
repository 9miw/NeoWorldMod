package com.ambition.neoworld.command.dungeon;

import com.ambition.neoworld.dungeon.definition.DungeonDefinitionData;
import com.ambition.neoworld.dungeon.structure.DungeonCaptureService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;

public final class DungeonLootCommands {
    private DungeonLootCommands() {}

    public static ResourceLocation presetId(String name) {
        return ResourceLocation.parse(name.contains(":") ? name : "neoworld:dungeon/rewards/" + name);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("loot").requires(DungeonCaptureService::allowed)
                .then(Commands.literal("chance").then(Commands.argument("name", ResourceLocationArgument.id())
                        .suggests(DungeonLootCommands::suggestPresets)
                        .then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 54))
                                .then(Commands.argument("percent", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0, 100))
                                        .executes(context -> configure(context, false))))))
                .then(Commands.literal("amount").then(Commands.argument("name", ResourceLocationArgument.id())
                        .suggests(DungeonLootCommands::suggestPresets)
                        .then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 54))
                                .then(Commands.argument("minimum", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 99))
                                        .then(Commands.argument("maximum", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 99))
                                                .executes(context -> configure(context, true)))))))
                .then(Commands.literal("save").then(Commands.argument("name", ResourceLocationArgument.id())
                        .executes(context -> {
                            String name = context.getNodes().stream().filter(node -> node.getNode().getName().equals("name"))
                                    .findFirst().orElseThrow().getRange().get(context.getInput());
                            return save(context.getSource(), presetId(name));
                        })));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPresets(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return net.minecraft.commands.SharedSuggestionProvider.suggest(
                DungeonDefinitionData.get(context.getSource().getServer()).lootPresetIds().stream().map(Object::toString), builder);
    }

    private static int configure(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, boolean amount) {
        var source = context.getSource();
        String name = context.getNodes().stream().filter(node -> node.getNode().getName().equals("name"))
                .findFirst().orElseThrow().getRange().get(context.getInput());
        ResourceLocation id = presetId(name);
        int slot = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "slot") - 1;
        var data = DungeonDefinitionData.get(source.getServer());
        if (com.ambition.neoworld.dungeon.instance.DungeonRuntime.get(source.getServer()).activeInstances().stream()
                .anyMatch(instance -> data.get(instance.dungeonId()).map(definition -> definition.reward() != null
                        && id.equals(definition.reward().presetId())).orElse(false))) {
            source.sendFailure(Component.literal("ชุดรางวัลนี้มีดันกำลังใช้งาน กรุณารอให้จบรอบก่อน"));
            return 0;
        }
        try {
            var items = data.lootPresetItems(id, source.registryAccess());
            if (slot >= items.size() || items.get(slot).isEmpty()) throw new IllegalArgumentException("ช่องนี้ไม่มีไอเทม");
            var old = data.lootRule(id, slot);
            var rule = amount ? new com.ambition.neoworld.dungeon.definition.DungeonLootRule(old.chance(),
                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "minimum"),
                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "maximum"))
                    : new com.ambition.neoworld.dungeon.definition.DungeonLootRule(
                            com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "percent"), old.minimum(), old.maximum());
            if (rule.maximum() > items.get(slot).getMaxStackSize()) {
                throw new IllegalArgumentException("จำนวนสูงสุดเกินที่ไอเทมซ้อนได้: " + items.get(slot).getMaxStackSize());
            }
            data.setLootRule(id, slot, rule);
            source.sendSuccess(() -> Component.literal("§a" + id + " ช่อง " + (slot + 1) + " | โอกาส " + rule.chance()
                    + "% | จำนวน " + (rule.minimum() == 0 ? "ตามกล่อง" : rule.minimum() + "–" + rule.maximum())
                    + " | ดันที่เชื่อมกลับเป็น DRAFT กรุณา validate/enable ใหม่"), false);
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("ตั้งค่ารางวัลไม่สำเร็จ: " + exception.getMessage()));
            return 0;
        }
    }

    private static int save(CommandSourceStack source, ResourceLocation id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (id.toString().length() > 230) {
            source.sendFailure(Component.literal("ชื่อชุดรางวัลยาวเกินไป"));
            return 0;
        }
        var player = source.getPlayerOrException();
        var level = player.serverLevel();
        if (!(player.pick(8, 1, false) instanceof BlockHitResult hit)
                || !(level.getBlockEntity(hit.getBlockPos()) instanceof ChestBlockEntity chest)) {
            source.sendFailure(Component.literal("กรุณาเล็งกล่องภายในระยะ 8 บล็อก"));
            return 0;
        }
        var state = level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof ChestBlock block)) return 0;
        if (chest.getLootTable() != null || (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                && level.getBlockEntity(hit.getBlockPos().relative(ChestBlock.getConnectedDirection(state))) instanceof ChestBlockEntity other
                && other.getLootTable() != null)) {
            source.sendFailure(Component.literal("กรุณาเปิดกล่อง Loot Table ก่อน แล้วจัดไอเทมตัวอย่างที่จะบันทึก"));
            return 0;
        }
        var container = ChestBlock.getContainer(block, state, level, hit.getBlockPos(), true);
        if (container == null || container.getContainerSize() > 54) return 0;
        try {
            java.util.List<net.minecraft.nbt.CompoundTag> items = new java.util.ArrayList<>();
            var bytes = new java.io.ByteArrayOutputStream();
            var output = new java.io.DataOutputStream(bytes);
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                var stack = container.getItem(slot);
                if (!stack.isEmpty() && stack.getCount() > stack.getMaxStackSize()) throw new IllegalArgumentException("Item count exceeds stack limit");
                if (!(stack.saveOptional(source.registryAccess()) instanceof net.minecraft.nbt.CompoundTag tag)) {
                    throw new IllegalArgumentException("Invalid item data");
                }
                net.minecraft.nbt.NbtIo.write(tag, output);
                if (bytes.size() > 65536) throw new IllegalArgumentException("Reward data exceeds 64 KiB");
                items.add(tag);
            }
            DungeonDefinitionData.get(source.getServer()).addLootPreset(id, items);
            source.sendSuccess(() -> Component.literal("§aบันทึกชุดรางวัล " + id
                    + " แล้ว | เชื่อมด้วย /neow dungeon admin set <id> reward " + id), false);
            return 1;
        } catch (IllegalArgumentException | java.io.IOException exception) {
            source.sendFailure(Component.literal("บันทึกชุดรางวัลไม่สำเร็จ: " + exception.getMessage()));
            return 0;
        }
    }
}
