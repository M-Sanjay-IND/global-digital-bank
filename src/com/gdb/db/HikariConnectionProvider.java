package com.gdb.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link ConnectionProvider} backed by a HikariCP pool. Connections handed out are proxies:
 * calling {@code close()} returns them to the pool instead of closing the database connection.
 */
public class HikariConnectionProvider implements ConnectionProvider {

    /** Pool tuning knobs; times are in milliseconds. */
    public record Settings(int maxPoolSize, int minIdle, long connectionTimeoutMs,
                           long idleTimeoutMs, long maxLifetimeMs, long busyTimeoutMs) {
        public static Settings defaults() {
            return new Settings(10, 2, 30_000, 600_000, 1_800_000, 5_000);
        }
    }

    private final String url;
    private final HikariDataSource dataSource;

    public HikariConnectionProvider(String url, String driver, Settings settings) {
        this.url = url;
        HikariConfig config = new HikariConfig();
        config.setPoolName("gdb-pool");
        config.setJdbcUrl(url);
        config.setDriverClassName(driver);
        config.setMaximumPoolSize(settings.maxPoolSize());
        config.setMinimumIdle(Math.min(settings.minIdle(), settings.maxPoolSize()));
        config.setConnectionTimeout(settings.connectionTimeoutMs());
        config.setIdleTimeout(settings.idleTimeoutMs());
        config.setMaxLifetime(settings.maxLifetimeMs());
        // SQLite allows one writer at a time; wait for the lock instead of failing with SQLITE_BUSY.
        config.addDataSourceProperty("busy_timeout", String.valueOf(settings.busyTimeoutMs()));
        this.dataSource = new HikariDataSource(config);
    }

    public HikariConnectionProvider(String url, String driver) {
        this(url, driver, Settings.defaults());
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** Closes the pool and every pooled connection. Safe to call more than once. */
    @Override
    public void shutdown() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public String getProviderName() {
        return "HikariConnectionProvider [" + url + ", maxPoolSize=" + dataSource.getMaximumPoolSize() + "]";
    }

    public boolean isClosed() {
        return dataSource.isClosed();
    }

    public int getActiveConnections() {
        return dataSource.getHikariPoolMXBean() == null ? 0 : dataSource.getHikariPoolMXBean().getActiveConnections();
    }

    public int getIdleConnections() {
        return dataSource.getHikariPoolMXBean() == null ? 0 : dataSource.getHikariPoolMXBean().getIdleConnections();
    }

    public int getTotalConnections() {
        return dataSource.getHikariPoolMXBean() == null ? 0 : dataSource.getHikariPoolMXBean().getTotalConnections();
    }
}
