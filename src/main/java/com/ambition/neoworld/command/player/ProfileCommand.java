package com.ambition.neoworld.command.player;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.player.PlayerActionPayload;
import com.ambition.neoworld.network.PlayerProfilePayload;
import com.ambition.neoworld.network.player.PlayerProfileSnapshot;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class ProfileCommand {
    private ProfileCommand() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("profile")
                .executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (!player.isAlive() || player.isSpectator()) {
                        context.getSource().sendFailure(Component.literal("ไม่สามารถเปิดโปรไฟล์ขณะตายหรืออยู่ในโหมดผู้ชม"));
                        return 0;
                    }
                    var request = new PlayerActionPayload(PlayerProfilePayload.COMMAND_REQUEST,
                            player.getUUID(), PlayerActionPayload.Action.PROFILE);
                    PacketDistributor.sendToPlayer(player, PlayerProfileSnapshot.capture(request, player));
                    return 1;
                }));
    }
}
