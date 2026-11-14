package com.gdb.tests;

import com.gdb.command.DepositCommand;
import com.gdb.command.TransferCommand;
import com.gdb.command.WithdrawCommand;
import com.gdb.db.ConnectionProvider;
import com.gdb.db.DataMigrator;
import com.gdb.db.JdbcConnectionProvider;
import com.gdb.db.SchemaInitializer;
import com.gdb.domain.Account;
import com.gdb.domain.AccountFactory;
import com.gdb.domain.Transaction;
import com.gdb.domain.TransactionType;
import com.gdb.exceptions.DataAccessException;
import com.gdb.logging.FileLogDestination;
import com.gdb.logging.LogDestination;
import com.gdb.logging.MemoryLogDestination;
import com.gdb.logging.TransactionLog;
import com.gdb.repository.InMemoryTransactionRepository;
import com.gdb.repository.JdbcTransactionRepository;
import com.gdb.repository.TransactionRepository;
import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

public class TestJdbcTransactionRepository {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("  ACTIVITY 24 — JdbcTransactionRepository & DATA MIGRATION");
        System.out.println("============================================================");

        File dbFile = File.createTempFile("gdb_txn_test", ".db");
        File serFile = File.createTempFile("gdb_txn_legacy", ".ser");
        serFile.delete(); // TransactionLog creates it on first write
        try {
            ConnectionProvider provider = new JdbcConnectionProvider("jdbc:sqlite:" + dbFile.getAbsolutePath());
            SchemaInitializer.initialize(provider);
            TransactionRepository repo = new JdbcTransactionRepository(provider);

            LocalDateTime t0 = LocalDateTime.of(2026, 11, 14, 9, 0, 0);

            System.out.println("\n[TEST 1] save + findAll round trip:");
            Transaction dep = new Transaction("T-1", t0, 1001, TransactionType.DEPOSIT, 500.0, 5500.0, "SUCCESS", "Deposit", 0, 0);
            Transaction xfer = new Transaction("T-2", t0.plusMinutes(1), 1001, TransactionType.TRANSFER, 200.0, 5300.0, "SUCCESS", "Transfer", 1001, 1002);
            repo.save(xfer); // saved out of order on purpose
            repo.save(dep);
            List<Transaction> all = repo.findAll();
            check("two rows stored", all.size() == 2);
            check("ordered by timestamp, not insert order", all.get(0).getTransactionId().equals("T-1"));
            Transaction d = all.get(0);
            check("all fields restored", d.getTimestamp().equals(t0) && d.getAccountNumber() == 1001
                && d.getType() == TransactionType.DEPOSIT && d.getAmount() == 500.0 && d.getBalanceAfter() == 5500.0
                && "SUCCESS".equals(d.getStatus()) && "Deposit".equals(d.getDescription()));
            check("from/to of 0 survive as 0 (stored NULL)", d.getFromAccount() == 0 && d.getToAccount() == 0);
            check("transfer from/to restored", all.get(1).getFromAccount() == 1001 && all.get(1).getToAccount() == 1002);
            expectFailure("duplicate id rejected", () -> repo.save(dep));

            System.out.println("\n[TEST 2] findByAccount matches owner, sender and receiver:");
            repo.save(new Transaction("T-3", t0.plusMinutes(2), 1003, TransactionType.DEPOSIT, 10.0, 10.0, "SUCCESS", "Other", 0, 0));
            check("1001 sees its deposit and transfer", ids(repo.findByAccount(1001)).equals(List.of("T-1", "T-2")));
            check("1002 sees the incoming transfer", ids(repo.findByAccount(1002)).equals(List.of("T-2")));
            check("unknown account sees nothing", repo.findByAccount(9999).isEmpty());

            System.out.println("\n[TEST 3] saveAll is all-or-nothing:");
            Transaction ok = new Transaction("B-1", t0.plusHours(1), 1001, TransactionType.WITHDRAW, 1.0, 1.0, "SUCCESS", "ok", 0, 0);
            Transaction clash = new Transaction("T-1", t0.plusHours(2), 1001, TransactionType.WITHDRAW, 1.0, 1.0, "SUCCESS", "clashes with T-1", 0, 0);
            expectFailure("batch containing a duplicate id", () -> repo.saveAll(List.of(ok, clash)));
            check("nothing from the failed batch was stored", repo.findAll().size() == 3);
            repo.saveAll(List.of(ok));
            check("valid batch stored", repo.findAll().size() == 4);

            System.out.println("\n[TEST 4] clear:");
            repo.clear();
            check("table emptied", repo.findAll().isEmpty());

            System.out.println("\n[TEST 5] migrate a legacy file log (serialized commands):");
            Account a = (Account) AccountFactory.createAccount(5001, "Alpha", 30, 50000.0, "SALARY");
            Account b = (Account) AccountFactory.createAccount(5002, "Beta", 31, 20000.0, "SALARY");
            a.setPin(1234);
            LogDestination fileLog = new FileLogDestination(new TransactionLog(serFile.getAbsolutePath()));
            DepositCommand c1 = new DepositCommand(a, 1000.0);
            WithdrawCommand c2 = new WithdrawCommand(a, 500.0, 1234);
            TransferCommand c3 = new TransferCommand(a, b, 2000.0, 1234);
            for (var cmd : List.of(c1, c2, c3)) {
                cmd.execute();
                fileLog.write(cmd);
            }
            fileLog.write(new DepositCommand(a, 1.0)); // never executed -> no transaction

            DataMigrator migrator = new DataMigrator(repo);
            DataMigrator.Result r1 = migrator.migrate(fileLog);
            System.out.println("  result: " + r1);
            check("4 commands read, 3 migrated, 1 empty skipped", r1.read() == 4 && r1.migrated() == 3 && r1.skippedEmpty() == 1);
            List<Transaction> migrated = repo.findAll();
            check("migrated rows match the originals", migrated.size() == 3
                && ids(migrated).containsAll(List.of(c1.getTransaction().getTransactionId(),
                    c2.getTransaction().getTransactionId(), c3.getTransaction().getTransactionId())));
            check("transfer details preserved", repo.findByAccount(5002).size() == 1
                && repo.findByAccount(5002).get(0).getAmount() == 2000.0);

            System.out.println("\n[TEST 6] migration is idempotent:");
            DataMigrator.Result r2 = migrator.migrate(fileLog);
            System.out.println("  result: " + r2);
            check("second run migrates nothing", r2.migrated() == 0 && r2.skippedDuplicate() == 3);
            check("row count unchanged", repo.findAll().size() == 3);

            System.out.println("\n[TEST 7] migrate from a memory log into the in-memory repository:");
            MemoryLogDestination mem = new MemoryLogDestination();
            DepositCommand m1 = new DepositCommand(b, 5.0);
            m1.execute();
            mem.write(m1);
            TransactionRepository inMem = new InMemoryTransactionRepository();
            DataMigrator.Result r3 = new DataMigrator(inMem).migrate(mem);
            check("works for any TransactionRepository", r3.migrated() == 1 && inMem.findAll().size() == 1);

            System.out.println("\n[TEST 8] duplicates inside the source are collapsed:");
            Transaction dup = new Transaction("D-1", t0, 1, TransactionType.DEPOSIT, 1.0, 1.0, "SUCCESS", "x", 0, 0);
            DataMigrator.Result r4 = migrator.migrate(List.of(dup, dup));
            check("one stored, one skipped", r4.migrated() == 1 && r4.skippedDuplicate() == 1);
        } finally {
            dbFile.delete();
            serFile.delete();
        }

        System.out.println("\n============================================================");
        if (failures == 0) {
            System.out.println("  ALL CHECKS PASSED");
        } else {
            System.out.println("  " + failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    private static List<String> ids(List<Transaction> txns) {
        return txns.stream().map(Transaction::getTransactionId).toList();
    }

    private static void check(String label, boolean ok) {
        System.out.println("  " + (ok ? "PASS" : "FAIL") + "  " + label);
        if (!ok) {
            failures++;
        }
    }

    private static void expectFailure(String label, Runnable action) {
        try {
            action.run();
            check(label + " (expected DataAccessException)", false);
        } catch (DataAccessException e) {
            check(label, true);
        } catch (RuntimeException e) {
            check(label + " (wrong exception " + e + ")", false);
        }
    }
}
