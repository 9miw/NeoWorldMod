package com.ambition.neoworld.registry;

import com.ambition.neoworld.client.ClientEconomyData;
import com.ambition.neoworld.client.ClientFashionData;
import com.ambition.neoworld.client.ClientLevelData;
import com.ambition.neoworld.client.gui.fashion.FashionScreenController;
import com.ambition.neoworld.client.gui.dungeon.DungeonBrowserScreenController;
import com.ambition.neoworld.client.gui.hub.MainHubScreenController;
import com.ambition.neoworld.client.gui.party.PartyScreenController;
import com.ambition.neoworld.network.FashionActionResultPayload;
import com.ambition.neoworld.network.DungeonBrowserDataPayload;
import com.ambition.neoworld.network.FashionClearPayload;
import com.ambition.neoworld.network.FashionEquipPayload;
import com.ambition.neoworld.network.FashionNetworkHandler;
import com.ambition.neoworld.network.LevelUpCelebrationPayload;
import com.ambition.neoworld.network.SyncFashionPayload;
import com.ambition.neoworld.network.SyncEconomyPayload;
import com.ambition.neoworld.network.SyncLevelPayload;
import com.ambition.neoworld.client.gui.market.MarketScreenController;
import com.ambition.neoworld.network.MarketActionResultPayload;
import com.ambition.neoworld.network.MarketBuyPayload;
import com.ambition.neoworld.network.MarketNetworkHandler;
import com.ambition.neoworld.network.MarketPagePayload;
import com.ambition.neoworld.network.MarketQueryPayload;
import com.ambition.neoworld.network.OpenFashionPayload;
import com.ambition.neoworld.network.OpenDungeonBrowserPayload;
import com.ambition.neoworld.network.OpenMainHubPayload;
import com.ambition.neoworld.network.OpenMarketPayload;
import com.ambition.neoworld.network.OpenPartyPayload;
import com.ambition.neoworld.network.PartyActionPayload;
import com.ambition.neoworld.network.PartyNetworkHandler;
import com.ambition.neoworld.network.PartyStatePayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// คลาสลงทะเบียน Network Payloads สำหรับ NeoForge 1.21.1
public final class ModPayloads {
    public static final String PROTOCOL_VERSION = "2";

    private ModPayloads() {
    }

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

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
                (payload, context) -> context.enqueueWork(() ->
                        ClientFashionData.update(payload.playerId(), payload.slots()))
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
        registrar.playToClient(PartyStatePayload.TYPE, PartyStatePayload.STREAM_CODEC,
                (payload, context) -> PartyScreenController.receiveState(payload));
        registrar.playToServer(MarketQueryPayload.TYPE, MarketQueryPayload.STREAM_CODEC,
                MarketNetworkHandler::handleQuery);
        registrar.playToServer(MarketBuyPayload.TYPE, MarketBuyPayload.STREAM_CODEC,
                MarketNetworkHandler::handleBuy);
        registrar.playToServer(FashionEquipPayload.TYPE, FashionEquipPayload.STREAM_CODEC,
                FashionNetworkHandler::handleEquip);
        registrar.playToServer(FashionClearPayload.TYPE, FashionClearPayload.STREAM_CODEC,
                FashionNetworkHandler::handleClear);
        registrar.playToServer(PartyActionPayload.TYPE, PartyActionPayload.STREAM_CODEC,
                PartyNetworkHandler::handleAction);

    }

}
