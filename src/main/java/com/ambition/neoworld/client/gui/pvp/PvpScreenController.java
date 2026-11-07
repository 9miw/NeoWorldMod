package com.ambition.neoworld.client.gui.pvp;

import net.minecraft.client.Minecraft;
import com.ambition.neoworld.network.pvp.PvpGuiDataPayload;
public final class PvpScreenController {
    private PvpScreenController() { }
    public static void open() { if(!PvpMatchScreenController.openCurrent()) Minecraft.getInstance().setScreen(new PvpSelectionScreen()); }
    public static void receive(PvpGuiDataPayload data) {
        if(Minecraft.getInstance().screen instanceof PvpSelectionScreen screen) screen.receive(data);
    }
    public static void invitation(com.ambition.neoworld.network.pvp.PvpInvitationPayload invitation) {
        com.ambition.neoworld.client.gui.common.NeoPopupScreen.showCountdownChoice(
                net.minecraft.network.chat.Component.literal("คำเชิญ PvP"),
                net.minecraft.network.chat.Component.literal(invitation.owner()+" เชิญคุณเข้าห้อง #"+invitation.room()),30,
                net.minecraft.network.chat.Component.literal("เข้าร่วม"),() -> answer(invitation,true),
                net.minecraft.network.chat.Component.literal("ปฏิเสธ"),() -> answer(invitation,false),() -> {});
    }
    private static void answer(com.ambition.neoworld.network.pvp.PvpInvitationPayload invitation,boolean accept) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.ambition.neoworld.network.pvp.PvpGuiRequestPayload(java.util.UUID.randomUUID(),
                accept ? com.ambition.neoworld.network.pvp.PvpGuiRequestPayload.Action.ACCEPT_INVITE : com.ambition.neoworld.network.pvp.PvpGuiRequestPayload.Action.DECLINE_INVITE,invitation.token().toString()));
    }
}
