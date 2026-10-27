package com.orodent.ecos.database;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

final class ConnectionFailures {
    private ConnectionFailures() { }

    static boolean isConnectionLoss(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        return matches(failure, seen);
    }

    private static boolean matches(Throwable failure, Set<Throwable> seen) {
        if (failure == null || !seen.add(failure)) return false;
        if (failure instanceof SQLException sql) {
            String state = sql.getSQLState();
            if (state != null && (state.startsWith("08") || state.equals(DatabaseIdentity.MISMATCH))) return true;
            if (matches(sql.getNextException(), seen)) return true;
        }
        return matches(failure.getCause(), seen);
    }
}
