package com.gdb.domain;
public class SalaryAccount extends Account {
    private String employerName;
    private int inactiveMonths;

    public SalaryAccount(int accountNumber, String name, int age, double initialBalance, String accountType)
            throws IllegalArgumentException {
        super(accountNumber, name, age, initialBalance, "SALARY");
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
