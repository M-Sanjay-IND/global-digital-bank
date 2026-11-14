package com.gdb.repository;

import com.gdb.domain.Transaction;
import com.gdb.exceptions.DataAccessException;
import com.gdb.file.BackupManager;
import com.gdb.file.CsvFile;
import com.gdb.file.CsvMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CSV-file-backed {@link TransactionRepository}; same storage model as {@link FileAccountRepository}:
 * atomic whole-file rewrites with a backup of the previous version.
 */
public class FileTransactionRepository implements TransactionRepository {
    private final Path file;
    private final BackupManager backups; // null = no backups

    public FileTransactionRepository(Path file, BackupManager backups) {
        if (file == null) {
            throw new IllegalArgumentException("File path is required");
        }
        this.file = file;
        this.backups = backups;
    }

    public FileTransactionRepository(Path file) {
        this(file, null);
    }

    @Override
    public synchronized void save(Transaction transaction) {
        saveAll(List.of(requireValid(transaction)));
    }

    /** One atomic file rewrite for the whole batch; a duplicate id rejects the batch before anything is written. */
    @Override
    public synchronized void saveAll(List<Transaction> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        List<Transaction> all = load();
        Set<String> ids = new HashSet<>();
        for (Transaction t : all) {
            ids.add(t.getTransactionId());
        }
        for (Transaction t : batch) {
            requireValid(t);
            if (!ids.add(t.getTransactionId())) {
                throw new DataAccessException("Failed to save transaction " + t.getTransactionId() + ": id already exists");
            }
        }
        all.addAll(batch);
        store(all);
    }

    @Override
    public synchronized List<Transaction> findByAccount(int accountNumber) {
        return load().stream()
            .filter(t -> t.getAccountNumber() == accountNumber || t.getFromAccount() == accountNumber || t.getToAccount() == accountNumber)
            .sorted(Comparator.comparing(Transaction::getTimestamp)) // stable: ties keep file order
            .toList();
    }

    @Override
    public synchronized List<Transaction> findAll() {
        List<Transaction> all = load();
        all.sort(Comparator.comparing(Transaction::getTimestamp));
        return all;
    }

    @Override
    public synchronized void clear() {
        store(new ArrayList<>());
    }

    private List<Transaction> load() {
        List<List<String>> rows = CsvFile.read(file, CsvMapper.TRANSACTION_HEADER);
        List<Transaction> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            try {
                result.add(CsvMapper.toTransaction(rows.get(i)));
            } catch (DataAccessException e) {
                throw new DataAccessException(file + ", data row " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return result;
    }

    private void store(List<Transaction> transactions) {
        List<List<String>> rows = new ArrayList<>(transactions.size());
        for (Transaction t : transactions) {
            rows.add(CsvMapper.toRow(t));
        }
        CsvFile.write(file, CsvMapper.TRANSACTION_HEADER, rows, backups);
    }

    private static Transaction requireValid(Transaction t) {
        if (t == null) {
            throw new IllegalArgumentException("Transaction must not be null");
        }
        if (t.getTransactionId() == null || t.getTimestamp() == null || t.getType() == null) {
            throw new IllegalArgumentException("Transaction needs an id, timestamp and type");
        }
        return t;
    }
}
