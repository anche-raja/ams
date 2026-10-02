package org.example.am.shared.dao.scheduling;

import java.sql.SQLException;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;

/**
 * Recognises "someone else is holding that row".
 *
 * <p>The PL/SQL wrote {@code SELECT ... FOR UPDATE WAIT 5} and caught Oracle's two lock errors,
 * ORA-00054 and ORA-30006, mapping both to {@link SchedulingStatus#BUSY}. H2 has no {@code WAIT}
 * clause, so the five-second bound moves to the connection URL as {@code LOCK_TIMEOUT=5000} and
 * arrives here as a single error code.</p>
 *
 * <p>The bound matters and is not arbitrary. The connection pool's wait timeout ({@code maxWaitMillis}
 * on the Tomcat data source) bounds how long a caller waits for a connection from the <em>pool</em>,
 * not how long a statement waits for a lock, and a session blocked on a lock is still holding its
 * connection. Without a statement-level bound, row contention turns into pool exhaustion across
 * every request. Five seconds keeps it an order of magnitude clear.</p>
 */
final class LockTimeouts {

    /** H2's {@code LOCK_TIMEOUT_1}. The equivalent of ORA-00054. */
    private static final int H2_LOCK_TIMEOUT = 50200;

    private LockTimeouts() {
        super();
    }

    /**
     * @return {@code true} when this failure is a lock wait expiring rather than a real fault
     */
    static boolean isLockTimeout(final Throwable failure) {
        // Spring's sql-error-codes.xml lists 50200 under H2's cannotAcquireLockCodes, so a
        // translated exception is the usual shape - but the raw SQLException is checked too,
        // because translation only happens on the paths that go through JdbcTemplate.
        if (failure instanceof CannotAcquireLockException
                || failure instanceof PessimisticLockingFailureException) {
            return true;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException
                    && ((SQLException) cause).getErrorCode() == H2_LOCK_TIMEOUT) {
                return true;
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return false;
    }
}
