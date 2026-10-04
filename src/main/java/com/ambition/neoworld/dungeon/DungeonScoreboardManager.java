package com.ambition.neoworld.dungeon;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Sends a private vanilla sidebar scoreboard to each dungeon player. */
public final class DungeonScoreboardManager {
    private static final Map<UUID, VirtualBoard> BOARDS = new ConcurrentHashMap<>();

    private DungeonScoreboardManager() {
    }

    public static void show(ServerPlayer player, String title, List<Component> lines) {
        VirtualBoard board = BOARDS.get(player.getUUID());
        if (board == null || !board.title().equals(title)) {
            if (board != null) removeBoard(player, board);
            board = createBoard(player, title);
            BOARDS.put(player.getUUID(), board);
        }

        int score = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            player.connection.send(new ClientboundSetScorePacket(
                    "nw_dungeon_" + i,
                    board.objective().getName(),
                    score--,
                    java.util.Optional.of(lines.get(i)),
                    java.util.Optional.of(BlankFormat.INSTANCE)
            ));
        }
    }

    public static void hide(ServerPlayer player) {
        VirtualBoard board = BOARDS.remove(player.getUUID());
        if (board == null) return;
        removeBoard(player, board);
        Objective serverSidebar = player.getServer().getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (serverSidebar != null) {
            player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, serverSidebar));
        }
    }

    public static void forget(UUID playerId) {
        BOARDS.remove(playerId);
    }

    private static VirtualBoard createBoard(ServerPlayer player, String title) {
        String objectiveName = "nwd_" + player.getUUID().toString().substring(0, 8);
        Scoreboard scoreboard = new Scoreboard();
        Objective objective = scoreboard.addObjective(
                objectiveName,
                ObjectiveCriteria.DUMMY,
                Component.literal(title),
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                BlankFormat.INSTANCE
        );
        player.connection.send(new ClientboundSetObjectivePacket(
                objective, ClientboundSetObjectivePacket.METHOD_ADD));
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
        return new VirtualBoard(title, objective);
    }

    private static void removeBoard(ServerPlayer player, VirtualBoard board) {
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
        player.connection.send(new ClientboundSetObjectivePacket(
                board.objective(), ClientboundSetObjectivePacket.METHOD_REMOVE));
    }

    private record VirtualBoard(String title, Objective objective) {
    }
}
