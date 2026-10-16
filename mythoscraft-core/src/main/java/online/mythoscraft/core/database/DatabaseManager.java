package online.mythoscraft.core.database;

import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All SQL runs on one dedicated thread. That keeps the main thread free, serialises writes (no lock contention
 * with SQLite) and guarantees that operations are applied in the order they were submitted – which the player
 * data cache relies on.
 */
public final class DatabaseManager {

    private final Logger logger;
    private final ExecutorService executor;
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicLong failures = new AtomicLong();
    private ConnectionProvider provider;
    private volatile boolean ready;
    private volatile String lastError;

    public DatabaseManager(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MythosCraft-Database");
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<Void> start(ConnectionProvider provider) {
        this.provider = provider;
        return query("initialize schema", c -> {
            Schema.migrate(c, provider.dialect(), logger);
            return null;
        }).thenRun(() -> {
            ready = true;
            logger.info("Database initialized (" + provider.describe() + ")");
        });
    }

    public <T> CompletableFuture<T> query(String operation, SqlFunction<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        pending.incrementAndGet();
        try {
            executor.execute(() -> {
                try {
                    future.complete(execute(work));
                } catch (Throwable t) {
                    failures.incrementAndGet();
                    lastError = operation + ": " + t.getMessage();
                    logger.log(Level.SEVERE, "Database operation failed (" + operation + "): " + t.getMessage(), t);
                    future.completeExceptionally(t);
                } finally {
                    pending.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            pending.decrementAndGet();
            future.completeExceptionally(e);
        }
        return future;
    }

    /** Runs the work inside a transaction; rolls back on any error. */
    public CompletableFuture<Void> transaction(String operation, SqlConsumer work) {
        return query(operation, c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                work.accept(c);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
            return null;
        });
    }

    /** Blocks until the work is done. Only allowed off the main thread (e.g. AsyncPlayerPreLoginEvent). */
    public <T> T await(CompletableFuture<T> future, long timeoutSeconds) throws SQLException {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Blocking database access on the main thread");
        }
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for the database", e);
        } catch (ExecutionException e) {
            throw new SQLException(e.getCause() == null ? e.getMessage() : e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException e) {
            throw new SQLException("Database did not answer within " + timeoutSeconds + "s", e);
        }
    }

    private <T> T execute(SqlFunction<T> work) throws SQLException {
        try {
            return work.apply(provider.connection());
        } catch (SQLException first) {
            if (!isConnectionProblem(first)) {
                throw first;
            }
            logger.warning("Database connection lost (" + first.getMessage() + ") – reconnecting and retrying once.");
            provider.reset();
            return work.apply(provider.connection());
        }
    }

    private static boolean isConnectionProblem(SQLException e) {
        String state = e.getSQLState();
        return (state != null && state.startsWith("08"))
                || (e.getMessage() != null && e.getMessage().toLowerCase().contains("closed"));
    }

    public SqlDialect dialect() {
        return provider == null ? SqlDialect.SQLITE : provider.dialect();
    }

    public boolean isReady() {
        return ready;
    }

    public int pending() {
        return pending.get();
    }

    public long failures() {
        return failures.get();
    }

    public String lastError() {
        return lastError;
    }

    public String describe() {
        return provider == null ? "-" : provider.describe();
    }

    /** Waits for queued work (e.g. the final save) and closes the connection. */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                logger.severe("Database did not finish pending work within 20 seconds (" + pending.get() + " left).");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (provider != null) {
            provider.close();
        }
        ready = false;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }
}
