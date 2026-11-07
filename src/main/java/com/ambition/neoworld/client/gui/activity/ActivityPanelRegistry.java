package com.ambition.neoworld.client.gui.activity;

import java.util.List;

/** Add new panel classes here; navigation derives its size from this list. */
public final class ActivityPanelRegistry {
    private ActivityPanelRegistry() {}
    public static List<ActivityPanel> panels() {
        return List.of(new DailyActivityPanel(), new MonthlyActivityPanel(), new OnlineActivityPanel(), new PendingActivityPanel());
    }
}
