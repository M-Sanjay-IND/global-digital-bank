package com.gdb.domain;

abstract class AbstractAccount {
    private static final double MIN_BALANCE_SAVINGS = 500.0;
    private static final double MIN_BALANCE_CURRENT = 1000.0;
    private static final int MIN_AGE = 18;
    private static final int MIN_PIN = 1000;
    private static final int MAX_PIN = 9999;

    private int accountNumber;
    private String name;
    private int age;
    private double balance;
    private String accountType;
    private String status;
    private Integer pin;

    public AbstractAccount(int accountNumber, String name, int age,
                   
                   double initialBalance, String accountType)
            throws IllegalArgumentException {
        if (age < MIN_AGE) {
            throw new IllegalArgumentException("Customer must be at least 18 years old. Provided: " + age);
        }
        if (!"Savings".equals(accountType) && !"Current".equals(accountType) && !"FIXED_DEPOSIT".equals(accountType) && !"SALARY".equals(accountType)) {
            throw new IllegalArgumentException("Account type must be 'Savings' or 'Current' or 'FIXED_DEPOSIT' or 'SALARY'. Provided: " + accountType);
        }
        double minBalance = "Savings".equals(accountType) ? MIN_BALANCE_SAVINGS : MIN_BALANCE_CURRENT;
        if (initialBalance < minBalance) {
            throw new IllegalArgumentException(accountType + " account requires minimum balance of \u20B9" + minBalance + ". Provided: \u20B9" + initialBalance);
        }

        this.accountNumber = accountNumber;
        this.name = name;
        this.age = age;
        this.balance = initialBalance;
        this.accountType = accountType;
        this.status = "Active";
        this.pin = null;
    }

    public final void deposit(double amount)
            throws InvalidAmountException, InactiveAccountException {
        validateActive();
        if (amount <= 0) {
            throw new InvalidAmountException("Deposit amount must be positive. Provided: \u20B9" + amount);
        }
        this.balance += amount;
    }

    public final void withdraw(double amount, int pin)
            throws InvalidAmountException,
            InsufficientBalanceException,
            MinimumBalanceViolationException,
            InactiveAccountException,
            InvalidPinException, AccountException {
        validateActive();
        if (getPin() == null) {
            throw new InvalidPinException("PIN not set for this account");
        }
        if (!verifyPin(pin)) {
            throw new InvalidPinException("Incorrect PIN");
        }
        if (amount <= 0) {
            throw new InvalidAmountException("Withdraw amount must be positive. Provided: \u20B9" + amount);
        }
        if (amount > getBalance()) {
            throw new InsufficientBalanceException("Insufficient balance. Available: \u20B9" + getBalance() + ", Requested: \u20B9" + amount);
        }
        if ((this.balance - amount) < getMinimumBalance()) {
            throw new MinimumBalanceViolationException("Cannot withdraw. Minimum balance of \u20B9" + getMinimumBalance() + " required. Available after withdrawal: \u20B9" + (getBalance() - amount));
        }
        processDebit(amount);
        this.balance -= amount;
    }

    public final void closeAccount() throws IllegalStateException {
        if ("Inactive".equals(this.status)) {
            throw new IllegalStateException("Account is already closed");
        }
        this.status = "Inactive";
    }

    public final void reopenAccount() throws IllegalStateException {
        if ("Active".equals(this.status)) {
            throw new IllegalStateException("Account is already active");
        }
        this.status = "Active";
    }

    public final void setPin(int pin) throws IllegalArgumentException {
        if (pin < MIN_PIN || pin > MAX_PIN) {
            throw new IllegalArgumentException("PIN must be a 4-digit number (1000-9999). Provided: " + pin);
        }
        this.pin = pin;
    }

    public final boolean verifyPin(int pin) {
        return this.pin != null && this.pin == pin;
    }

    public final boolean hasPin() {
        return this.pin != null;
    }

    double getMinimumBalance() {
        if ("Savings".equals(this.accountType)) {
            return MIN_BALANCE_SAVINGS;
        } else if ("Current".equals(this.accountType)) {
            return MIN_BALANCE_CURRENT;
        }
        return 0.0;
    }

    public final void validateActive() throws InactiveAccountException {
        if (!"Active".equals(this.status)) {
            throw new InactiveAccountException("Account is inactive. Please reopen the account or contact support.");
        }
    }

    public final int getAccountNumber() {
        return accountNumber;
    }

    public final String getName() {
        return name;
    }

    public final int getAge() {
        return age;
    }

    public final double getBalance() {
        return balance;
    }

    public final String getAccountType() {
        return accountType;
    }

    public final String getStatus() {
        return status;
    }

    public final Integer getPin() {
        return pin;
    }

    @Override
    public final String toString() {
        return "Account #" + accountNumber + " | " + name + " (" + age + " yrs) | " + accountType + " | \u20B9" + balance + " | " + status + " | PIN: " + (hasPin() ? "Yes" : "No");
    }

    abstract protected void processDebit(double amount) throws MinimumBalanceViolationException, InsufficientBalanceException, AccountException;
}
