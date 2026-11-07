package com.ambition.neoworld.economy.analytics;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/** Serializable telemetry commands; never capture players, connections or executable closures. */
public record AnalyticsWork(String id, Kind kind, EconomyAnalyticsRepository.Event event,
        EconomyAnalyticsRepository.Balance balance, String day) {
    public enum Kind { EVENT, BALANCE, CARRY_DAILY }

    public AnalyticsWork { id = UUID.fromString(id).toString(); }

    public static AnalyticsWork event(EconomyAnalyticsRepository.Event event) {
        return new AnalyticsWork(event.id(), Kind.EVENT, event, null, null);
    }
    public static AnalyticsWork balance(EconomyAnalyticsRepository.Balance balance, String day) {
        return new AnalyticsWork(UUID.randomUUID().toString(), Kind.BALANCE, null, balance, day);
    }
    public static AnalyticsWork carryDaily(String day) {
        return new AnalyticsWork(UUID.randomUUID().toString(), Kind.CARRY_DAILY, null, null, day);
    }

    public void validate() {
        UUID.fromString(id);
        if (kind == null) throw new IllegalArgumentException("Missing analytics work kind");
        switch (kind) {
            case EVENT -> {
                if (event == null || !id.equals(UUID.fromString(event.id()).toString()) || event.type() == null || event.source() == null
                        || event.reference() == null || event.flow() == null || event.currency() == null || event.detail() == null)
                    throw new IllegalArgumentException("Invalid analytics event");
            }
            case BALANCE -> {
                if (balance == null || balance.player() == null) throw new IllegalArgumentException("Missing balance");
                LocalDate.parse(day);
            }
            case CARRY_DAILY -> LocalDate.parse(day);
        }
    }

    /** Receipt and all effects belong to the caller's SQL transaction. */
    public void apply(Connection c, String prefix) throws SQLException {
        validate();
        if (c.getAutoCommit()) throw new SQLException("Analytics effects and receipt require one SQL transaction");
        if (!prefix.matches("[A-Za-z_][A-Za-z0-9_]{0,31}")) throw new SQLException("Invalid analytics prefix");
        String receipts = prefix + "economy_outbox_receipts";
        try (var s = c.prepareStatement("SELECT job_id FROM " + receipts + " WHERE job_id=?")) {
            s.setString(1, id);
            try (var r = s.executeQuery()) { if (r.next()) return; }
        }
        try (var s = c.prepareStatement("INSERT INTO " + receipts + " (job_id) VALUES (?)")) {
            s.setString(1, id); s.executeUpdate();
        }
        switch (kind) {
            case EVENT -> EconomyAnalyticsRepository.insert(c, prefix + "economy_events", event);
            case BALANCE -> EconomyAnalyticsRepository.balance(c, prefix, balance, day);
            case CARRY_DAILY -> EconomyAnalyticsRepository.carryDaily(c, prefix, day);
        }
    }
}
