package com.gdb.db;

import com.gdb.command.TransactionCommand;
import com.gdb.domain.Transaction;
import com.gdb.logging.FileLogDestination;
import com.gdb.logging.LogDestination;
import com.gdb.logging.TransactionLog;
import com.gdb.repository.RepositoryFactory;
import com.gdb.repository.TransactionRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Copies legacy transaction history (serialized command logs, memory logs or plain lists) into a
 * {@link TransactionRepository}. Safe to run repeatedly: transactions whose id is already present are skipped.
 */
public class DataMigrator {

    /** Outcome of one migration run. */
    public record Result(int read, int migrated, int skippedDuplicate, int skippedEmpty) {
        @Override
        public String toString() {
            return "read=" + read + ", migrated=" + migrated
                + ", skippedDuplicate=" + skippedDuplicate + ", skippedEmpty=" + skippedEmpty;
        }
    }

    private final TransactionRepository target;

    public DataMigrator(TransactionRepository target) {
        if (target == null) {
            throw new IllegalArgumentException("Target repository is required");
        }
        this.target = target;
    }

    /** Migrates the transactions recorded by the commands in a log. Commands that never produced one are skipped. */
    public Result migrate(LogDestination source) {
        List<Transaction> fromLog = new ArrayList<>();
        int empty = 0;
        for (TransactionCommand cmd : source.readAll()) {
            Transaction t = cmd == null ? null : cmd.getTransaction();
            if (t == null) {
                empty++;
            } else {
                fromLog.add(t);
            }
        }
        Result partial = migrate(fromLog);
        return new Result(partial.read() + empty, partial.migrated(), partial.skippedDuplicate(), empty);
    }

    /** Migrates a plain list. The whole batch is stored atomically or not at all. */
    public Result migrate(List<Transaction> transactions) {
        Set<String> knownIds = new HashSet<>();
        for (Transaction existing : target.findAll()) {
            knownIds.add(existing.getTransactionId());
        }

        List<Transaction> toInsert = new ArrayList<>();
        int duplicates = 0;
        for (Transaction t : transactions) {
            if (knownIds.add(t.getTransactionId())) { // also drops duplicates inside the source itself
                toInsert.add(t);
            } else {
                duplicates++;
            }
        }
        target.saveAll(toInsert);
        return new Result(transactions.size(), toInsert.size(), duplicates, 0);
    }

    /** Usage: {@code DataMigrator [path/to/transactions.ser]}; migrates into the repository configured in persistence.properties. */
    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : "data/transactions.ser";
        TransactionRepository repo = RepositoryFactory.getTransactionRepository();
        Result result = new DataMigrator(repo).migrate(new FileLogDestination(new TransactionLog(path)));
        System.out.println("Migrated " + path + " into " + repo.getClass().getSimpleName() + ": " + result);
    }
}
