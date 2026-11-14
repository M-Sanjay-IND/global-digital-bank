package com.gdb.repository;

import com.gdb.domain.IAccount;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    void save(IAccount account);
    Optional<IAccount> findById(int accountNumber);
    List<IAccount> findAll();
    void update(IAccount account);
    void delete(int accountNumber);
    boolean exists(int accountNumber);
    int nextAccountNumber();

    /**
     * Atomically moves {@code amount} from one account to another: either both the debit and
     * the credit are persisted, or neither is.
     *
     * <p>This is a storage-level operation. It checks that both accounts exist and are active,
     * that the amount is positive and that the source holds enough funds. PIN, minimum-balance
     * and daily-limit rules belong to the domain layer and are not applied here.
     *
     * @throws com.gdb.exceptions.DataAccessException if the transfer is rejected or fails
     * @throws UnsupportedOperationException if the implementation has no atomic transfer
     */
    default void transfer(int fromAccountNumber, int toAccountNumber, double amount) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support atomic transfers");
    }
}
