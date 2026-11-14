package com.gdb.repository;

import com.gdb.db.ConnectionProvider;
import com.gdb.db.HikariConnectionProvider;
import com.gdb.db.JdbcConnectionProvider;
import com.gdb.db.SchemaInitializer;
import java.io.*;
import java.util.Properties;

public class RepositoryFactory {
    private static AccountRepository accountRepositoryInstance;
    private static TransactionRepository transactionRepositoryInstance;
    private static ConnectionProvider connectionProviderInstance;

    public static Properties loadPersistenceProperties() {
        Properties props = new Properties();
        String[] paths = {
            "config/persistence.properties",
            "src/main/resources/config/persistence.properties",
            "bin/config/persistence.properties"
        };

        for (String p : paths) {
            File f = new File(p);
            if (f.exists()) {
                try (InputStream in = new FileInputStream(f)) {
                    props.load(in);
                    return props;
                } catch (IOException ignored) {
                }
            }
        }

        try (InputStream in = RepositoryFactory.class.getResourceAsStream("/config/persistence.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }

        return props;
    }

    public static String getPersistenceMode() {
        return loadPersistenceProperties().getProperty("persistence.mode", "memory").trim().toLowerCase();
    }

    /** Builds (but does not initialise the schema of) a provider from persistence settings: pooled when enabled. */
    public static ConnectionProvider createConnectionProvider(Properties props) {
        String url = props.getProperty("persistence.db.url", "jdbc:sqlite:gdb.db").trim();
        String driver = props.getProperty("persistence.db.driver", "org.sqlite.JDBC").trim();
        boolean pooled = Boolean.parseBoolean(props.getProperty("persistence.pool.enabled", "false").trim());
        if (!pooled) {
            return new JdbcConnectionProvider(url, driver);
        }
        HikariConnectionProvider.Settings d = HikariConnectionProvider.Settings.defaults();
        HikariConnectionProvider.Settings settings = new HikariConnectionProvider.Settings(
            intProperty(props, "persistence.pool.maxSize", d.maxPoolSize()),
            intProperty(props, "persistence.pool.minIdle", d.minIdle()),
            intProperty(props, "persistence.pool.connectionTimeoutMs", (int) d.connectionTimeoutMs()),
            intProperty(props, "persistence.pool.idleTimeoutMs", (int) d.idleTimeoutMs()),
            intProperty(props, "persistence.pool.maxLifetimeMs", (int) d.maxLifetimeMs()),
            intProperty(props, "persistence.pool.busyTimeoutMs", (int) d.busyTimeoutMs()));
        return new HikariConnectionProvider(url, driver, settings);
    }

    private static int intProperty(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for " + key + ": '" + value + "'", e);
        }
    }

    public static synchronized ConnectionProvider getConnectionProvider() {
        if (connectionProviderInstance == null) {
            ConnectionProvider provider = createConnectionProvider(loadPersistenceProperties());
            SchemaInitializer.initialize(provider);
            connectionProviderInstance = provider;
        }
        return connectionProviderInstance;
    }

    public static synchronized AccountRepository getAccountRepository() {
        if (accountRepositoryInstance == null) {
            String mode = getPersistenceMode();
            if ("jdbc".equals(mode)) {
                accountRepositoryInstance = new JdbcAccountRepository(getConnectionProvider());
            } else {
                accountRepositoryInstance = new InMemoryAccountRepository();
            }
        }
        return accountRepositoryInstance;
    }

    public static synchronized TransactionRepository getTransactionRepository() {
        if (transactionRepositoryInstance == null) {
            String mode = getPersistenceMode();
            if ("jdbc".equals(mode)) {
                transactionRepositoryInstance = new JdbcTransactionRepository(getConnectionProvider());
            } else {
                transactionRepositoryInstance = new InMemoryTransactionRepository();
            }
        }
        return transactionRepositoryInstance;
    }

    public static synchronized void reset() {
        if (connectionProviderInstance != null) {
            connectionProviderInstance.shutdown();
            connectionProviderInstance = null;
        }
        accountRepositoryInstance = null;
        transactionRepositoryInstance = null;
    }
}
