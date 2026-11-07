package com.ambition.neoworld.command.player;

import com.ambition.neoworld.network.player.PlayerActionPayload;
import com.ambition.neoworld.network.PlayerProfilePayload;
import com.ambition.neoworld.network.player.PlayerProfileSnapshot;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ProfileCommand {
    private ProfileCommand() {}
    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("profile")
                .executes(context -> {
                    var player = context.getSource().getPlayerOrException();
                    if (!player.isAlive() || player.isSpectator()) {
                        context.getSource().sendFailure(Component.literal("ไม่สามารถเปิดโปรไฟล์ขณะตายหรืออยู่ในโหมดผู้ชม"));
                        return 0;
                    }
                    var request = new PlayerActionPayload(PlayerProfilePayload.COMMAND_REQUEST,
                            player.getUUID(), PlayerActionPayload.Action.PROFILE);
                    com.ambition.neoworld.network.player.PlayerProfileService.send(request,player,player);
                    return 1;
                });
    }
}
