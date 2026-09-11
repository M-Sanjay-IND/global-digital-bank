package com.gdb.domain;
public class SavingsAccount extends Account {
    private final double minBalance = 1000.0;
    private final double interestRate = 4;

    public SavingsAccount(int accountNumber, String name, int age, double initialBalance, String accountType)
            throws IllegalArgumentException {
        super(accountNumber, name, age, initialBalance, "Savings");
    }

    void applyInterest() {
        double interest = this.getBalance() * (this.interestRate / 100);
        try {
            if (this.getBalance() < minBalance) {
                throw new InvalidAmountException("Balance is below the minimum required for interest application.");
            }
            this.deposit(interest);
        } catch (InactiveAccountException | InvalidAmountException e) {
            System.out.println("Cannot apply interest: " + e.getMessage());
        }
    }

    void getminBalance() {
        System.out.println("Minimum Balance for the Savings Account is: " + this.minBalance);
    }

    void getInterestRate() {
        System.out.println("Interest Rate for the Savings Account is: " + this.interestRate + "%");
    }

    double getMinBalance() {
        return this.minBalance;
    }
    @Override
    public void withdraw(double amount, int pin)
            throws InvalidAmountException,
            InsufficientBalanceException,
            MinimumBalanceViolationException,
            InactiveAccountException,
            InvalidPinException, AccountException {
        if ((super.getBalance() - amount) < this.getMinimumBalance()) {
            throw new MinimumBalanceViolationException("Cannot withdraw. Minimum balance of \u20B9" + this.getMinimumBalance() + " required. Available after withdrawal: \u20B9" + (this.getBalance() - amount));
        }
        super.withdraw(amount, pin);
    }
}
