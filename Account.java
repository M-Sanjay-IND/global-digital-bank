public class Account {
    private int accountNumber;
    private String name;
    private int age;
    private double balance;
    private String accountType;
    private String status;
    private Integer pin;

    public Account(int accountNumber, String name, int age, int pin, double initialBalance, String accountType) {    
        if(accountType != "Savings" || accountType != "Current"){
          this.accountType = "Savings";
        }else{
          this.accountType = accountType;
        } 
        this.accountNumber = accountNumber;
        this.name = name;
        if(pin < 1000 || pin > 9999){
          this.pin = null;
        }else{
          this.pin = pin;
        }
        if(age < 18){
          this.age = 18;
        }else{
          this.age = age;
        }
        if((accountType == "Savings" && initialBalance < 500)){
          this.balance = 500;
        }else if((accountType == "Current" && initialBalance < 1000)){
          this.balance = 1000;
        }else{
          this.balance = initialBalance;
        }
        this.accountType = accountType;
        this.status = "Active";
    }

    public boolean deposit(double amount) {
        if (amount > 0) {
            this.balance += amount;
            return true;
        }
        return false;
    }

    public boolean withdraw(double amount) {
        if (amount > 0 && amount <= this.balance) {
            if(this.accountType == "Savings" && (this.balance - amount) < 500 ){
              return false;
            }else if(this.accountType == "Current" && (this.balance - amount) < 1000){
              return false;
            }else{
              this.balance -= amount;
              return true;
            }
        }
        return false;
    }

    public int getAccountNumber() {
        return this.accountNumber;
    }

    public String getName() {
        return this.name;
    }

    public int getAge() {
        return this.age;
    }

    public double getBalance() {
        return this.balance;
    }

    public String getAccountType() {
        return this.accountType;
    }

    public String getStatus() {
        return this.status;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public boolean closeAccount(){
      if(this.status == "Active"){
        this.status == "Inactive";
        return true;
      }
      return false;
    }

    
}
