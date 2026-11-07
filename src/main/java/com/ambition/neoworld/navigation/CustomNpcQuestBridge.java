package com.ambition.neoworld.navigation;

import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.constants.EnumQuestCompletion;

/** Read-only adapter; Custom NPCs retains all objective and reward handling. */
public final class CustomNpcQuestBridge {
    public static java.util.List<String> pendingLocations(ServerPlayer player,int quest) {
        var data=PlayerData.get(player).questData.activeQuests.get(quest);
        if(data==null || data.quest==null || !(data.quest.questInterface instanceof noppes.npcs.quests.QuestLocation location))
            return java.util.List.of();
        return pendingLocations(location,data);
    }
    static java.util.List<String> pendingLocations(noppes.npcs.quests.QuestLocation location,
            noppes.npcs.controllers.data.QuestData data) {
        var names=new String[]{location.location,location.location2,location.location3};
        var pending=new java.util.ArrayList<String>();
        for(int i=0;i<names.length;i++)
            if(names[i]!=null && !names[i].isEmpty() && !location.getFound(data,i+1)) pending.add(names[i]);
        return java.util.List.copyOf(pending);
    }
    public record Snapshot(QuestNavigationState.Phase phase,String completerNpc) {}
    private CustomNpcQuestBridge() {}
    public static QuestNavigationState.Phase phase(ServerPlayer player,int quest) {
        return snapshot(player,quest).phase();
    }
    public static Snapshot snapshot(ServerPlayer player,int quest) {
        var data=PlayerData.get(player).questData.activeQuests.get(quest);
        if(data==null || data.quest==null) return new Snapshot(QuestNavigationState.Phase.NONE,"");
        return new Snapshot(QuestNavigationState.phase(true,data.isCompleted,false,data.quest.completion==EnumQuestCompletion.Instant),
                data.quest.completerNpc==null ? "" : data.quest.completerNpc);
    }
}
