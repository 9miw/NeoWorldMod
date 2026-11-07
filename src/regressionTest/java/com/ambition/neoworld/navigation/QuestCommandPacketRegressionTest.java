package com.ambition.neoworld.navigation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;

/** Exercise NeoForge and Arclight identity-map exports through the real join-packet validator. */
public final class QuestCommandPacketRegressionTest {
    public static void run() {
        // Headless fixture: initialize only vanilla argument serializers, without booting blocks/mod loading.
        try {
            var bootstrapped=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
            bootstrapped.setAccessible(true);bootstrapped.setBoolean(null,true);
        } catch(ReflectiveOperationException exception) { throw new AssertionError(exception); }
        net.minecraft.commands.synchronization.ArgumentTypeInfos.bootstrap(net.minecraft.core.registries.BuiltInRegistries.COMMAND_ARGUMENT_TYPE);
        for(boolean arclight:new boolean[]{false,true}) {
        for(int permission:new int[]{0,1,2,3,4}) {
            var dispatcher=new CommandDispatcher<CommandSourceStack>();
            com.ambition.neoworld.command.NeoWorldCommand.registerCommands(new net.neoforged.neoforge.event.RegisterCommandsEvent(
                    dispatcher,Commands.CommandSelection.DEDICATED,null));
            if (dispatcher.getRoot().getChildren().size() != 1
                    || dispatcher.getRoot().getChild("neow") == null
                    || dispatcher.getRoot().getChild("neow").getRedirect() != null) {
                throw new AssertionError("NeoWorld must register only the /neow root without legacy aliases");
            }
            var root = dispatcher.getRoot().getChild("neow");
            if (root.getChild("profile") == null || root.getChild("economyreport") == null) {
                throw new AssertionError("Profile and economy report commands must belong to /neow");
            }
            var source=new CommandSourceStack(net.minecraft.commands.CommandSource.NULL,net.minecraft.world.phys.Vec3.ZERO,
                    net.minecraft.world.phys.Vec2.ZERO,null,permission,"test",net.minecraft.network.chat.Component.literal("test"),null,null);
            var exported=new RootCommandNode<SharedSuggestionProvider>();
            java.util.Map<com.mojang.brigadier.tree.CommandNode<CommandSourceStack>,com.mojang.brigadier.tree.CommandNode<SharedSuggestionProvider>> mapping=arclight ? new java.util.IdentityHashMap<>() : new java.util.HashMap<>();
            net.neoforged.neoforge.server.command.CommandHelper.mergeCommandNode(dispatcher.getRoot(),exported,mapping,source,
                    context->0,suggestions->net.minecraft.commands.synchronization.SuggestionProviders.ASK_SERVER);
            var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                ClientboundCommandsPacket.STREAM_CODEC.encode(buffer,new ClientboundCommandsPacket(exported));
                ClientboundCommandsPacket.STREAM_CODEC.decode(buffer);
                if(buffer.isReadable())throw new AssertionError("Command packet was not fully decoded");
            } finally {buffer.release();}
        }
        }
        System.out.println("Quest command join-packet regression checks passed");
    }
}
