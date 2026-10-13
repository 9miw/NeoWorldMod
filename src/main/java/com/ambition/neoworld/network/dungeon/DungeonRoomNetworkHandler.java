package com.ambition.neoworld.network.dungeon;

import java.util.List;
import com.ambition.neoworld.dungeon.definition.DungeonDefinitionData;
import com.ambition.neoworld.dungeon.room.DungeonRoomManager;
import com.ambition.neoworld.dungeon.room.DungeonRoomState;
import com.ambition.neoworld.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class DungeonRoomNetworkHandler {
    private DungeonRoomNetworkHandler() {}
    public static void send(ServerPlayer player, boolean open) {
        if (!com.ambition.neoworld.permission.ModPermissions.hasPermission(player.createCommandSourceStack(),
                com.ambition.neoworld.permission.ModPermissions.COMMAND_DUNGEON_USE, 0)) return;
        var server = player.getServer();
        var room = DungeonRoomManager.get(server).findForPlayer(player.getUUID()).orElse(null);
        var definition = room == null ? null : DungeonDefinitionData.get(server).get(room.dungeonId()).orElse(null);
        if (room == null || definition == null) {
            PacketDistributor.sendToPlayer(player, new DungeonRoomViewPayload(open, 0, "", "", 0, 0, 0, 0, false, List.of()));
            return;
        }
        var members = room.members().stream().map(member -> {
            var online = server.getPlayerList().getPlayer(member.playerId());
            boolean owner = member.playerId().equals(room.ownerId());
            return new DungeonRoomViewPayload.Member(member.playerId(), member.playerName(),
                    online == null ? 0 : online.getData(ModAttachments.PLAYER_DATA).getLevel(), owner,
                    online != null && room.isReady(member.playerId()), online != null);
        }).toList();
        PacketDistributor.sendToPlayer(player, new DungeonRoomViewPayload(open, room.id(), definition.displayName(),
                definition.structureId() == null ? "" : definition.structureId().toString(), room.type().capacity(),
                definition.minimumLevel(), definition.timeLimitSeconds(), definition.monsterWaves().size(),
                room.state() == DungeonRoomState.WAITING, members));
    }
}
