package com.gdb.repository;

import com.gdb.domain.Account;
import com.gdb.domain.IAccount;
import com.gdb.exceptions.DataAccessException;
import com.gdb.file.BackupManager;
import com.gdb.file.CsvFile;
import com.gdb.file.CsvMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * CSV-file-backed {@link AccountRepository}. Every operation reads the file fresh and every change rewrites it
 * atomically (temp file + rename), after backing up the previous version, so a crash never leaves a torn file.
 *
 * <p>Thread-safe within one instance. Two instances (or processes) writing the same file are not coordinated.
 */
public class FileAccountRepository implements AccountRepository {
    private static final int FIRST_ACCOUNT_NUMBER = 1001;

    private final Path file;
    private final BackupManager backups; // null = no backups
    private int lastIssuedNumber = 0;

    public FileAccountRepository(Path file, BackupManager backups) {
        if (file == null) {
            throw new IllegalArgumentException("File path is required");
        }
        this.file = file;
        this.backups = backups;
    }

    public FileAccountRepository(Path file) {
        this(file, null);
    }

    @Override
    public synchronized void save(IAccount account) {
        requireAccount(account);
        List<IAccount> accounts = load();
        if (indexOf(accounts, account.getAccountNumber()) >= 0) {
            throw new DataAccessException("Failed to save account #" + account.getAccountNumber() + ": account already exists");
        }
        accounts.add(account);
        store(accounts);
    }

    @Override
    public synchronized Optional<IAccount> findById(int accountNumber) {
        List<IAccount> accounts = load();
        int i = indexOf(accounts, accountNumber);
        return i < 0 ? Optional.empty() : Optional.of(accounts.get(i));
    }

    @Override
    public synchronized List<IAccount> findAll() {
        List<IAccount> accounts = load();
        accounts.sort(Comparator.comparingInt(IAccount::getAccountNumber));
        return accounts;
    }

    @Override
    public synchronized void update(IAccount account) {
        requireAccount(account);
        List<IAccount> accounts = load();
        int i = indexOf(accounts, account.getAccountNumber());
        if (i < 0) {
            throw new DataAccessException("Cannot update: account #" + account.getAccountNumber() + " does not exist");
        }
        accounts.set(i, account);
        store(accounts);
    }

    @Override
    public synchronized void delete(int accountNumber) {
        List<IAccount> accounts = load();
        int i = indexOf(accounts, accountNumber);
        if (i >= 0) {
            accounts.remove(i);
            store(accounts);
        }
    }

    @Override
    public synchronized boolean exists(int accountNumber) {
        return indexOf(load(), accountNumber) >= 0;
    }

    @Override
    public synchronized int nextAccountNumber() {
        int max = 0;
        for (IAccount a : load()) {
            max = Math.max(max, a.getAccountNumber());
        }
        lastIssuedNumber = Math.max(Math.max(max, lastIssuedNumber) + 1, FIRST_ACCOUNT_NUMBER);
        return lastIssuedNumber;
    }

    /** Both balances change in memory and are written with a single atomic file replacement: all or nothing. */
    @Override
    public synchronized void transfer(int fromAccountNumber, int toAccountNumber, double amount) {
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new DataAccessException("Transfer amount must be positive. Provided: " + amount);
        }
        if (fromAccountNumber == toAccountNumber) {
            throw new DataAccessException("Cannot transfer to the same account (#" + fromAccountNumber + ")");
        }
        List<IAccount> accounts = load();
        int fromIndex = indexOf(accounts, fromAccountNumber);
        int toIndex = indexOf(accounts, toAccountNumber);
        if (fromIndex < 0) {
            throw new DataAccessException("Source account #" + fromAccountNumber + " not found");
        }
        IAccount from = accounts.get(fromIndex);
        if (!"Active".equals(from.getStatus())) {
            throw new DataAccessException("Source account #" + fromAccountNumber + " is not active");
        }
        if (from.getBalance() < amount) {
            throw new DataAccessException("Insufficient balance in account #" + fromAccountNumber
                + ". Available: " + from.getBalance() + ", Requested: " + amount);
        }
        if (toIndex < 0) {
            throw new DataAccessException("Destination account #" + toAccountNumber + " not found");
        }
        IAccount to = accounts.get(toIndex);
        if (!"Active".equals(to.getStatus())) {
            throw new DataAccessException("Destination account #" + toAccountNumber + " is not active");
        }
        adjust(from, -amount);
        adjust(to, amount);
        store(accounts);
    }

    private static void adjust(IAccount account, double delta) {
        ((Account) account).restoreState(account.getBalance() + delta, account.getStatus(), account.getPin(), account.getTenureYears());
    }

    private List<IAccount> load() {
        List<List<String>> rows = CsvFile.read(file, CsvMapper.ACCOUNT_HEADER);
        List<IAccount> accounts = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            try {
                accounts.add(CsvMapper.toAccount(rows.get(i)));
            } catch (DataAccessException e) {
                throw new DataAccessException(file + ", data row " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return accounts;
    }

    private void store(List<IAccount> accounts) {
        List<IAccount> sorted = new ArrayList<>(accounts);
        sorted.sort(Comparator.comparingInt(IAccount::getAccountNumber));
        List<List<String>> rows = new ArrayList<>(sorted.size());
        for (IAccount a : sorted) {
            rows.add(CsvMapper.toRow(a));
        }
        CsvFile.write(file, CsvMapper.ACCOUNT_HEADER, rows, backups);
    }

    private static int indexOf(List<IAccount> accounts, int accountNumber) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getAccountNumber() == accountNumber) {
                return i;
            }
        }
        return -1;
    }

    private static void requireAccount(IAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("Account must not be null");
        }
    }
}
