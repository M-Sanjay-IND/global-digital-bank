package com.gdb.repository;

import com.gdb.domain.Transaction;
import java.util.List;

public interface TransactionRepository {
    void save(Transaction transaction);
    List<Transaction> findByAccount(int accountNumber);
    List<Transaction> findAll();
    void clear();

    /** Saves every transaction; implementations may override to make the batch all-or-nothing. */
    default void saveAll(List<Transaction> batch) {
        for (Transaction t : batch) {
            save(t);
        }
    }
}
