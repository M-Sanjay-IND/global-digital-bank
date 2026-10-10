package com.gdb.repository;

import com.gdb.domain.Transaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryTransactionRepository implements TransactionRepository {
    private final List<Transaction> transactions = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void save(Transaction transaction) {
        if (transaction != null) {
            transactions.add(transaction);
        }
    }

    @Override
    public List<Transaction> findByAccount(int accountNumber) {
        synchronized (transactions) {
            return transactions.stream()
                .filter(t -> t.getAccountNumber() == accountNumber || t.getFromAccount() == accountNumber || t.getToAccount() == accountNumber)
                .toList();
        }
    }

    @Override
    public List<Transaction> findAll() {
        synchronized (transactions) {
            return new ArrayList<>(transactions);
        }
    }

    @Override
    public void clear() {
        transactions.clear();
    }
}
