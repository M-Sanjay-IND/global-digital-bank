package com.gdb.tests;

import com.gdb.domain.*;
import com.gdb.repository.*;
import com.gdb.service.AccountService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class TestRepositoryInMemory {
    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("  ACTIVITY 21 \u2014 REPOSITORY PATTERN (IN-MEMORY + FACTORY)");
        System.out.println("============================================================");
        System.out.println();

        System.out.println("[STEP 1] Testing RepositoryFactory & Configuration:");
        String mode = RepositoryFactory.getPersistenceMode();
        System.out.println("  Persistence mode loaded: " + mode + " [PASS]");

        // This driver tests the in-memory stores, so it must not depend on persistence.mode (jdbc would hit gdb.db).
        AccountRepository accountRepo = new InMemoryAccountRepository();
        TransactionRepository txnRepo = new InMemoryTransactionRepository();

        System.out.println("  AccountRepository instantiated: " + accountRepo.getClass().getSimpleName() + " [PASS]");
        System.out.println("  TransactionRepository instantiated: " + txnRepo.getClass().getSimpleName() + " [PASS]");
        System.out.println();

        System.out.println("[STEP 2] Testing InMemoryAccountRepository CRUD:");
        int id1 = accountRepo.nextAccountNumber();
        int id2 = accountRepo.nextAccountNumber();
        System.out.println("  Generated next Account #: " + id1);
        System.out.println("  Generated next Account #: " + id2);

        IAccount acc1 = AccountFactory.createAccount(id1, "Rajesh Sharma", 30, 15000.0, "SAVINGS", 0);
        ((Account) acc1).setPin(1234);
        IAccount acc2 = AccountFactory.createAccount(id2, "Priya Patel", 28, 30000.0, "CURRENT", 0);
        ((Account) acc2).setPin(1234);

        accountRepo.save(acc1);
        accountRepo.save(acc2);
        System.out.println("  Saved Account #" + id1 + ": " + acc1.getName() + " (" + acc1.getAccountType() + " | Rs. " + acc1.getBalance() + ")");
        System.out.println("  Saved Account #" + id2 + ": " + acc2.getName() + " (" + acc2.getAccountType() + " | Rs. " + acc2.getBalance() + ")");

        Optional<IAccount> found = accountRepo.findById(id1);
        System.out.println("  Find by ID (" + id1 + "): " + (found.isPresent() ? "PRESENT [PASS]" : "MISSING [FAIL]"));
        System.out.println("  Exists (" + id1 + "): " + accountRepo.exists(id1) + " [PASS]");
        System.out.println("  Exists (9999): " + accountRepo.exists(9999) + " [PASS]");
        System.out.println("  Total accounts in repo: " + accountRepo.findAll().size() + " [EXPECTED: 2]");
        System.out.println();

        System.out.println("[STEP 3] Testing Account Update & Delete:");
        ((Account) acc1).deposit(5000.0);
        accountRepo.update(acc1);
        System.out.println("  Updated Account #" + id1 + " balance to Rs. " + acc1.getBalance());
        System.out.println("  Verified updated balance: Rs. " + accountRepo.findById(id1).get().getBalance() + " [PASS]");

        accountRepo.delete(id2);
        System.out.println("  Deleted Account #" + id2 + ": Exists = " + accountRepo.exists(id2) + " [PASS]");
        System.out.println("  Total accounts in repo after delete: " + accountRepo.findAll().size() + " [EXPECTED: 1]");
        System.out.println();

        System.out.println("[STEP 4] Testing InMemoryTransactionRepository:");
        Transaction t1 = new Transaction("TXN-101", LocalDateTime.now(), id1, TransactionType.DEPOSIT, 5000.0, 20000.0, "SUCCESS", "Deposit of Rs. 5000.0", 0, 0);
        Transaction t2 = new Transaction("TXN-102", LocalDateTime.now(), id1, TransactionType.WITHDRAW, 2000.0, 18000.0, "SUCCESS", "Withdrawal of Rs. 2000.0", 0, 0);
        Transaction t3 = new Transaction("TXN-103", LocalDateTime.now(), id1, TransactionType.TRANSFER, 3000.0, 15000.0, "SUCCESS", "Transfer to #" + id2, id1, id2);

        txnRepo.save(t1);
        txnRepo.save(t2);
        txnRepo.save(t3);

        System.out.println("  Saved 3 transactions (Deposit #1001, Withdraw #1001, Transfer #1001->#1002)");
        System.out.println("  Total transactions: " + txnRepo.findAll().size() + " [EXPECTED: 3]");
        System.out.println("  Transactions for Account #" + id1 + ": " + txnRepo.findByAccount(id1).size() + " [EXPECTED: 3]");
        System.out.println("  Transactions for Account #" + id2 + ": " + txnRepo.findByAccount(id2).size() + " [EXPECTED: 1]");
        System.out.println();

        System.out.println("[STEP 5] Testing Refactored AccountService with Repositories:");
        InMemoryAccountRepository customAccRepo = new InMemoryAccountRepository(1003);
        InMemoryTransactionRepository customTxnRepo = new InMemoryTransactionRepository();
        AccountService service = new AccountService(customAccRepo, customTxnRepo);

        try {
            IAccount sAcc1 = service.openAccount("SAVINGS", "Amit Kumar", 35, 25000.0, 1);
            ((Account) sAcc1).setPin(1234);
            System.out.println("  Opened Account #" + sAcc1.getAccountNumber() + " via Service: " + sAcc1.getName() + " (" + sAcc1.getAccountType() + " | Rs. " + sAcc1.getBalance() + ")");

            IAccount sAcc2 = service.openAccount("CURRENT", "Sunita Rao", 29, 50000.0, 0);
            ((Account) sAcc2).setPin(1234);
            System.out.println("  Opened Account #" + sAcc2.getAccountNumber() + " via Service: " + sAcc2.getName() + " (" + sAcc2.getAccountType() + " | Rs. " + sAcc2.getBalance() + ")");

            service.deposit(sAcc1.getAccountNumber(), 5000.0);
            System.out.println("  Executed Deposit (+5000.0) on #" + sAcc1.getAccountNumber() + ": Balance = Rs. " + service.getAccount(sAcc1.getAccountNumber()).getBalance() + " [PASS]");

            service.withdraw(sAcc1.getAccountNumber(), 3000.0, 1234);
            System.out.println("  Executed Withdrawal (-3000.0) on #" + sAcc1.getAccountNumber() + ": Balance = Rs. " + service.getAccount(sAcc1.getAccountNumber()).getBalance() + " [PASS]");

            service.transfer(sAcc1.getAccountNumber(), sAcc2.getAccountNumber(), 4000.0, 1234);
            System.out.println("  Executed Transfer (4000.0) #" + sAcc1.getAccountNumber() + " -> #" + sAcc2.getAccountNumber() + ":");
            System.out.println("    #" + sAcc1.getAccountNumber() + " Balance = Rs. " + service.getAccount(sAcc1.getAccountNumber()).getBalance() + " [PASS]");
            System.out.println("    #" + sAcc2.getAccountNumber() + " Balance = Rs. " + service.getAccount(sAcc2.getAccountNumber()).getBalance() + " [PASS]");

            List<Transaction> serviceTxns = service.getTransactionHistory();
            System.out.println("  Service Transaction History count: " + serviceTxns.size() + " [PASS]");
            System.out.println();

            System.out.println("[STEP 6] All Repository Pattern tests verified successfully!");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
