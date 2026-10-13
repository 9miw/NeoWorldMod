package com.ambition.neoworld.client;

import java.util.List;

import com.ambition.neoworld.network.PartyHudPayload;

public final class ClientPartyHudData {
    private static List<PartyHudPayload.Member> members = List.of();

    private ClientPartyHudData() {}

    public static List<PartyHudPayload.Member> members() {
        return members;
    }

    public static void update(PartyHudPayload payload) {
        members = payload.members();
    }

    public static void reset() {
        members = List.of();
    }
}
