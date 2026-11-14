package com.gdb.tests;

import com.gdb.command.DepositCommand;
import com.gdb.db.DataMigrator;
import com.gdb.domain.Account;
import com.gdb.domain.AccountFactory;
import com.gdb.domain.IAccount;
import com.gdb.domain.Transaction;
import com.gdb.domain.TransactionType;
import com.gdb.exceptions.DataAccessException;
import com.gdb.file.AtomicFileWriter;
import com.gdb.file.BackupManager;
import com.gdb.file.CsvMapper;
import com.gdb.logging.MemoryLogDestination;
import com.gdb.repository.AccountRepository;
import com.gdb.repository.FileAccountRepository;
import com.gdb.repository.FileTransactionRepository;
import com.gdb.repository.RepositoryFactory;
import com.gdb.repository.TransactionRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

public class TestFileRepository {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("  ACTIVITY 26 — FILE REPOSITORY (CSV + ATOMIC WRITES + BACKUP)");
        System.out.println("============================================================");

        Path dir = Files.createTempDirectory("gdb_file_test");
        try {
            csvMapper();
            atomicWriter(dir.resolve("atomic"));
            backupManager(dir.resolve("backup"));
            accountRepository(dir.resolve("accounts"));
            transactionRepository(dir.resolve("txns"));
            factoryAndMigration(dir.resolve("factory"));
        } finally {
            deleteRecursively(dir);
        }

