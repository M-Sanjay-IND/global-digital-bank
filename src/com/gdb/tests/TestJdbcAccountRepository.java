package com.gdb.tests;

import com.gdb.db.ConnectionProvider;
import com.gdb.db.JdbcConnectionProvider;
import com.gdb.db.SchemaInitializer;
import com.gdb.domain.Account;
import com.gdb.domain.AccountFactory;
import com.gdb.domain.IAccount;
import com.gdb.exceptions.DataAccessException;
import com.gdb.repository.AccountRepository;
import com.gdb.repository.JdbcAccountRepository;
import java.io.File;
import java.util.List;
import java.util.Optional;

public class TestJdbcAccountRepository {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("  ACTIVITY 23 — JdbcAccountRepository (CRUD + ATOMIC TRANSFER)");
        System.out.println("============================================================");

        File dbFile = File.createTempFile("gdb_accounts_test", ".db");
        try {
            ConnectionProvider provider = new JdbcConnectionProvider("jdbc:sqlite:" + dbFile.getAbsolutePath());
            SchemaInitializer.initialize(provider);
            AccountRepository repo = new JdbcAccountRepository(provider);

            System.out.println("\n[TEST 1] save + findById round trip:");
            check("empty repo hands out 1001 first", repo.nextAccountNumber() == 1001);
            Account savings = (Account) AccountFactory.createAccount(1001, "Rajesh Sharma", 30, 20000.0, "SAVINGS", 4);
            savings.setPin(1234);
            repo.save(savings);
            Optional<IAccount> loaded = repo.findById(1001);
            check("account found", loaded.isPresent());
            IAccount l = loaded.get();
            check("type restored as SavingsAccount", l.getClass().getSimpleName().equals("SavingsAccount"));
            check("name/age/balance restored", l.getName().equals("Rajesh Sharma") && l.getAge() == 30 && l.getBalance() == 20000.0);
            check("tenure restored", l.getTenureYears() == 4);
            check("PIN restored", l.verifyPin(1234));
            check("status restored", "Active".equals(l.getStatus()));
            int n1 = repo.nextAccountNumber();
            int n2 = repo.nextAccountNumber();
            check("next number follows highest", n1 == 1002);
            check("repeated calls never repeat a number", n2 == 1003);

            System.out.println("\n[TEST 2] null PIN, overdrawn Current account:");
            Account current = (Account) AccountFactory.createAccount(1002, "Priya Patel", 28, 2000.0, "CURRENT", 0);
            repo.save(current);
            check("no PIN stored as null", !repo.findById(1002).get().hasPin());
            current.setPin(4321);
            current.withdraw(10000.0, 4321);
            repo.update(current);
            IAccount overdrawn = repo.findById(1002).get();
            check("negative balance survives reload", overdrawn.getBalance() == -8000.0);
            check("PIN persisted by update", overdrawn.verifyPin(4321));

            System.out.println("\n[TEST 3] update / exists / findAll / delete:");
            Account reloaded = (Account) repo.findById(1001).get();
            reloaded.deposit(500.0);
            reloaded.closeAccount();
            repo.update(reloaded);
            check("balance + status updated", repo.findById(1001).get().getBalance() == 20500.0
                && "Inactive".equals(repo.findById(1001).get().getStatus()));
            check("exists true/false", repo.exists(1001) && !repo.exists(9999));
            check("findAll ordered", ids(repo.findAll()).equals(List.of(1001, 1002)));
            expectFailure("update of missing account rejected", () -> repo.update(AccountFactory.createAccount(7777, "Ghost", 30, 5000.0, "SALARY")));
            expectFailure("duplicate save rejected", () -> repo.save(savings));
            repo.delete(1002);
            check("delete removes row", !repo.exists(1002) && repo.findAll().size() == 1);

            System.out.println("\n[TEST 4] atomic transfer commits both sides:");
            repo.save(AccountFactory.createAccount(2001, "Source", 40, 10000.0, "SALARY"));
            repo.save(AccountFactory.createAccount(2002, "Target", 40, 1000.0, "SALARY"));
            repo.transfer(2001, 2002, 2500.0);
            check("debit applied", repo.findById(2001).get().getBalance() == 7500.0);
            check("credit applied", repo.findById(2002).get().getBalance() == 3500.0);

            System.out.println("\n[TEST 5] rejected transfers leave balances untouched:");
            expectFailure("insufficient funds", () -> repo.transfer(2001, 2002, 999999.0));
            check("balances unchanged after insufficient funds",
                repo.findById(2001).get().getBalance() == 7500.0 && repo.findById(2002).get().getBalance() == 3500.0);
            expectFailure("negative amount", () -> repo.transfer(2001, 2002, -1.0));
            expectFailure("same account", () -> repo.transfer(2001, 2001, 10.0));
            expectFailure("inactive source (1001 is closed)", () -> repo.transfer(1001, 2002, 10.0));

            System.out.println("\n[TEST 6] rollback: debit succeeds but credit fails:");
            expectFailure("missing destination", () -> repo.transfer(2001, 4040, 1000.0));
            check("source debit was rolled back", repo.findById(2001).get().getBalance() == 7500.0);
            repo.save(closedAccount());
            expectFailure("inactive destination", () -> repo.transfer(2001, 3001, 1000.0));
            check("source debit rolled back again", repo.findById(2001).get().getBalance() == 7500.0);
        } finally {
            dbFile.delete();
        }

        System.out.println("\n============================================================");
        if (failures == 0) {
            System.out.println("  ALL CHECKS PASSED");
        } else {
            System.out.println("  " + failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    private static IAccount closedAccount() throws Exception {
        Account a = (Account) AccountFactory.createAccount(3001, "Closed", 50, 5000.0, "SALARY");
        a.closeAccount();
        return a;
    }

    private static List<Integer> ids(List<IAccount> accounts) {
        return accounts.stream().map(IAccount::getAccountNumber).toList();
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
            check(label + " -> " + e.getMessage(), true);
        } catch (RuntimeException e) {
            check(label + " (wrong exception " + e + ")", false);
        }
    }
}
