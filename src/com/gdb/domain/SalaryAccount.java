package com.gdb.domain;
public class SalaryAccount extends AbstractAccount {
    private String employerName;
    private int inactiveMonths;

    public SalaryAccount(int accountNumber, String name, int age, double initialBalance, String accountType)
            throws IllegalArgumentException {
        super(accountNumber, name, age, initialBalance, "SALARY");
    }

    @Override
    protected void processDebit(double amount) throws MinimumBalanceViolationException, InsufficientBalanceException, AccountException {
        if(super.getBalance() < amount){
            throw new InsufficientBalanceException("Cannot withdraw. Not enough balance to withdraw.\n");
        }
    }

    void setEmployerName(String employerName) {
        this.employerName = employerName;
    }

    void setInactiveMonths(int inactiveMonths) {
        this.inactiveMonths = inactiveMonths;
    }

    String getEmployerName() {
        return this.employerName;
    }

    void getInactiveMonths() {
        System.out.println("Inactive Months for the Salary Account is: " + this.inactiveMonths);
    }
}
