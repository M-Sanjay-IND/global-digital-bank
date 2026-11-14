package com.gdb.repository;

import com.gdb.db.ConnectionProvider;
import com.gdb.db.HikariConnectionProvider;
import com.gdb.db.JdbcConnectionProvider;
import com.gdb.db.SchemaInitializer;
import com.gdb.file.BackupManager;
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

    /** CSV account store from {@code persistence.file.*} settings (backups.keep=0 disables backups). */
    public static AccountRepository createFileAccountRepository(Properties props) {
        return new FileAccountRepository(filePath(props, "persistence.file.accounts", "accounts.csv"), backupManager(props));
    }

    /** CSV transaction store from {@code persistence.file.*} settings. */
    public static TransactionRepository createFileTransactionRepository(Properties props) {
        return new FileTransactionRepository(filePath(props, "persistence.file.transactions", "transactions.csv"), backupManager(props));
    }

    private static java.nio.file.Path filePath(Properties props, String key, String defaultName) {
        String dir = props.getProperty("persistence.file.dir", "data").trim();
        return java.nio.file.Path.of(dir, props.getProperty(key, defaultName).trim());
    }

    private static BackupManager backupManager(Properties props) {
        int keep = intProperty(props, "persistence.file.backups.keep", 5);
        return keep <= 0 ? null : new BackupManager(keep);
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
            } else if ("file".equals(mode)) {
                accountRepositoryInstance = createFileAccountRepository(loadPersistenceProperties());
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
            } else if ("file".equals(mode)) {
                transactionRepositoryInstance = createFileTransactionRepository(loadPersistenceProperties());
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
