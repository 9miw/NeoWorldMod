package com.ambition.neoworld.registry;

import com.ambition.neoworld.client.state.ClientEconomyData;
import com.ambition.neoworld.client.state.ClientFashionData;
import com.ambition.neoworld.client.state.ClientLevelData;
import com.ambition.neoworld.client.ClientPartyHudData;
import com.ambition.neoworld.network.PartyHudPayload;
import com.ambition.neoworld.client.gui.fashion.FashionScreenController;
import com.ambition.neoworld.client.gui.dungeon.DungeonBrowserScreenController;
import com.ambition.neoworld.client.gui.hub.MainHubScreenController;
import com.ambition.neoworld.client.gui.party.PartyScreenController;
import com.ambition.neoworld.network.fashion.FashionActionResultPayload;
import com.ambition.neoworld.network.dungeon.DungeonBrowserDataPayload;
import com.ambition.neoworld.network.fashion.FashionClearPayload;
import com.ambition.neoworld.network.fashion.FashionEquipPayload;
import com.ambition.neoworld.network.fashion.FashionNetworkHandler;
import com.ambition.neoworld.network.level.LevelUpCelebrationPayload;
import com.ambition.neoworld.network.fashion.SyncFashionPayload;
import com.ambition.neoworld.network.economy.SyncEconomyPayload;
import com.ambition.neoworld.network.level.SyncLevelPayload;
import com.ambition.neoworld.client.gui.market.MarketScreenController;
import com.ambition.neoworld.network.market.MarketActionResultPayload;
import com.ambition.neoworld.network.market.MarketBuyPayload;
import com.ambition.neoworld.network.market.MarketManagementActionPayload;
import com.ambition.neoworld.network.market.MarketManagementPagePayload;
import com.ambition.neoworld.network.market.MarketManagementQueryPayload;
import com.ambition.neoworld.network.market.MarketNetworkHandler;
import com.ambition.neoworld.network.market.MarketPagePayload;
import com.ambition.neoworld.network.market.MarketQueryPayload;
import com.ambition.neoworld.network.market.MarketSellOpenPayload;
import com.ambition.neoworld.network.market.MarketSellSubmitPayload;
import com.ambition.neoworld.network.fashion.OpenFashionPayload;
import com.ambition.neoworld.network.dungeon.OpenDungeonBrowserPayload;
import com.ambition.neoworld.network.hub.OpenMainHubPayload;
import com.ambition.neoworld.network.market.OpenMarketPayload;
import com.ambition.neoworld.network.party.OpenPartyPayload;
import com.ambition.neoworld.network.party.PartyActionPayload;
import com.ambition.neoworld.network.party.PartyNetworkHandler;
import com.ambition.neoworld.network.party.PartyStatePayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// คลาสลงทะเบียน Network Payloads สำหรับ NeoForge 1.21.1
public final class ModPayloads {
    public static final String PROTOCOL_VERSION = "13";

