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
        } catch (InactiveAccountException e) {
            System.out.println("Cannot apply interest: " + e.getMessage());
        } catch (InvalidAmountException e) {
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
}
