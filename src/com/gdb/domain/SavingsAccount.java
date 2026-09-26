package com.gdb.domain;

import com.gdb.exceptions.InactiveAccountException;
import com.gdb.exceptions.InvalidAmountException;
import com.gdb.exceptions.MinimumBalanceViolationException;

public class SavingsAccount extends AbstractAccount {
    private final double minBalance = 1000.0;
    private final double interestRate = 4;

    public SavingsAccount(int accountNumber, String name, int age, double initialBalance, String accountType)
            throws IllegalArgumentException {
        super(accountNumber, name, age, initialBalance, "Savings");
    }

    @Override
    protected void processDebit(double amount) throws MinimumBalanceViolationException {
        if ((super.getBalance() - amount) < this.getMinimumBalance()) {
            throw new MinimumBalanceViolationException("Cannot withdraw. Minimum balance of \u20B9" + this.getMinimumBalance() + " required. Available after withdrawal: \u20B9" + (this.getBalance() - amount));
        }
    }

    public void applyInterest() {
        double interest = this.getBalance() * (this.interestRate / 100);
        try {
            if (this.getBalance() < minBalance) {
                throw new InvalidAmountException("Balance is below the minimum required for interest application.");
            }
            this.deposit(interest, super.getPin());
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

    public double getMinBalance() {
        return this.minBalance;
    }
}
