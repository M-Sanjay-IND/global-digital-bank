package com.gdb.tests;

import com.gdb.db.ConnectionProvider;
import com.gdb.db.HikariConnectionProvider;
import com.gdb.db.JdbcConnectionProvider;
import com.gdb.db.SchemaInitializer;
import com.gdb.domain.AccountFactory;
import com.gdb.exceptions.DataAccessException;
import com.gdb.repository.AccountRepository;
import com.gdb.repository.JdbcAccountRepository;
import com.gdb.repository.RepositoryFactory;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class TestConnectionPooling {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("  ACTIVITY 25 — CONNECTION POOLING (HikariCP)");
        System.out.println("============================================================");

        File dbFile = File.createTempFile("gdb_pool_test", ".db");
        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        try {
            System.out.println("\n[TEST 1] Pool lifecycle:");
            HikariConnectionProvider pool = new HikariConnectionProvider(url, "org.sqlite.JDBC");
            SchemaInitializer.initialize(pool);
            try (Connection c = pool.getConnection(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT 1")) {
                check("connection from pool works", rs.next() && rs.getInt(1) == 1);
            }
            check("provider name describes the pool", pool.getProviderName().startsWith("HikariConnectionProvider"));
            check("pool is open", !pool.isClosed());
            pool.shutdown();
            check("pool closed after shutdown", pool.isClosed());
            try {
                pool.getConnection();
                check("getConnection after shutdown must fail", false);
            } catch (SQLException e) {
                check("getConnection after shutdown fails", true);
            }
            pool.shutdown();
            check("shutdown is idempotent", true);

            System.out.println("\n[TEST 2] Connections are reused, not reopened:");
            HikariConnectionProvider single = new HikariConnectionProvider(url, "org.sqlite.JDBC",
                new HikariConnectionProvider.Settings(1, 1, 2000, 600_000, 1_800_000, 5000));
            Connection first;
            try (Connection c = single.getConnection()) {
                first = c.unwrap(Connection.class);
            }
            check("returned to pool, not closed", single.getIdleConnections() == 1 && single.getActiveConnections() == 0);
            try (Connection c = single.getConnection()) {
                check("same physical connection handed out again", c.unwrap(Connection.class) == first);
                check("one borrowed connection counts as active", single.getActiveConnections() == 1);
            }
            single.shutdown();

            System.out.println("\n[TEST 3] Pool exhaustion times out instead of hanging:");
            HikariConnectionProvider tiny = new HikariConnectionProvider(url, "org.sqlite.JDBC",
                new HikariConnectionProvider.Settings(2, 1, 500, 600_000, 1_800_000, 5000));
            try (Connection a = tiny.getConnection(); Connection b = tiny.getConnection()) {
                check("pool never exceeds its maximum", tiny.getTotalConnections() == 2);
                long t = System.nanoTime();
                try {
                    tiny.getConnection();
                    check("third connection must time out", false);
                } catch (SQLException e) {
                    long ms = (System.nanoTime() - t) / 1_000_000;
                    check("third request fails after ~connectionTimeout (" + ms + " ms)", ms >= 400 && ms < 5000);
                }
            }
            try (Connection again = tiny.getConnection()) {
                check("capacity returns once connections are released", again != null);
            }
            tiny.shutdown();

            System.out.println("\n[TEST 4] Concurrent repository use through the pool:");
            HikariConnectionProvider shared = new HikariConnectionProvider(url, "org.sqlite.JDBC",
                new HikariConnectionProvider.Settings(4, 2, 10_000, 600_000, 1_800_000, 10_000));
            SchemaInitializer.initialize(shared);
            AccountRepository repo = new JdbcAccountRepository(shared);

            ExecutorService exec = Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true); // a failing check must end the run, not leave the JVM hanging on workers
                return t;
            });
            List<Callable<Integer>> inserts = new ArrayList<>();
            for (int w = 0; w < 8; w++) {
                final int worker = w;
                inserts.add(() -> {
                    for (int i = 0; i < 25; i++) {
                        int number = 10_000 + worker * 100 + i;
                        repo.save(AccountFactory.createAccount(number, "W" + worker, 30, 10_000.0, "SALARY"));
                    }
                    return 25;
                });
            }
            int inserted = 0;
            for (Future<Integer> f : exec.invokeAll(inserts)) {
                inserted += f.get();
            }
            check("8 threads inserted 200 accounts without error", inserted == 200 && repo.findAll().size() == 200);
            check("pool stayed within its limit", shared.getTotalConnections() <= 4);

            AtomicInteger moved = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            List<Callable<Void>> transfers = new ArrayList<>();
            for (int w = 0; w < 8; w++) {
                final int worker = w;
                transfers.add(() -> {
                    for (int i = 0; i < 25; i++) {
                        int from = 10_000 + worker * 100 + i;
                        int to = 10_000 + ((worker + 1) % 8) * 100 + i;
                        try {
                            repo.transfer(from, to, 100.0);
                            moved.incrementAndGet();
                        } catch (DataAccessException e) {
                            rejected.incrementAndGet();
                            System.out.println("    unexpected: " + e.getMessage());
                        }
                    }
                    return null;
                });
            }
            for (Future<Void> f : exec.invokeAll(transfers)) {
                f.get();
            }
            exec.shutdown();
            double total = repo.findAll().stream().mapToDouble(a -> a.getBalance()).sum();
            check("200 concurrent transfers all committed (" + moved.get() + " ok, " + rejected.get() + " failed)", moved.get() == 200 && rejected.get() == 0);
            check("money conserved across concurrent transfers", total == 200 * 10_000.0);
            shared.shutdown();

            System.out.println("\n[TEST 5] RepositoryFactory selects the provider from configuration:");
            Properties on = new Properties();
            on.setProperty("persistence.db.url", url);
            on.setProperty("persistence.pool.enabled", "true");
            on.setProperty("persistence.pool.maxSize", "3");
            ConnectionProvider p1 = RepositoryFactory.createConnectionProvider(on);
            check("pool.enabled=true -> HikariConnectionProvider (maxSize 3)",
                p1 instanceof HikariConnectionProvider && p1.getProviderName().contains("maxPoolSize=3"));
            p1.shutdown();
            Properties off = new Properties();
            off.setProperty("persistence.db.url", url);
            off.setProperty("persistence.pool.enabled", "false");
            check("pool.enabled=false -> JdbcConnectionProvider", RepositoryFactory.createConnectionProvider(off) instanceof JdbcConnectionProvider);
            check("pool setting absent -> JdbcConnectionProvider", RepositoryFactory.createConnectionProvider(new Properties()) instanceof JdbcConnectionProvider);
            Properties bad = new Properties();
            bad.setProperty("persistence.db.url", url);
            bad.setProperty("persistence.pool.enabled", "true");
            bad.setProperty("persistence.pool.maxSize", "lots");
            try {
                RepositoryFactory.createConnectionProvider(bad);
                check("non-numeric pool size must be rejected", false);
            } catch (IllegalArgumentException e) {
                check("non-numeric pool size rejected: " + e.getMessage(), e.getMessage().contains("persistence.pool.maxSize"));
            }

            System.out.println("\n[TEST 6] Benchmark: 2000 open -> query -> close cycles");
            int rounds = 2000;
            ConnectionProvider direct = new JdbcConnectionProvider(url);
            HikariConnectionProvider pooled = new HikariConnectionProvider(url, "org.sqlite.JDBC");
            cycle(direct, 200);
            cycle(pooled, 200); // warm-up
            long directMs = cycle(direct, rounds);
            long pooledMs = cycle(pooled, rounds);
            pooled.shutdown();
            System.out.printf("  DriverManager: %d ms (%.3f ms/op)%n", directMs, directMs / (double) rounds);
            System.out.printf("  HikariCP:      %d ms (%.3f ms/op)%n", pooledMs, pooledMs / (double) rounds);
            System.out.printf("  Speed-up:      %.1fx%n", directMs / (double) Math.max(1, pooledMs));
            check("pooled connections are faster than opening new ones", pooledMs < directMs);
        } finally {
            dbFile.delete();
        }

        System.out.println("\n============================================================");
        if (failures == 0) {
            System.out.println("  ALL CHECKS PASSED");
        } else {
            System.out.println("  " + failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    private static long cycle(ConnectionProvider provider, int rounds) throws SQLException {
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            try (Connection c = provider.getConnection();
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM accounts")) {
                rs.next();
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void check(String label, boolean ok) {
        System.out.println("  " + (ok ? "PASS" : "FAIL") + "  " + label);
        if (!ok) {
            failures++;
        }
    }
}
