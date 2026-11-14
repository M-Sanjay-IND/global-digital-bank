package com.gdb.domain;

public class AccountFactory {
    public static IAccount createAccount(int accountNumber, String name, int age, double initialBalance,
            String accountType, int tenureYears) {
        if (accountType == null) {
            throw new IllegalArgumentException("Account type cannot be null");
        }
        return switch (accountType.toUpperCase()) {
            case "SAVINGS" -> new SavingsAccount(accountNumber, name, age, initialBalance, "Savings", tenureYears);
            case "CURRENT" -> new CurrentAccount(accountNumber, name, age, initialBalance, "Current");
            case "SALARY" -> new SalaryAccount(accountNumber, name, age, initialBalance, "Salary");
            case "FIXED_DEPOSIT" -> new FixedDepositAccount(accountNumber, name, age, initialBalance, "FIXED_DEPOSIT");
            default -> throw new IllegalArgumentException("Unexpected value: " + accountType);
        };
    }

    public static IAccount createAccount(int accountNumber, String name, int age, double initialBalance,
            String accountType) {
        return createAccount(accountNumber, name, age, initialBalance, accountType, 0);
    }

    /**
     * Rebuilds an account from persisted state. Constructors enforce opening-balance rules that a stored
     * balance (e.g. an overdrawn Current account) may legitimately violate, so the account is built with a
     * safe amount and the real balance, status, PIN and tenure are then restored.
     */
    public static IAccount restore(int accountNumber, String name, int age, double balance, String accountType,
            String status, Integer pin, int tenureYears) {
        IAccount account = createAccount(accountNumber, name, age, Math.max(balance, 1000.0), accountType, tenureYears);
        ((Account) account).restoreState(balance, status, pin, tenureYears);
        return account;
    }
}
