package com.ambition.neoworld.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import com.ambition.neoworld.network.TitleStatePayload;

public final class ClientTitleData {
    private static final Map<UUID, TitleStatePayload> TITLES = new HashMap<>();
    private ClientTitleData() {}
    public static void update(TitleStatePayload payload) {
        if (payload.titleId().isEmpty()) TITLES.remove(payload.playerId());
        else TITLES.put(payload.playerId(), payload);
    }
    public static Optional<TitleStatePayload> title(UUID id) { return Optional.ofNullable(TITLES.get(id)); }
    public static void reset() { TITLES.clear(); }
}
