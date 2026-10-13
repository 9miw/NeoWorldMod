package com.ambition.neoworld.command.party;

import com.ambition.neoworld.dungeon.instance.DungeonRuntime;
import com.ambition.neoworld.dungeon.room.DungeonRoomManager;
import com.ambition.neoworld.network.party.OpenPartyPayload;
import com.ambition.neoworld.party.Party;
import com.ambition.neoworld.party.PartyActionResult;
import com.ambition.neoworld.party.PartyManager;
import com.ambition.neoworld.party.PartyMember;
import com.ambition.neoworld.permission.ModPermissions;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PartyCommand {
    private PartyCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("party")
                .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_DUNGEON_USE, 0))
                .then(Commands.literal("create")
                        .then(Commands.argument("size", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.List.of("trio", "raid", "3", "6"), builder))
                                .executes(context -> createParty(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "size")))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getOnlinePlayerNames(), builder))
                                .executes(context -> invite(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("leader", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getOnlinePlayerNames(), builder))
                                .executes(context -> accept(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "leader")))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("leader", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getOnlinePlayerNames(), builder))
                                .executes(context -> decline(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "leader")))))
                .then(Commands.literal("leave").executes(context -> leave(context.getSource())))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getOnlinePlayerNames(), builder))
                                .executes(context -> kick(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player")))))
                .then(Commands.literal("disband").executes(context -> disband(context.getSource())))
                .then(Commands.literal("ready").executes(context -> ready(context.getSource())))
                .then(Commands.literal("info").executes(context -> info(context.getSource())))
                .then(Commands.literal("gui").executes(context -> openGui(context.getSource())))
                .then(Commands.literal("open").executes(context -> openGui(context.getSource())))
                .then(Commands.literal("list")
                        .requires(source -> ModPermissions.hasPermission(source, ModPermissions.COMMAND_DUNGEON_ADMIN, 2))
                        .executes(context -> listParties(context.getSource())));
    }

    private static int createParty(CommandSourceStack source, String sizeName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (isBusy(source, player)) return 0;
        int capacity = switch (sizeName.toLowerCase(java.util.Locale.ROOT)) {
            case "trio", "3" -> 3;
            case "raid", "6" -> 6;
            default -> 0;
        };
        return sendResult(source, PartyManager.get(source.getServer()).create(player, capacity));
    }

    private static int invite(CommandSourceStack source, String targetName) throws CommandSyntaxException {
        ServerPlayer leader = source.getPlayerOrException();
        Party party = PartyManager.get(source.getServer()).findForPlayer(leader.getUUID()).orElse(null);
        if (partyHasDungeonRoom(source, party)) return 0;
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] ไม่พบผู้เล่นออนไลน์: " + targetName));
            return 0;
        }
        if (isBusy(source, target)) return 0;
        PartyActionResult result = PartyManager.get(source.getServer()).invite(leader, target);
        if (result.successful()) {
            target.sendSystemMessage(Component.literal("§e[NeoWorld] " + leader.getGameProfile().getName()
                    + " เชิญคุณเข้าปาร์ตี้ ใช้ /neoworld party accept " + leader.getGameProfile().getName()));
        }
        return sendResult(source, result);
    }

    private static int accept(CommandSourceStack source, String leaderName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (isBusy(source, player)) return 0;
        ServerPlayer leader = source.getServer().getPlayerList().getPlayerByName(leaderName);
        if (leader == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] ไม่พบหัวหน้าปาร์ตี้ออนไลน์: " + leaderName));
            return 0;
        }
        Party party = PartyManager.get(source.getServer()).findForPlayer(leader.getUUID()).orElse(null);
        if (partyHasDungeonRoom(source, party)) return 0;
        return sendResult(source, PartyManager.get(source.getServer()).accept(player, leader));
    }

    private static int decline(CommandSourceStack source, String leaderName) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerPlayer leader = source.getServer().getPlayerList().getPlayerByName(leaderName);
        if (leader == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] ไม่พบหัวหน้าปาร์ตี้ออนไลน์: " + leaderName));
            return 0;
        }
        return sendResult(source, PartyManager.get(source.getServer()).decline(player, leader));
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Party party = PartyManager.get(source.getServer()).findForPlayer(player.getUUID()).orElse(null);
        if (partyHasDungeonRoom(source, party)) return 0;
        return sendResult(source, PartyManager.get(source.getServer()).leave(player.getUUID()));
    }

    private static int kick(CommandSourceStack source, String targetName) throws CommandSyntaxException {
        ServerPlayer leader = source.getPlayerOrException();
        Party party = PartyManager.get(source.getServer()).findForPlayer(leader.getUUID()).orElse(null);
        if (partyHasDungeonRoom(source, party)) return 0;
        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] ไม่พบผู้เล่นออนไลน์: " + targetName));
            return 0;
        }
        return sendResult(source, PartyManager.get(source.getServer()).kick(leader.getUUID(), target.getUUID()));
    }

    private static int disband(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Party party = PartyManager.get(source.getServer()).findForPlayer(player.getUUID()).orElse(null);
        if (partyHasDungeonRoom(source, party)) return 0;
        return sendResult(source, PartyManager.get(source.getServer()).disband(player.getUUID()));
    }

    private static int ready(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return sendResult(source, PartyManager.get(source.getServer()).toggleReady(player.getUUID()));
    }

    private static int info(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Party party = PartyManager.get(source.getServer()).findForPlayer(player.getUUID()).orElse(null);
        if (party == null) {
            source.sendFailure(Component.literal("§c[NeoWorld] คุณไม่ได้อยู่ในปาร์ตี้"));
            return 0;
        }
        sendParty(source, party);
        return 1;
    }

    private static int openGui(CommandSourceStack source) throws CommandSyntaxException {
        PacketDistributor.sendToPlayer(source.getPlayerOrException(), OpenPartyPayload.INSTANCE);
        return 1;
    }

    private static int listParties(CommandSourceStack source) {
        var parties = PartyManager.get(source.getServer()).parties();
        source.sendSuccess(() -> Component.literal("§6=== Parties: §f" + parties.size() + " §6==="), false);
        parties.forEach(party -> sendParty(source, party));
        return parties.size();
    }

    private static boolean isBusy(CommandSourceStack source, ServerPlayer player) {
        if (DungeonRoomManager.get(source.getServer()).findForPlayer(player.getUUID()).isPresent()) {
            source.sendFailure(Component.literal("§c[NeoWorld] ผู้เล่นอยู่ในห้องดันเจี้ยนแล้ว"));
            return true;
        }
        if (DungeonRuntime.get(source.getServer()).getForPlayer(player.getUUID()).isPresent()) {
            source.sendFailure(Component.literal("§c[NeoWorld] ผู้เล่นอยู่ใน Dungeon Instance แล้ว"));
            return true;
        }
        return false;
    }

    private static boolean partyHasDungeonRoom(CommandSourceStack source, Party party) {
        if (party == null) return false;
        boolean hasRoom = party.members().stream()
                .anyMatch(member -> DungeonRoomManager.get(source.getServer())
                        .findForPlayer(member.playerId())
                        .isPresent());
        if (hasRoom) {
            source.sendFailure(Component.literal(
                    "§c[NeoWorld] ปาร์ตี้นี้ถูกผูกกับห้องดันเจี้ยนแล้ว ใช้คำสั่ง dungeon room แทน"));
            return true;
        }
        return false;
    }

    private static int sendResult(CommandSourceStack source, PartyActionResult result) {
        if (!result.successful()) {
            source.sendFailure(Component.literal("§c[NeoWorld] " + result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§a[NeoWorld] " + result.message()), false);
        if (result.party() != null) {
            sendParty(source, result.party());
        }
        return 1;
    }

    private static void sendParty(CommandSourceStack source, Party party) {
        String leaderName = party.members().stream()
                .filter(member -> member.playerId().equals(party.leaderId()))
                .map(PartyMember::playerName)
                .findFirst()
                .orElse("unknown");
        source.sendSuccess(() -> Component.literal(String.format(
                "§eParty §7| §f%d/%d §7| leader=§f%s",
                party.memberCount(), party.capacity(), leaderName)), false);
        for (PartyMember member : party.members()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "§7- §f%s", member.playerName())), false);
        }
    }
}
