package com.ambition.neoworld.command;

import com.ambition.neoworld.lootbox.LootBoxDefinitions;
import com.ambition.neoworld.registry.ModDataComponents;
import com.ambition.neoworld.registry.ModItems;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Held-box administration. Add uses the offhand box and main-hand reward. */
public final class LootBoxCommand {
    private LootBoxCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("lootbox").requires(source -> source.hasPermission(2))
                .executes(c -> run(c.getSource(), () -> {
                    c.getSource().sendSuccess(() -> Component.literal("ถือกล่อง: /neow lootbox create | count <จำนวนรายการ> | list | remove <ลำดับ> | give <ผู้เล่น> <จำนวนกล่อง> / เพิ่มรางวัล: กล่องมือรอง ไอเทมมือหลัก แล้ว add <น้ำหนัก>"), false);
                    return 1;
                }))
                .then(Commands.literal("create").executes(c -> run(c.getSource(), () -> {
                    var player = c.getSource().getPlayerOrException();
                    var box = heldBox(player, false);
                    String id = "box_" + java.util.UUID.randomUUID().toString().replace("-", "");
                    LootBoxDefinitions.get(player.server).create(id);
                    box.set(ModDataComponents.LOOT_BOX_ID.get(), id);
                    player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
                    c.getSource().sendSuccess(() -> Component.literal("ตั้งค่ากล่องที่ถือแล้ว ย้ายกล่องไปมือรองและถือรางวัลในมือหลัก จากนั้นใช้ /neow lootbox add 10"), true);
                    return 1;
                })))
                .then(Commands.literal("add").then(Commands.argument("weight", DoubleArgumentType.doubleArg(0, 100))
                        .executes(c -> run(c.getSource(), () -> {
                            var player = c.getSource().getPlayerOrException();
                            var box = player.getOffhandItem();
                            String id = boxId(box);
                            var item = player.getMainHandItem().copy();
                            double weight = DoubleArgumentType.getDouble(c, "weight");
                            LootBoxDefinitions.get(player.server).add(id, item, weight);
                            c.getSource().sendSuccess(() -> Component.literal("เพิ่มรางวัล: ").append(item.getDisplayName())
                                    .append(" ×" + item.getCount() + " / น้ำหนัก " + weight + " (สุ่มไม่ซ้ำ)"), true);
                            return 1;
                        }))))
                .then(Commands.literal("count").then(Commands.argument("count", IntegerArgumentType.integer(1, 9))
                        .executes(c -> run(c.getSource(), () -> {
                            var player = c.getSource().getPlayerOrException();
                            int count = IntegerArgumentType.getInteger(c, "count");
                            LootBoxDefinitions.get(player.server).setCount(boxId(heldBox(player, true)), count);
                            c.getSource().sendSuccess(() -> Component.literal("กล่องที่ถือจะสุ่มให้ " + count + " รายการไม่ซ้ำต่อกล่องแล้ว"), true);
                            return 1;
                        }))))
                .then(Commands.literal("list").executes(c -> run(c.getSource(), () -> {
                    var player = c.getSource().getPlayerOrException();
                    String id = boxId(heldBox(player, true));
                    var data = LootBoxDefinitions.get(player.server); var entries = data.entries(id);
                    c.getSource().sendSuccess(() -> Component.literal("กล่องที่ถือ / รางวัลทั้งหมด " + entries.size()
                            + " รายการ (สูงสุด 9) / ได้รับ " + data.count(id) + " รายการต่อกล่อง"), false);
                    for (int i = 0; i < entries.size(); i++) {
                        int index = i + 1; var entry = entries.get(i); var item = entry.item();
                        c.getSource().sendSuccess(() -> Component.literal(index + ". ").append(item.getDisplayName())
                                .append(" ×" + item.getCount() + " / น้ำหนัก " + entry.chance()), false);
                    }
                    return 1;
                })))
                .then(Commands.literal("remove").then(Commands.argument("index", IntegerArgumentType.integer(1, 9))
                        .executes(c -> run(c.getSource(), () -> {
                            var player = c.getSource().getPlayerOrException();
                            int index = IntegerArgumentType.getInteger(c, "index");
                            LootBoxDefinitions.get(player.server).remove(boxId(heldBox(player, true)), index);
                            c.getSource().sendSuccess(() -> Component.literal("ลบรางวัลลำดับ " + index + " จากกล่องที่ถือแล้ว"), true);
                            return 1;
                        }))))
                .then(Commands.literal("give").then(Commands.argument("players", EntityArgument.players())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(c -> run(c.getSource(), () -> {
                                    var owner = c.getSource().getPlayerOrException();
                                    var template = heldBox(owner, true).copyWithCount(1);
                                    LootBoxDefinitions.get(owner.server).entries(boxId(template));
                                    int count = IntegerArgumentType.getInteger(c, "count");
                                    var players = EntityArgument.getPlayers(c, "players");
                                    for (var player : players) {
                                        var box = template.copyWithCount(count);
                                        player.getInventory().add(box);
                                        if (!box.isEmpty()) {
                                            var dropped = player.drop(box, false);
                                            if (dropped != null) { dropped.setNoPickUpDelay(); dropped.setTarget(player.getUUID()); }
                                        }
                                        player.getInventory().setChanged(); player.containerMenu.broadcastChanges();
                                    }
                                    c.getSource().sendSuccess(() -> Component.literal("แจกกล่องชนิดที่ถือ ×" + count + " ให้ " + players.size()
                                            + " คนแล้ว (กระเป๋าเต็มจะวางของข้างตัว)"), true);
                                    return players.size();
                                })))));
    }
    private static ItemStack heldBox(ServerPlayer player, boolean configured) {
        for (var stack : java.util.List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.is(ModItems.LOOT_BOX.get()) && stack.has(ModDataComponents.LOOT_BOX_ID.get()) == configured) return stack;
        }
        throw new IllegalArgumentException(configured ? "กรุณาถือกล่องที่ตั้งค่าแล้วในมือหลักหรือมือรอง" : "กรุณาถือกล่องใหม่ที่ยังไม่ตั้งค่า แล้วใช้ /neow lootbox create");
    }
    private static String boxId(ItemStack stack) {
        if (!stack.is(ModItems.LOOT_BOX.get()) || !stack.has(ModDataComponents.LOOT_BOX_ID.get()))
            throw new IllegalArgumentException("กรุณาถือกล่องที่ตั้งค่าแล้วในมือรอง และไอเทมรางวัลในมือหลัก");
        return stack.get(ModDataComponents.LOOT_BOX_ID.get());
    }
    private interface Action { int run() throws com.mojang.brigadier.exceptions.CommandSyntaxException; }
    private static int run(CommandSourceStack source, Action action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try { return action.run(); }
        catch (IllegalArgumentException invalid) { source.sendFailure(Component.literal(invalid.getMessage())); return 0; }
    }
}
