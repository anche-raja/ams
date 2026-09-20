package org.example.am.shared.dao.scheduling;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * Runs a scheduling operation so that anything other than {@code OK} leaves the database untouched.
 *
 * <p>This is the {@code SAVEPOINT} / {@code ROLLBACK TO} pair every one of the nine procedures
 * opened with. It is a savepoint rather than a transaction because the caller owns the transaction:
 * these operations run inside a Spring {@code @Transactional} service and must remain abandonable
 * as one unit, which is what lets {@code RescheduleNcrAction} cancel a date and take a new one with
 * the option of discarding both.</p>
 *
 * <p>The savepoint is taken on the connection bound to the current transaction, rather than through
 * {@code TransactionAspectSupport.currentTransactionStatus()}. The latter only sees a transaction
 * started by the transaction <em>aspect</em>, so it throws under a test-managed transaction - and
 * the tests for this logic are exactly where it has to work.</p>
 *
 * <p>Stronger than the original, deliberately. Most early returns in the PL/SQL simply returned
 * without rolling back, and were safe only because nothing had been written yet - a property the
 * reader had to re-establish for each branch. Rolling back on every non-{@code OK} status makes the
 * package header's promise ("a status other than OK reliably means I did nothing") true by
 * construction.</p>
 */
final class SavepointScope {

    private SavepointScope() {
        super();
    }

    /**
     * @param dataSource the transaction's data source
     * @param operation  the work to attempt
     * @return the operation's own result, or {@link SchedulingStatus#BUSY} when a lock wait expired
     */
    static SchedulingResult run(final DataSource dataSource,
            final Supplier<SchedulingResult> operation) {
        final Connection connection = DataSourceUtils.getConnection(dataSource);
        Savepoint savepoint = null;
        try {
            // Outside a transaction there is nothing to roll back to and nothing to protect: the
            // caller is in autocommit, so each statement stands on its own anyway.
            if (!connection.getAutoCommit()) {
                savepoint = connection.setSavepoint("ams_scheduling");
            }

            final SchedulingResult result = operation.get();
            if (!result.isOk() && savepoint != null) {
                connection.rollback(savepoint);
            }
            return result;
        } catch (final SQLException failure) {
            rollback(connection, savepoint);
            throw new IllegalStateException("Scheduling operation failed", failure);
        } catch (final RuntimeException failure) {
            rollback(connection, savepoint);
            if (LockTimeouts.isLockTimeout(failure)) {
                // An ordinary business outcome, not a fault: someone else is mid-booking.
                return new SchedulingResult(SchedulingStatus.BUSY);
            }
            throw failure;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static void rollback(final Connection connection, final Savepoint savepoint) {
        if (savepoint == null) {
            return;
        }
        try {
            connection.rollback(savepoint);
        } catch (final SQLException ignored) {
            // The original failure is the one worth reporting; a failed rollback here means the
            // transaction is already doomed and the caller will see that instead.
        }
    }
}
