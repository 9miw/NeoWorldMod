package com.ambition.neoworld.network.player;

import com.ambition.neoworld.network.party.PartyNetworkHandler;

import java.util.Map;
import java.util.WeakHashMap;
import com.ambition.neoworld.permission.ModPermissions;
import com.ambition.neoworld.trade.service.TradeManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class PlayerActionNetworkHandler {
    private static final Map<ServerPlayer, Integer> LAST_ACTION = new WeakHashMap<>();
    private PlayerActionNetworkHandler() {}

    public static void handle(PlayerActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer actor)) return;
        int tick = actor.getServer().getTickCount();
        Integer previous = LAST_ACTION.get(actor);
        if (previous != null && tick - previous < 5) {
            reply(context, payload, false, "กรุณารอสักครู่"); return;
        }
        LAST_ACTION.put(actor, tick);
        ServerPlayer target = actor.getServer().getPlayerList().getPlayer(payload.targetId());
        if (target == null || (target == actor && payload.action() != PlayerActionPayload.Action.PROFILE) || !actor.isAlive() || !target.isAlive()
                || actor.isSpectator() || target.isSpectator()
                || actor.serverLevel() != target.serverLevel()) {
            reply(context, payload, false, "ผู้เล่นไม่พร้อมใช้งาน"); return;
        }
        if (payload.action() != PlayerActionPayload.Action.TRADE && actor.distanceToSqr(target) > 64) {
            reply(context, payload, false, "ผู้เล่นอยู่ห่างเกิน 8 บล็อก"); return;
        }
        switch (payload.action()) {
            case PROFILE -> context.reply(PlayerProfileSnapshot.capture(payload, target));
            case TRADE -> {
                String error = TradeManager.get(actor.getServer()).request(actor, target);
                reply(context, payload, error == null || error.isEmpty(), error == null ? "" : error);
            }
            case INVITE -> {
                if (!ModPermissions.hasPermission(actor.createCommandSourceStack(), ModPermissions.COMMAND_DUNGEON_USE, 0)) {
                    reply(context, payload, false, "ไม่มีสิทธิ์ใช้งานปาร์ตี้"); return;
                }
                var result = PartyNetworkHandler.inviteTarget(actor, target);
                reply(context, payload, result.successful(), result.message());
            }
        }
    }
    private static void reply(IPayloadContext context, PlayerActionPayload request, boolean success, String message) {
        context.reply(new PlayerActionResultPayload(request.requestId(), request.targetId(), success, message));
    }
}
