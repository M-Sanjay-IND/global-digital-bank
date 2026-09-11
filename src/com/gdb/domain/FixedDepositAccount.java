package com.gdb.domain;
public class FixedDepositAccount extends Account {
    private int tenureMonths = 12;
    private double interestRate = 6.5;

    FixedDepositAccount(int accountNumber, String name, int age, double initialBalance, String accountType)
            throws IllegalArgumentException {
        super(accountNumber, name, age, initialBalance, "FIXED_DEPOSIT");
    }

    double calculateMaturityAmount() {
        double principal = this.getBalance();
        double rate = this.interestRate / 100;
        double time = (double) this.tenureMonths / 12;
        return principal * Math.pow((1 + rate), time);

    }
    double getTenureMonths() {
        return this.tenureMonths;
    }

    double getInterestRate() {
        return this.interestRate;
    }

    @Override
    public void withdraw(double amount, int pin)
            throws InvalidAmountException,
            InsufficientBalanceException,
            MinimumBalanceViolationException,
            InactiveAccountException,
            InvalidPinException,AccountException {
        throw new AccountException("Pre-mature account cannot withdraw\n");
    }
}
