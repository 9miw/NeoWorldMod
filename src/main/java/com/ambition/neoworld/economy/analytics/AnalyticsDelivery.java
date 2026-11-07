package com.ambition.neoworld.economy.analytics;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** SQL receipts make replay safe even when commit succeeded but file acknowledgement did not. */
public final class AnalyticsDelivery {
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    private AnalyticsDelivery() {}

    public static int flush(AnalyticsOutbox outbox, Connections connections, String prefix, int limit) throws Exception {
        var batch = outbox.batch(limit);
        if (batch.isEmpty()) return 0;
        commit(batch, connections, prefix);
        outbox.acknowledge(batch);
        return batch.size();
    }

    public static void commit(List<AnalyticsOutbox.Job> batch, Connections connections, String prefix) throws Exception {
        try (var c = connections.open()) {
            c.setAutoCommit(false);
            try {
                for (var job : batch) job.work().apply(c, prefix);
                c.commit();
            } catch (Exception failure) {
                try { c.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }
}
