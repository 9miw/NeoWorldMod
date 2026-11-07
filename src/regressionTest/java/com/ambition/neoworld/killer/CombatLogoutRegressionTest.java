package com.ambition.neoworld.killer;

import java.util.UUID;
import com.ambition.neoworld.data.PlayerData;

final class CombatLogoutRegressionTest {
    static void run() {
        var state = new CombatLogoutState<PlayerData>();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var wallet = new PlayerData(1, 0, 1_000, 0, 0);
        check(!state.retain(a, wallet, 0), "Normal logout must not leave a body");
        state.tag(a, b, 100, false);
        check(state.remaining(a, 100) == 1_200 && state.remaining(b, 100) == 1_200, "Both combat participants are tagged");
        check(state.remaining(c, 100) == 0, "Uninvolved player is not tagged");
        check(state.remaining(a, 1_299) == 1 && state.remaining(a, 1_300) == 0, "Combat tag expires at sixty seconds");
        state.tag(a, b, 1_500, false);
        state.tag(a, b, 2_000, false);
        check(state.remaining(b, 3_199) == 1, "New damage refreshes combat timer");
        check(state.retain(a, wallet, 3_199), "Tagged logout retains the real player data");
        check(state.body(a).player() == wallet && state.body(a).expiresAt() == 4_399, "Body lasts sixty seconds after exit, even at end of combat tag");
        state.retain(a, wallet, 3_299);
        state.tag(a, b, 3_399, false);
        check(state.body(a).expiresAt() == 4_399, "Repeated disconnect or hits cannot extend body lifetime");
        wallet.removeSilver(50);
        var body = state.take(a);
        check(body.player().getSilver() == 950 && state.body(a) == null && state.take(a) == null,
                "Reconnect releases one authoritative body with latest wallet, never a stale copy");
        state.tagUntil(a, body.expiresAt());
        check(state.remaining(a, 4_398) == 201, "Reconnect preserves newer combat hits as well as remaining body risk");
        state.clearCombat(a);
        check(state.remaining(a, 4_398) == 0, "Final removal clears combat");
        state.tag(a, b, 5_000, false);
        state.tag(a, b, 5_001, true);
        check(!state.retain(a, wallet, 5_002) && state.remaining(b, 5_002) == 0, "Arena combat cannot cause retained logout");
        state.tag(a, a, 6_000, false);
        check(state.remaining(a, 6_001) == 0, "Self damage never causes combat logout");
        state.tag(a, b, 7_000, false);
        state.retain(a, wallet, 7_001);
        state.clear();
        check(state.bodies().isEmpty() && state.remaining(a, 7_002) == 0, "Shutdown drops all in-memory session state");
        System.out.println("Combat logout state regression checks passed");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
