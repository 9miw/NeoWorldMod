package com.ambition.neoworld.market.storage;

import com.ambition.neoworld.database.NeoWorldDatabase;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

/** Market-only transaction scope. Repository try-with-resources borrows, never closes, the outer connection. */
public final class MarketSql {
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();
    private MarketSql() {}

    public static Connection connection() throws SQLException {
        Connection current = CURRENT.get();
        if (current == null) return NeoWorldDatabase.getConnection();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) return null;
                    if (method.getName().equals("commit") || method.getName().equals("rollback")
                            || method.getName().equals("setAutoCommit"))
                        throw new SQLException("Only the outer market transaction owns commit/rollback");
                    try { return method.invoke(current, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
    }

    public static <T> T transaction(NeoWorldDatabase.SqlTransaction<T> work) throws SQLException {
        return atomic(NeoWorldDatabase::getConnection, work);
    }

    public static <T> T atomic(Connections connections, NeoWorldDatabase.SqlTransaction<T> work) throws SQLException {
        if (CURRENT.get() != null) {
            try { return work.execute(CURRENT.get()); }
            catch (SQLException e) { throw e; }
            catch (Exception e) { throw new SQLException("Nested market transaction failed", e); }
        }
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            CURRENT.set(c);
            try {
                T result = work.execute(c);
                c.commit();
                return result;
            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                if (e instanceof SQLException sql) throw sql;
                throw new SQLException("Market transaction failed", e);
            } finally { CURRENT.remove(); }
        }
    }
}
