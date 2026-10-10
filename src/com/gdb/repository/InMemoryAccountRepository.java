package com.gdb.repository;

import com.gdb.domain.IAccount;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryAccountRepository implements AccountRepository {
    private final Map<Integer, IAccount> accounts;
    private final AtomicInteger sequence;

    public InMemoryAccountRepository() {
        this(1001);
    }

    public InMemoryAccountRepository(int startSequence) {
        this.accounts = new ConcurrentHashMap<>();
        this.sequence = new AtomicInteger(startSequence);
    }

    @Override
    public void save(IAccount account) {
        if (account != null) {
            accounts.put(account.getAccountNumber(), account);
        }
    }

    @Override
    public Optional<IAccount> findById(int accountNumber) {
        return Optional.ofNullable(accounts.get(accountNumber));
    }

    @Override
    public List<IAccount> findAll() {
        return new ArrayList<>(accounts.values());
    }

    @Override
    public void update(IAccount account) {
        if (account != null) {
            accounts.put(account.getAccountNumber(), account);
        }
    }

    @Override
    public void delete(int accountNumber) {
        accounts.remove(accountNumber);
    }

    @Override
    public boolean exists(int accountNumber) {
        return accounts.containsKey(accountNumber);
    }

    @Override
    public int nextAccountNumber() {
        return sequence.getAndIncrement();
    }

    public void clear() {
        accounts.clear();
        sequence.set(1001);
    }
}
