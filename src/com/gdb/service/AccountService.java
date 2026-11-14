package com.gdb.service;

import com.gdb.command.*;
import com.gdb.domain.*;
import com.gdb.exceptions.*;
import com.gdb.logging.TransactionLogger;
import com.gdb.repository.AccountRepository;
import com.gdb.repository.InMemoryAccountRepository;
import com.gdb.repository.InMemoryTransactionRepository;
import com.gdb.repository.RepositoryFactory;
import com.gdb.repository.TransactionRepository;
import java.util.*;

public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionLogger logger;
    private final TransferService transferService;

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                          TransactionLogger logger, TransferService transferService) {
        this.accountRepository = accountRepository != null ? accountRepository : resolveAccountRepository();
        this.transactionRepository = transactionRepository != null ? transactionRepository : resolveTransactionRepository();
        this.logger = logger;
        this.transferService = transferService != null ? transferService : new TransferService();
    }

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this(accountRepository, transactionRepository, null, new TransferService());
    }

    public AccountService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                          TransactionLogger logger) {
        this(accountRepository, transactionRepository, logger, new TransferService());
    }

    public AccountService(TransactionLogger logger, TransferService transferService) {
        this(resolveAccountRepository(), resolveTransactionRepository(), logger, transferService);
    }

    public AccountService(TransactionLogger logger) {
        this(resolveAccountRepository(), resolveTransactionRepository(), logger, new TransferService());
    }

    public AccountService() {
        this(resolveAccountRepository(), resolveTransactionRepository(), null, new TransferService());
    }

    private static AccountRepository resolveAccountRepository() {
        try {
            return RepositoryFactory.getAccountRepository();
        } catch (UnsupportedOperationException e) {
            return new InMemoryAccountRepository();
        }
    }

    private static TransactionRepository resolveTransactionRepository() {
        try {
            return RepositoryFactory.getTransactionRepository();
        } catch (UnsupportedOperationException e) {
            return new InMemoryTransactionRepository();
        }
    }

    public IAccount openAccount(String type, String name, int age, double initialBalance) {
        return openAccount(type, name, age, initialBalance, 0);
    }

    public IAccount openAccount(String type, String name, int age, double initialBalance, int tenureYears) {
        int accNo = accountRepository.nextAccountNumber();
        IAccount account = AccountFactory.createAccount(accNo, name, age, initialBalance, type, tenureYears);
        accountRepository.save(account);
        return account;
    }

    /** Opens an account with its PIN set before it is persisted, so an invalid PIN creates nothing. */
    public IAccount openAccount(String type, String name, int age, double initialBalance, int tenureYears, int pin) {
        int accNo = accountRepository.nextAccountNumber();
        IAccount account = AccountFactory.createAccount(accNo, name, age, initialBalance, type, tenureYears);
        account.setPin(pin);
        accountRepository.save(account);
        return account;
    }

    public void closeAccount(int accountNumber, int pin) throws AccountException {
        IAccount account = getAccount(accountNumber);
        if (account == null) {
            throw new AccountException("Account not found: " + accountNumber);
        }
        if (!account.verifyPin(pin)) {
            throw new InvalidPinException("Incorrect PIN");
        }
        if (account instanceof Account acc) {
            acc.closeAccount();
            accountRepository.update(acc);
        }
    }

    public Transaction deposit(int accountNumber, double amount) throws Exception {
        IAccount account = getAccount(accountNumber);
        if (account == null) {
            throw new AccountException("Account not found: " + accountNumber);
        }
        DepositCommand cmd = new DepositCommand((Account) account, amount);
        cmd.execute();
        accountRepository.update(account);
        if (transactionRepository != null) {
            transactionRepository.save(cmd.getTransaction());
        }
        if (logger != null) {
            logger.log(cmd);
        }
        return cmd.getTransaction();
    }

    public Transaction withdraw(int accountNumber, double amount, int pin) throws Exception {
        IAccount account = getAccount(accountNumber);
        if (account == null) {
            throw new AccountException("Account not found: " + accountNumber);
        }
        WithdrawCommand cmd = new WithdrawCommand((Account) account, amount, pin);
        cmd.execute();
        accountRepository.update(account);
        if (transactionRepository != null) {
            transactionRepository.save(cmd.getTransaction());
        }
        if (logger != null) {
            logger.log(cmd);
        }
        return cmd.getTransaction();
    }

    public Transaction transfer(int fromAccNo, int toAccNo, double amount, int pin) throws Exception {
        IAccount fromAccount = getAccount(fromAccNo);
        if (fromAccount == null) {
            throw new AccountException("Account not found: " + fromAccNo);
        }
        IAccount toAccount = getAccount(toAccNo);
        if (toAccount == null) {
            throw new AccountException("Account not found: " + toAccNo);
        }
        TransferCommand cmd = new TransferCommand((Account) fromAccount, (Account) toAccount, amount, pin);
        cmd.execute();
        accountRepository.update(fromAccount);
        accountRepository.update(toAccount);
        if (transactionRepository != null) {
            transactionRepository.save(cmd.getTransaction());
        }
        if (logger != null) {
            logger.log(cmd);
        }
        return cmd.getTransaction();
    }

    public IAccount getAccount(int accountNumber) {
        return accountRepository.findById(accountNumber).orElse(null);
    }

    public Collection<IAccount> getAllAccounts() {
        return accountRepository.findAll();
    }

    public List<Transaction> getTransactionHistory() {
        if (transactionRepository != null) {
            List<Transaction> txns = transactionRepository.findAll();
            if (!txns.isEmpty()) {
                return txns;
            }
        }
        if (logger != null) {
            List<TransactionCommand> commands = logger.readAll();
            List<Transaction> transactions = new ArrayList<>();
            for (TransactionCommand cmd : commands) {
                if (cmd != null && cmd.getTransaction() != null) {
                    transactions.add(cmd.getTransaction());
                }
            }
            return transactions;
        }
        return Collections.emptyList();
    }

    public List<Transaction> getTransactionHistory(int accountNumber) {
        if (transactionRepository != null) {
            return transactionRepository.findByAccount(accountNumber);
        }
        return getTransactionHistory().stream()
            .filter(t -> t.getAccountNumber() == accountNumber || t.getFromAccount() == accountNumber || t.getToAccount() == accountNumber)
            .toList();
    }

    public int getNextAccountNumber() {
        return accountRepository.nextAccountNumber();
    }

    public AccountRepository getAccountRepository() {
        return accountRepository;
    }

    public TransactionRepository getTransactionRepository() {
        return transactionRepository;
    }

    public TransactionLogger getLogger() {
        return logger;
    }

    public TransferService getTransferService() {
        return transferService;
    }
}
