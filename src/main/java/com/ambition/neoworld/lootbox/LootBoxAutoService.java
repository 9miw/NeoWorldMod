package com.ambition.neoworld.lootbox;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.network.lootbox.LootBoxAutoRequestPayload;
import com.ambition.neoworld.network.lootbox.LootBoxAutoStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = NeoWorld.MODID)
public final class LootBoxAutoService {
    private record Context(UUID token, InteractionHand hand, int slot, ItemStack box, long expires) {}
    private record Session(Context context, LootBoxAutoRun run) {}
    private static final Map<UUID,Context> LAST = new HashMap<>();
    private static final Map<UUID,Session> ACTIVE = new HashMap<>();
    private LootBoxAutoService() {}

    public static UUID remember(ServerPlayer player, InteractionHand hand, ItemStack box) {
        UUID token = UUID.randomUUID();
        LAST.put(player.getUUID(),new Context(token,hand,player.getInventory().selected,box.copyWithCount(1),
                player.server.overworld().getGameTime() + 1200));
        return token;
    }
    public static void request(ServerPlayer player, LootBoxAutoRequestPayload request) {
        if (request.count() == 0) { stop(player); return; }
        // Never extend an in-progress request by replaying a start packet.
        if (ACTIVE.containsKey(player.getUUID())) { state(player); return; }
        var context = LAST.get(player.getUUID());
        long tick = player.server.overworld().getGameTime();
        if (context == null || !context.token().equals(request.opening()) || context.expires() < tick || !valid(player,context)) {
            stop(player); return;
        }
        // A token may start at most once; future result packets provide a fresh one.
        LAST.remove(player.getUUID());
        ACTIVE.put(player.getUUID(),new Session(context,new LootBoxAutoRun(request.count(),tick,request.fast())));
        if (request.fast()) player.getCooldowns().addCooldown(context.box().getItem(),LootBoxAutoRun.FAST_INTERVAL);
        state(player);
    }
    private static boolean valid(ServerPlayer player, Context context) {
        return player.isAlive() && !player.isSpectator() && player.containerMenu == player.inventoryMenu
                && (context.hand() != InteractionHand.MAIN_HAND || context.slot() == player.getInventory().selected)
                && ItemStack.isSameItemSameComponents(player.getItemInHand(context.hand()),context.box());
    }
    private static void state(ServerPlayer player) {
        var session = ACTIVE.get(player.getUUID());
        PacketDistributor.sendToPlayer(player,new LootBoxAutoStatePayload(session == null ? 0 : session.run().remaining()));
    }
    public static void stop(ServerPlayer player) { ACTIVE.remove(player.getUUID()); state(player); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        long tick = event.getServer().overworld().getGameTime();
        for (UUID id : java.util.List.copyOf(ACTIVE.keySet())) {
            var player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null) { ACTIVE.remove(id); LAST.remove(id); continue; }
            var session = ACTIVE.get(id);
            if (!valid(player,session.context())) { stop(player); continue; }
            if (!session.run().ready(tick) || player.getCooldowns().isOnCooldown(session.context().box().getItem())) continue;
            if (!LootBoxService.openAutomatic(player,session.context().hand(),session.run().interval() == LootBoxAutoRun.FAST_INTERVAL)) { stop(player); continue; }
            session.run().opened(tick);
            if (session.run().remaining() == 0) ACTIVE.remove(id);
            state(player);
        }
        LAST.entrySet().removeIf(entry -> entry.getValue().expires() < tick);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID()); LAST.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); LAST.clear(); }
}