        System.out.println("\n============================================================");
        if (failures == 0) {
            System.out.println("  ALL CHECKS PASSED");
        } else {
            System.out.println("  " + failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------------------------------------

    private static void csvMapper() {
        System.out.println("\n[TEST 1] CsvMapper: quoting and model round trips");
        List<List<String>> tricky = List.of(
            List.of("plain", "with,comma", "with \"quotes\"", "two\nlines", "", " padded "),
            List.of("", "", ""));
        check("text with commas, quotes, newlines and empties round-trips", CsvMapper.parse(CsvMapper.format(tricky)).equals(tricky));
        check("CRLF line endings and missing final newline are accepted",
            CsvMapper.parse("a,b\r\nc,d").equals(List.of(List.of("a", "b"), List.of("c", "d"))));
        expectFailure("unterminated quote rejected", () -> CsvMapper.parse("a,\"oops\n"));
        expectFailure("text after closing quote rejected", () -> CsvMapper.parse("\"a\"b,c\n"));

        Account acct = (Account) AccountFactory.createAccount(1001, "O'Brien, \"Bob\"\nJr", 40, 25000.5, "SAVINGS", 4);
        IAccount back = CsvMapper.toAccount(CsvMapper.toRow(acct));
        check("awkward holder name survives", back.getName().equals("O'Brien, \"Bob\"\nJr"));
        check("balance/type/tenure survive", back.getBalance() == 25000.5 && back.getTenureYears() == 4
            && back.getClass().getSimpleName().equals("SavingsAccount"));
        check("missing PIN stays absent", !back.hasPin());
        acct.setPin(4321);
        check("PIN survives", CsvMapper.toAccount(CsvMapper.toRow(acct)).verifyPin(4321));

        Transaction t = new Transaction("T-1", LocalDateTime.of(2026, 11, 14, 9, 30, 15, 123_000_000), 1001,
            TransactionType.TRANSFER, 12.34, 99.99, "SUCCESS", "Pay, \"rent\"", 1001, 1002);
        Transaction tb = CsvMapper.toTransaction(CsvMapper.toRow(t));
        check("transaction fields survive", tb.getTransactionId().equals("T-1") && tb.getTimestamp().equals(t.getTimestamp())
            && tb.getType() == TransactionType.TRANSFER && tb.getAmount() == 12.34 && tb.getBalanceAfter() == 99.99
            && tb.getDescription().equals("Pay, \"rent\"") && tb.getFromAccount() == 1001 && tb.getToAccount() == 1002);
        Transaction none = CsvMapper.toTransaction(CsvMapper.toRow(
            new Transaction("T-2", t.getTimestamp(), 1, TransactionType.DEPOSIT, 1, 1, "SUCCESS", "d", 0, 0)));
        check("from/to of 0 survive", none.getFromAccount() == 0 && none.getToAccount() == 0);
        expectFailure("wrong column count rejected", () -> CsvMapper.toAccount(List.of("1", "x")));
        expectFailure("non-numeric balance rejected", () -> CsvMapper.toAccount(List.of("1", "N", "30", "lots", "SALARY", "Active", "", "0")));
        expectFailure("unknown transaction type rejected", () -> CsvMapper.toTransaction(
            List.of("T", "2026-11-14T09:00", "1", "BOGUS", "1", "1", "OK", "", "", "")));
    }

    private static void atomicWriter(Path dir) throws IOException {
        System.out.println("\n[TEST 2] AtomicFileWriter: all-or-nothing replacement");
        Path file = dir.resolve("data.txt");
        AtomicFileWriter.writeString(file, "version 1");
        check("creates missing directories and the file", Files.readString(file).equals("version 1"));
        AtomicFileWriter.writeString(file, "version 2");
        check("replaces existing content", Files.readString(file).equals("version 2"));
        check("leaves no temp files behind", listNames(dir).equals(List.of("data.txt")));

        try {
            AtomicFileWriter.write(file, out -> {
                out.write("version 3 (half writ".getBytes(StandardCharsets.UTF_8));
                throw new IOException("simulated crash mid-write");
            });
            check("crash must surface as an exception", false);
        } catch (IOException e) {
            check("crash mid-write surfaces as IOException", true);
        }
        check("old content intact after the crash", Files.readString(file).equals("version 2"));
        check("temp file cleaned up after the crash", listNames(dir).equals(List.of("data.txt")));
    }

    private static void backupManager(Path dir) throws Exception {
        System.out.println("\n[TEST 3] BackupManager: timestamped copies and pruning");
        Path file = dir.resolve("accounts.csv");
        BackupManager manager = new BackupManager(3);
        check("nothing to back up -> empty", manager.backup(file).isEmpty());
        for (int i = 1; i <= 5; i++) {
            AtomicFileWriter.writeString(file, "content " + i);
            Path b = manager.backup(file).orElseThrow();
            if (i == 1) {
                check("backup name is <file>.bak.<timestamp>", b.getFileName().toString().matches("accounts\\.csv\\.bak\\.\\d{8}-\\d{9}"));
                check("backup content equals the file", Files.readString(b).equals("content 1"));
            }
        }
        List<Path> kept = manager.list(file);
        check("only the newest 3 of 5 backups are kept", kept.size() == 3);
        check("kept backups are the newest, oldest first", contents(kept).equals(List.of("content 3", "content 4", "content 5")));
        check("backups of other files are not counted", manager.list(dir.resolve("other.csv")).isEmpty());

        AtomicFileWriter.writeString(file, "corrupted");
        check("restoreLatest returns the newest backup", manager.restoreLatest(file).isPresent());
        check("file restored from latest backup", Files.readString(file).equals("content 5"));
        check("restore with no backups -> empty", manager.restoreLatest(dir.resolve("none.csv")).isEmpty());
        try {
            new BackupManager(0);
            check("keep=0 must be rejected", false);
        } catch (IllegalArgumentException e) {
            check("keep=0 rejected", true);
        }
    }

    private static void accountRepository(Path dir) throws Exception {
        System.out.println("\n[TEST 4] FileAccountRepository");
        Path file = dir.resolve("accounts.csv");
        AccountRepository repo = new FileAccountRepository(file, new BackupManager(3));

        check("missing file reads as empty", repo.findAll().isEmpty() && !repo.exists(1001));
        check("first account number is 1001", repo.nextAccountNumber() == 1001);
        check("numbers never repeat before a save", repo.nextAccountNumber() == 1002);

        Account a = (Account) AccountFactory.createAccount(1001, "Rajesh Sharma", 30, 20000.0, "SAVINGS", 4);
        a.setPin(1234);
        repo.save(a);
        Account c = (Account) AccountFactory.createAccount(1002, "Priya, Patel", 28, 2000.0, "CURRENT", 0);
        c.setPin(4321);
        repo.save(c);
        check("header + 2 rows on disk", Files.readAllLines(file).size() == 3);

        AccountRepository reopened = new FileAccountRepository(file); // a brand-new instance reads the file
        IAccount loaded = reopened.findById(1001).orElseThrow();
        check("data survives a new instance", loaded.getName().equals("Rajesh Sharma") && loaded.getBalance() == 20000.0
            && loaded.verifyPin(1234) && loaded.getTenureYears() == 4);
        check("next number follows stored accounts", reopened.nextAccountNumber() == 1003);
        check("findAll ordered by number", reopened.findAll().stream().map(IAccount::getAccountNumber).toList().equals(List.of(1001, 1002)));

        Account overdrawn = (Account) reopened.findById(1002).orElseThrow();
        overdrawn.withdraw(10000.0, 4321);
        reopened.update(overdrawn);
        check("negative balance (overdraft) survives", reopened.findById(1002).orElseThrow().getBalance() == -8000.0);

        expectFailure("duplicate save rejected", () -> repo.save(a));
        expectFailure("update of missing account rejected", () -> repo.update(AccountFactory.createAccount(9, "Ghost", 30, 5000.0, "SALARY")));
        check("backups were taken before writes", new BackupManager(3).list(file).size() >= 1);

        System.out.println("  -- atomic transfer");
        repo.save(AccountFactory.createAccount(2001, "Source", 40, 10000.0, "SALARY"));
        repo.save(AccountFactory.createAccount(2002, "Target", 40, 1000.0, "SALARY"));
        repo.transfer(2001, 2002, 2500.0);
        check("debit and credit both applied", repo.findById(2001).orElseThrow().getBalance() == 7500.0
            && repo.findById(2002).orElseThrow().getBalance() == 3500.0);
        String before = Files.readString(file);
        expectFailure("insufficient funds", () -> repo.transfer(2001, 2002, 999999.0));
        expectFailure("missing destination", () -> repo.transfer(2001, 4040, 100.0));
        expectFailure("negative amount", () -> repo.transfer(2001, 2002, -1));
        expectFailure("same account", () -> repo.transfer(2001, 2001, 1));
        check("rejected transfers leave the file byte-for-byte unchanged", Files.readString(file).equals(before));
        Account closed = (Account) AccountFactory.createAccount(3001, "Closed", 50, 5000.0, "SALARY");
        closed.closeAccount();
        repo.save(closed);
        expectFailure("inactive destination", () -> repo.transfer(2001, 3001, 100.0));
        check("source untouched by the failed transfer", repo.findById(2001).orElseThrow().getBalance() == 7500.0);

        repo.delete(3001);
        check("delete removes the row", !repo.exists(3001));
        repo.delete(3001);
        check("deleting a missing account is a no-op", true);

        System.out.println("  -- corrupt data is reported, never silently dropped");
        Path bad = dir.resolve("bad.csv");
        Files.writeString(bad, "wrong,header\n1,2\n");
        expectFailure("unexpected header rejected", () -> new FileAccountRepository(bad).findAll());
        Path badRow = dir.resolve("badrow.csv");
        Files.writeString(badRow, String.join(",", CsvMapper.ACCOUNT_HEADER) + "\n1001,Ok,30,5000.0,SALARY,Active,,0\n1002,Bad,30,not-a-number,SALARY,Active,,0\n");
        try {
            new FileAccountRepository(badRow).findAll();
            check("corrupt row must be rejected", false);
        } catch (DataAccessException e) {
            check("corrupt row rejected with its row number", e.getMessage().contains("data row 2"));
        }
    }

    private static void transactionRepository(Path dir) throws Exception {
        System.out.println("\n[TEST 5] FileTransactionRepository");
        Path file = dir.resolve("transactions.csv");
        TransactionRepository repo = new FileTransactionRepository(file, new BackupManager(2));
        LocalDateTime t0 = LocalDateTime.of(2026, 11, 14, 9, 0);

        check("missing file reads as empty", repo.findAll().isEmpty());
        Transaction dep = new Transaction("T-1", t0, 1001, TransactionType.DEPOSIT, 500.0, 5500.0, "SUCCESS", "Deposit", 0, 0);
        Transaction xfer = new Transaction("T-2", t0.plusMinutes(1), 1001, TransactionType.TRANSFER, 200.0, 5300.0, "SUCCESS", "Transfer, with comma", 1001, 1002);
        repo.save(xfer); // out of order on purpose
        repo.save(dep);
        List<Transaction> all = new FileTransactionRepository(file).findAll(); // fresh instance
        check("data survives a new instance", all.size() == 2);
        check("ordered by timestamp, not insert order", all.get(0).getTransactionId().equals("T-1"));
        check("fields incl. comma in description restored", all.get(1).getDescription().equals("Transfer, with comma")
            && all.get(1).getFromAccount() == 1001 && all.get(1).getToAccount() == 1002 && all.get(0).getFromAccount() == 0);
        expectFailure("duplicate id rejected", () -> repo.save(dep));

        repo.save(new Transaction("T-3", t0.plusMinutes(2), 1003, TransactionType.DEPOSIT, 10.0, 10.0, "SUCCESS", "Other", 0, 0));
        check("findByAccount: owner and sender", ids(repo.findByAccount(1001)).equals(List.of("T-1", "T-2")));
        check("findByAccount: receiver", ids(repo.findByAccount(1002)).equals(List.of("T-2")));
        check("findByAccount: unknown account", repo.findByAccount(9999).isEmpty());

        String before = Files.readString(file);
        Transaction ok = new Transaction("B-1", t0.plusHours(1), 1001, TransactionType.WITHDRAW, 1.0, 1.0, "SUCCESS", "ok", 0, 0);
        Transaction clash = new Transaction("T-1", t0.plusHours(2), 1001, TransactionType.WITHDRAW, 1.0, 1.0, "SUCCESS", "clash", 0, 0);
        expectFailure("batch containing a duplicate id", () -> repo.saveAll(List.of(ok, clash)));
        check("failed batch wrote nothing", Files.readString(file).equals(before));
        repo.saveAll(List.of(ok));
        check("valid batch stored", repo.findAll().size() == 4);

        check("backup limit respected", new BackupManager(2).list(file).size() <= 2);
        repo.clear();
        check("clear empties the history", repo.findAll().isEmpty());
        check("cleared file still has its header", Files.readAllLines(file).equals(List.of(String.join(",", CsvMapper.TRANSACTION_HEADER))));
    }

    private static void factoryAndMigration(Path dir) throws Exception {
        System.out.println("\n[TEST 6] RepositoryFactory (persistence.mode=file) and DataMigrator");
        Properties props = new Properties();
        props.setProperty("persistence.file.dir", dir.toString());
        props.setProperty("persistence.file.accounts", "acc.csv");
        props.setProperty("persistence.file.transactions", "txn.csv");
        props.setProperty("persistence.file.backups.keep", "2");

        AccountRepository accounts = RepositoryFactory.createFileAccountRepository(props);
        TransactionRepository txns = RepositoryFactory.createFileTransactionRepository(props);
        check("factory builds file repositories", accounts instanceof FileAccountRepository && txns instanceof FileTransactionRepository);
        accounts.save(AccountFactory.createAccount(1001, "Factory", 30, 5000.0, "SALARY"));
        accounts.update(accounts.findById(1001).orElseThrow());
        check("files created in the configured directory with the configured names", Files.exists(dir.resolve("acc.csv")));
        check("configured backup count applies", new BackupManager(9).list(dir.resolve("acc.csv")).size() == 1);

        props.setProperty("persistence.file.backups.keep", "0");
        Path noBackupDir = dir.resolve("nobackup");
        props.setProperty("persistence.file.dir", noBackupDir.toString());
        AccountRepository noBackups = RepositoryFactory.createFileAccountRepository(props);
        noBackups.save(AccountFactory.createAccount(1, "A", 30, 5000.0, "SALARY"));
        noBackups.save(AccountFactory.createAccount(2, "B", 30, 5000.0, "SALARY"));
        check("keep=0 disables backups", new BackupManager(9).list(noBackupDir.resolve("accounts.csv")).isEmpty());
        props.setProperty("persistence.file.backups.keep", "many");
        try {
            RepositoryFactory.createFileAccountRepository(props);
            check("non-numeric keep must be rejected", false);
        } catch (IllegalArgumentException e) {
            check("non-numeric keep rejected", e.getMessage().contains("persistence.file.backups.keep"));
        }

        Account a = (Account) AccountFactory.createAccount(7001, "Mig", 30, 5000.0, "SALARY");
        MemoryLogDestination legacy = new MemoryLogDestination();
        DepositCommand d1 = new DepositCommand(a, 100.0);
        d1.execute();
        legacy.write(d1);
        DataMigrator migrator = new DataMigrator(txns);
        check("DataMigrator imports into the file repository", migrator.migrate(legacy).migrated() == 1 && txns.findAll().size() == 1);
        check("and is idempotent there too", migrator.migrate(legacy).migrated() == 0 && txns.findAll().size() == 1);
    }

    // ------------------------------------------------------------------------------------------------

    private static List<String> ids(List<Transaction> txns) {
        return txns.stream().map(Transaction::getTransactionId).toList();
    }

    private static List<String> listNames(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static List<String> contents(List<Path> files) throws IOException {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Path p : files) {
            out.add(Files.readString(p));
        }
        return out;
    }

    private static void deleteRecursively(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
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
