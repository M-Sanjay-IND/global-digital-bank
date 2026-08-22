public class TestAccount {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" GLOBAL DIGITAL BANK - ACCOUNT TEST");
        System.out.println("==================================================");

        System.out.println(">>> 1. Creating Account");
        Account acc1 = new Account(1001, "John Doe", 25, 1000.0, "Savings");
        System.out.println("Account created!");
        printAccountDetails(acc1);

        System.out.println(">>> 2. Deposit Money");
        System.out.println("Depositing \u20B9500.0: " + (acc1.deposit(500.0) ? "SUCCESS\nNew balance: \u20B9" + acc1.getBalance() : "FAILED (Invalid amount)"));
        System.out.println("Depositing \u20B9-100.0: " + (acc1.deposit(-100.0) ? "SUCCESS\nNew balance: \u20B9" + acc1.getBalance() : "FAILED (Invalid amount)"));

        System.out.println(">>> 3. Withdraw Money");
        System.out.println("Withdrawing \u20B9200.0: " + (acc1.withdraw(200.0) ? "SUCCESS\nNew balance: \u20B9" + acc1.getBalance() : "FAILED (Insufficient balance)"));
        System.out.println("Withdrawing \u20B92000.0: " + (acc1.withdraw(2000.0) ? "SUCCESS\nNew balance: \u20B9" + acc1.getBalance() : "FAILED (Insufficient balance)\nCurrent balance: \u20B9" + acc1.getBalance()));

        System.out.println(">>> 4. Creating Another Account");
        Account acc2 = new Account(1002, "Jane Smith", 30, 2000.0, "Current");
        printAccountDetails(acc2);

        System.out.println(">>> 5. All Accounts");
        printAccountDetails(acc1);
        printAccountDetails(acc2);

        System.out.println("==================================================");
        System.out.println(" TEST COMPLETED!");
        System.out.println("==================================================");
    }

    private static void printAccountDetails(Account account) {
        System.out.println("Account #" + account.getAccountNumber() + " | " + account.getName() + " (" + account.getAge() + " yrs) | " + account.getAccountType() + " | \u20B9" + account.getBalance() + " | " + account.getStatus());
    }
}
