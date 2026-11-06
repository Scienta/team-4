package no.scienta.cfp;

import java.sql.SQLException;
import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a row lock ({@code SELECT … FOR UPDATE}) with a bounded wait. A waiting request holds one of the five pooled
 * connections, so an unbounded wait behind one stuck holder could starve the whole app. Past {@code cfp.lock-timeout}
 * Postgres gives up with SQLSTATE 55P03; this turns that into a {@link CannotAcquireLockException}, which
 * {@link ApiErrors} answers with 503 and {@code Retry-After} (docs/adr/0001-talk-row-lock.md).
 */
@Component
public class RowLocks {

    /** Postgres {@code lock_not_available}. Spring 7 leaves it an {@code UncategorizedSQLException}. */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private final JdbcTemplate jdbc;
    private final String timeoutMillis;

    public RowLocks(JdbcTemplate jdbc, @Value("${cfp.lock-timeout:5s}") Duration timeout) {
        this.jdbc = jdbc;
        this.timeoutMillis = Long.toString(timeout.toMillis());
    }

    /**
     * Sets {@code lock_timeout} for the rest of the current transaction only ({@code set_config(…, true)} is
     * {@code SET LOCAL}, so it never leaks to the next user of the pooled connection), then runs the locking query.
     * Outside a transaction both the lock and the timeout would end with the statement, so that is refused.
     */
    public <T> T forUpdate(Supplier<T> lockingQuery) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("a row lock needs a transaction: it would end with the statement");
        }
        jdbc.queryForObject("SELECT set_config('lock_timeout', ?, true)", String.class, timeoutMillis);
        try {
            return lockingQuery.get();
        } catch (DataAccessException e) {
            if (e.getMostSpecificCause() instanceof SQLException sql && LOCK_NOT_AVAILABLE.equals(sql.getSQLState())) {
                throw new CannotAcquireLockException("row lock not granted within " + timeoutMillis + " ms", e);
            }
            throw e;
        }
    }
}
