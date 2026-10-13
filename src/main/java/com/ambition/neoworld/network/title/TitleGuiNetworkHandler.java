package com.ambition.neoworld.network.title;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import com.ambition.neoworld.title.TitleRegistry;
import com.ambition.neoworld.title.TitlePlayerState;
import com.ambition.neoworld.title.service.TitleService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class TitleGuiNetworkHandler {
    private static final Map<ServerPlayer, Boolean> IN_FLIGHT = new WeakHashMap<>();
    private TitleGuiNetworkHandler() {}
    public static void handle(TitleGuiRequestPayload request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!TitleService.isRunning() || IN_FLIGHT.containsKey(player)) {
            PacketDistributor.sendToPlayer(player, TitleGuiPagePayload.failure(request.requestId(), "ระบบกำลังทำงาน กรุณาลองใหม่"));
            return;
        }
        var service = TitleService.get();
        service.load(player);
        IN_FLIGHT.put(player, true);
        CompletableFuture<TitlePlayerState> action = switch (request.action()) {
            case QUERY -> service.inspect(player.getUUID());
            case EQUIP -> service.equip(player.getUUID(), request.titleId());
            case CLEAR -> service.equip(player.getUUID(), "");
            case SETTINGS -> service.settings(player.getUUID(), request.showTitle(), request.showEffects());
        };
        action.whenComplete((state, error) -> {
            IN_FLIGHT.remove(player);
            if (player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
            var response = error == null ? TitleGuiPages.build(request, state, TitleRegistry.all())
                    : TitleGuiPagePayload.failure(request.requestId(), "ทำรายการไม่สำเร็จ ฉายาอาจยังไม่ปลดล็อกหรือฐานข้อมูลไม่พร้อม");
            PacketDistributor.sendToPlayer(player, response);
        });
    }
}
