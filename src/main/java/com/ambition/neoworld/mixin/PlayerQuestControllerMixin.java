package com.ambition.neoworld.mixin;

import com.ambition.neoworld.navigation.QuestNavigationService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.PlayerQuestController;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QuestStartEvent is cancellable and fires before insertion; observe only successful insertion. */
@Mixin(value = PlayerQuestController.class, remap = false)
public abstract class PlayerQuestControllerMixin {
    @Inject(method = "addActiveQuest", at = @At(value = "INVOKE",
            target = "Ljava/util/HashMap;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.AFTER))
    private static void neoworld$trackAcceptedQuest(Quest quest, Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) QuestNavigationService.questAccepted(serverPlayer, quest.id);
    }
}
