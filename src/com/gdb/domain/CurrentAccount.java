package com.gdb.domain;
public class CurrentAccount extends Account {
    private double overDraftLimit = 25000;

    public CurrentAccount(int accountNumber, String name, int age, double initialBalance, String accountType)
            throws IllegalArgumentException {
        super(accountNumber, name, age, initialBalance, "Current");
    }

    double getOverDraftLimit() {
        return this.overDraftLimit;
    }

    void setOverDraftLimit(double newLimit) {
        if (newLimit >= 0) {
            this.overDraftLimit = newLimit;
        } else {
            System.out.println("Overdraft limit cannot be negative.");
        }
    }
}