    private ModPayloads() {
    }

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(com.ambition.neoworld.network.activity.OpenActivityPayload.TYPE,
                com.ambition.neoworld.network.activity.OpenActivityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.ambition.neoworld.client.gui.activity.ActivityScreen.open()));
        registrar.playToClient(com.ambition.neoworld.network.activity.ActivityStatePayload.TYPE,
                com.ambition.neoworld.network.activity.ActivityStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.ambition.neoworld.client.gui.activity.ActivityScreen.receive(payload)));
        registrar.playToServer(com.ambition.neoworld.network.activity.ActivityRequestPayload.TYPE,
                com.ambition.neoworld.network.activity.ActivityRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        com.ambition.neoworld.activity.ActivityService.handle(player, payload);
                }));
        registrar.playToClient(com.ambition.neoworld.network.pvp.PvpMatchOfferPayload.TYPE,
                com.ambition.neoworld.network.pvp.PvpMatchOfferPayload.STREAM_CODEC,
                (payload,context) -> context.enqueueWork(() -> com.ambition.neoworld.client.gui.pvp.PvpMatchScreenController.receive(payload)));
        registrar.playToServer(com.ambition.neoworld.network.pvp.PvpMatchConfirmPayload.TYPE,
                com.ambition.neoworld.network.pvp.PvpMatchConfirmPayload.STREAM_CODEC,
                (payload,context) -> context.enqueueWork(() -> {
                    if(context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        com.ambition.neoworld.pvp.room.PvpRoomManager.get(player.getServer()).confirm(player,payload.offer());
                }));
        registrar.playToClient(com.ambition.neoworld.network.pvp.PvpQueueStatePayload.TYPE,
                com.ambition.neoworld.network.pvp.PvpQueueStatePayload.STREAM_CODEC,
                (payload,context) -> context.enqueueWork(() -> com.ambition.neoworld.client.state.ClientPvpQueueData.update(payload)));
        registrar.playToServer(com.ambition.neoworld.network.pvp.PvpQueueCancelPayload.TYPE,
                com.ambition.neoworld.network.pvp.PvpQueueCancelPayload.STREAM_CODEC,
                (payload,context) -> context.enqueueWork(() -> {
                    if(context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        var manager=com.ambition.neoworld.pvp.room.PvpRoomManager.get(player.getServer());
                        if(manager.queued(player.getUUID())) manager.unqueue(player);
                        else manager.sendQueueState(player);
                    }
                }));
        registrar.playToClient(com.ambition.neoworld.network.pvp.OpenPvpPayload.TYPE,
                com.ambition.neoworld.network.pvp.OpenPvpPayload.STREAM_CODEC,
                (payload,context) -> context.enqueueWork(com.ambition.neoworld.client.gui.pvp.PvpScreenController::open));
        registrar.playToClient(com.ambition.neoworld.network.pvp.PvpGuiDataPayload.TYPE,
                com.ambition.neoworld.network.pvp.PvpGuiDataPayload.STREAM_CODEC,
                (payload,context) -> context.enqueueWork(() -> com.ambition.neoworld.client.gui.pvp.PvpScreenController.receive(payload)));
        registrar.playToServer(com.ambition.neoworld.network.pvp.PvpGuiRequestPayload.TYPE,
                com.ambition.neoworld.network.pvp.PvpGuiRequestPayload.STREAM_CODEC,
                (payload,context) -> context.enqueueWork(() -> {
                    if(context.player() instanceof net.minecraft.server.level.ServerPlayer player)
                        com.ambition.neoworld.network.pvp.PvpGuiNetworkHandler.handle(player,payload);
                }));
        registrar.playToClient(com.ambition.neoworld.network.dungeon.DungeonDeathStatePayload.TYPE,
                com.ambition.neoworld.network.dungeon.DungeonDeathStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.ambition.neoworld.client.gui.integration.DungeonDeathGui.receive(payload.dungeonDeath())));
        registrar.playToServer(com.ambition.neoworld.network.dungeon.DungeonDeathQueryPayload.TYPE,
                com.ambition.neoworld.network.dungeon.DungeonDeathQueryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                                new com.ambition.neoworld.network.dungeon.DungeonDeathStatePayload(
                                        com.ambition.neoworld.dungeon.room.DungeonRoomEventHandler.isDungeonDeath(player)));
                    }
                }));
        registrar.playToServer(com.ambition.neoworld.network.dungeon.DungeonDeathExitPayload.TYPE,
                com.ambition.neoworld.network.dungeon.DungeonDeathExitPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        com.ambition.neoworld.dungeon.room.DungeonRoomEventHandler.requestDeathExit(player);
                    }
                }));
        registrar.playToClient(com.ambition.neoworld.network.title.OpenTitleGuiPayload.TYPE,
                com.ambition.neoworld.network.title.OpenTitleGuiPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.ambition.neoworld.client.gui.title.TitleScreenController.open()));
        registrar.playToClient(com.ambition.neoworld.network.title.TitleGuiPagePayload.TYPE,
                com.ambition.neoworld.network.title.TitleGuiPagePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.ambition.neoworld.client.gui.title.TitleScreenController.receive(payload)));
        registrar.playToServer(com.ambition.neoworld.network.title.TitleGuiRequestPayload.TYPE,
                com.ambition.neoworld.network.title.TitleGuiRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.ambition.neoworld.network.title.TitleGuiNetworkHandler.handle(payload, context)));
        registrar.playToClient(com.ambition.neoworld.network.PlayerProfilePayload.TYPE,
                com.ambition.neoworld.network.PlayerProfilePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.ambition.neoworld.client.gui.player.PlayerProfileScreen.receive(payload)));
        registrar.playToServer(com.ambition.neoworld.network.player.PlayerActionPayload.TYPE,
                com.ambition.neoworld.network.player.PlayerActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.ambition.neoworld.network.player.PlayerActionNetworkHandler.handle(payload, context)));
        registrar.playToClient(com.ambition.neoworld.network.player.PlayerActionResultPayload.TYPE,
                com.ambition.neoworld.network.player.PlayerActionResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.ambition.neoworld.client.gui.player.PlayerActionScreen.receive(payload)));
        registrar.playToClient(com.ambition.neoworld.network.TitleStatePayload.TYPE,
                com.ambition.neoworld.network.TitleStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.ambition.neoworld.client.ClientTitleData.update(payload)));
        registrar.playToServer(com.ambition.neoworld.network.TradeActionPayload.TYPE,
                com.ambition.neoworld.network.TradeActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        com.ambition.neoworld.trade.service.TradeManager.get(player.getServer()).action(player, payload);
                    }
                }));
        registrar.playToClient(com.ambition.neoworld.network.TradeStatePayload.TYPE,
                com.ambition.neoworld.network.TradeStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        com.ambition.neoworld.client.gui.trade.TradeScreenController.receive(payload)));
        registrar.playToClient(com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload.TYPE,
                com.ambition.neoworld.network.dungeon.DungeonRoomViewPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> com.ambition.neoworld.client.gui.dungeon.DungeonRoomScreen.receive(payload)));
        registrar.playToServer(com.ambition.neoworld.network.dungeon.DungeonRoomQueryPayload.TYPE,
                com.ambition.neoworld.network.dungeon.DungeonRoomQueryPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                        com.ambition.neoworld.network.dungeon.DungeonRoomNetworkHandler.send(player, false);
                    }
                }));
        registrar.playToClient(PartyHudPayload.TYPE, PartyHudPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPartyHudData.update(payload)));

        // ซิงค์เลเวลและ EXP จาก Server ไป Client
        registrar.playToClient(
                SyncLevelPayload.TYPE,
                SyncLevelPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientLevelData.update(payload.level(), payload.exp(), payload.maxExp()))
        );

        // ส่งสัญญาณเฉลิมฉลองการเลเวลอัปจาก Server ไป Client
        registrar.playToClient(
                LevelUpCelebrationPayload.TYPE,
                LevelUpCelebrationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientLevelData.onLevelUpCelebration(payload.newLevel()))
        );

        // ซิงค์ข้อมูลยอดเงินระบบ Economy (Silver, Gold, Diamond) จาก Server ไป Client
        registrar.playToClient(
                SyncEconomyPayload.TYPE,
                SyncEconomyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientEconomyData.update(payload.silver(), payload.gold(), payload.diamond()))
        );

        registrar.playToClient(
                SyncFashionPayload.TYPE,
                SyncFashionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    ClientFashionData.update(payload.playerId(), payload.slots());
                    if (payload.includesWardrobe()) ClientFashionData.updateWardrobe(payload.wardrobe());
                })
        );

        registrar.playToClient(OpenMarketPayload.TYPE, OpenMarketPayload.STREAM_CODEC,
                (payload, context) -> MarketScreenController.open());
        registrar.playToClient(OpenDungeonBrowserPayload.TYPE, OpenDungeonBrowserPayload.STREAM_CODEC,
                (payload, context) -> DungeonBrowserScreenController.open());
        registrar.playToClient(DungeonBrowserDataPayload.TYPE, DungeonBrowserDataPayload.STREAM_CODEC,
                (payload, context) -> DungeonBrowserScreenController.receiveData(payload));
        registrar.playToClient(OpenMainHubPayload.TYPE, OpenMainHubPayload.STREAM_CODEC,
                (payload, context) -> MainHubScreenController.open());
        registrar.playToClient(OpenFashionPayload.TYPE, OpenFashionPayload.STREAM_CODEC,
                (payload, context) -> FashionScreenController.open());
        registrar.playToClient(OpenPartyPayload.TYPE, OpenPartyPayload.STREAM_CODEC,
                (payload, context) -> PartyScreenController.open());
        registrar.playToClient(FashionActionResultPayload.TYPE, FashionActionResultPayload.STREAM_CODEC,
                (payload, context) -> FashionScreenController.receiveAction(payload));
        registrar.playToClient(MarketPagePayload.TYPE, MarketPagePayload.STREAM_CODEC,
                (payload, context) -> MarketScreenController.receivePage(payload));
        registrar.playToClient(MarketActionResultPayload.TYPE, MarketActionResultPayload.STREAM_CODEC,
                (payload, context) -> MarketScreenController.receiveAction(payload));
        registrar.playToClient(MarketManagementPagePayload.TYPE, MarketManagementPagePayload.STREAM_CODEC,
                (payload, context) -> MarketScreenController.receiveManagementPage(payload));
        registrar.playToClient(PartyStatePayload.TYPE, PartyStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> PartyScreenController.receiveState(payload)));
        registrar.playToServer(MarketQueryPayload.TYPE, MarketQueryPayload.STREAM_CODEC,
                MarketNetworkHandler::handleQuery);
        registrar.playToServer(MarketBuyPayload.TYPE, MarketBuyPayload.STREAM_CODEC,
                MarketNetworkHandler::handleBuy);
        registrar.playToServer(MarketManagementQueryPayload.TYPE, MarketManagementQueryPayload.STREAM_CODEC,
                MarketNetworkHandler::handleManagementQuery);
        registrar.playToServer(MarketManagementActionPayload.TYPE, MarketManagementActionPayload.STREAM_CODEC,
                MarketNetworkHandler::handleManagementAction);
        registrar.playToServer(MarketSellOpenPayload.TYPE, MarketSellOpenPayload.STREAM_CODEC,
                MarketNetworkHandler::handleSellOpen);
        registrar.playToServer(MarketSellSubmitPayload.TYPE, MarketSellSubmitPayload.STREAM_CODEC,
                MarketNetworkHandler::handleSellSubmit);
        registrar.playToServer(FashionEquipPayload.TYPE, FashionEquipPayload.STREAM_CODEC,
                FashionNetworkHandler::handleEquip);
        registrar.playToServer(FashionClearPayload.TYPE, FashionClearPayload.STREAM_CODEC,
                FashionNetworkHandler::handleClear);
        registrar.playToServer(PartyActionPayload.TYPE, PartyActionPayload.STREAM_CODEC,
                PartyNetworkHandler::handleAction);

    }

}
