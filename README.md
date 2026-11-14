# Global Digital Bank (GDB) Engine

An enterprise-grade, modular banking system designed and implemented in Java. GDB models real-world core banking features with strict object-oriented design principles, robust custom exception hierarchies, configurable rule engines, and software design patterns.

---

## 🏛️ Architecture Overview

The system is architected across discrete, decoupled layers:

```
src/com/gdb/
├── Main.java           # Application bootstrapper launching interactive AccountUI
├── command/            # Command Pattern implementation for banking operations
│   ├── DepositCommand.java
│   ├── WithdrawCommand.java
│   ├── TransferCommand.java
│   └── TransactionCommand.java
├── database/           # In-memory simulated persistence engine
│   └── SimulatedDatabase.java
├── db/                 # JDBC Foundation, Connection Providers & Schema Management
│   ├── ConnectionProvider.java
│   ├── DataMigrator.java
│   ├── JdbcConnectionProvider.java
│   └── SchemaInitializer.java
├── domain/             # Core banking entities, account hierarchies & rules engine
│   ├── Account.java
│   ├── AbstractAccount.java
│   ├── AccountFactory.java
│   ├── AccountRulesEngine.java
│   ├── AccountRulesPropertiesLoader.java
│   ├── CurrentAccount.java
│   ├── FixedDepositAccount.java
│   ├── IAccount.java
│   ├── SalaryAccount.java
│   ├── SavingsAccount.java
│   ├── Transaction.java
│   └── TransactionType.java
├── exceptions/         # Domain-specific checked banking exception hierarchy
│   ├── AccountException.java
│   ├── DataAccessException.java
│   ├── InactiveAccountException.java
│   ├── InsufficientBalanceException.java
│   ├── InvalidAmountException.java
│   ├── InvalidPinException.java
│   └── MinimumBalanceViolationException.java
├── logging/            # Bridge Pattern logging backends & file persistence
│   ├── DatabaseLogDestination.java
│   ├── FileLogDestination.java
│   ├── LogDestination.java
│   ├── MemoryLogDestination.java
│   ├── TransactionLog.java
│   └── TransactionLogger.java
├── repository/         # Repository Pattern contracts, factory & in-memory stores
│   ├── AccountRepository.java
│   ├── InMemoryAccountRepository.java
│   ├── JdbcAccountRepository.java
│   ├── JdbcTransactionRepository.java
│   ├── InMemoryTransactionRepository.java
│   ├── RepositoryFactory.java
│   └── TransactionRepository.java
├── service/            # Business workflows and service orchestration
│   ├── AccountService.java
│   └── TransferService.java
├── ui/                 # Menu-driven interactive console presentation layer
│   └── AccountUI.java
└── tests/              # Activity test suites and validation drivers
    ├── TestAbstractAccount.java
    ├── TestAccountEnhanced.java
    ├── TestAccountExceptions.java
    ├── TestAccountRulesEngine.java
    ├── TestAccountRulesEngineProperties.java
    ├── TestAccountService.java
    ├── TestAccountUI.java
    ├── TestBridgeLogging.java
    ├── TestCommandLogging.java
    ├── TestDynamicAccountRules.java
    ├── TestInterfaceFactory.java
    ├── TestJdbcAccountRepository.java
    ├── TestJdbcTransactionRepository.java
    ├── TestJdbcConnection.java
    ├── TestRepositoryInMemory.java
    ├── testSubclassAccounts.java
    ├── TestTransactionModel.java
    └── TestTransfer.java
```

---

## 🚀 Design Patterns & Architectural Highlights

1. **JDBC Foundation & Connection Provider (`ConnectionProvider`, `JdbcConnectionProvider`)**
   - Abstraction of database connection acquisition and lifecycle management.
   - SQLite JDBC integration (`org.sqlite.JDBC`) with automated DDL migration via `SchemaInitializer` and `schema.sql`.

2. **Repository Pattern (`AccountRepository`, `TransactionRepository`)**
   - Clean separation of business logic from underlying data storage mechanisms.
   - Configurable via `persistence.properties` through `RepositoryFactory`.
   - Thread-safe `InMemoryAccountRepository` and `InMemoryTransactionRepository`.
   - `JdbcAccountRepository` (SQLite, PreparedStatements) with CRUD and an atomic, rollback-safe `transfer` (Activity 23). See `docs/ACTIVITY_23.md`.
   - `JdbcTransactionRepository` persists history; `DataMigrator` imports legacy logs idempotently (Activity 24). See `docs/ACTIVITY_24.md`.

3. **Presentation & UI Layer (`AccountUI`)**
   - Menu-driven console interface supporting account opening, deposits, withdrawals, fund transfers, balance lookups, and audit history.
   - Stream-decoupled architecture for seamless unit and integration testing.

4. **Service Orchestration Layer (`AccountService`)**
   - Single point of coordination for banking operations.
   - Coordinates domain models, repository persistence, and transaction logging.

5. **Bridge Pattern (Logging)**
   - **Abstraction**: `TransactionLogger`
   - **Implementor**: `LogDestination`
   - **Concrete Implementors**: `FileLogDestination`, `DatabaseLogDestination`, `MemoryLogDestination`
   - **Benefit**: Pluggable storage backends switchable at runtime without altering client code.

6. **Command Pattern (Transactions)**
   - **Command Interface**: `TransactionCommand` (executable & serializable)
   - **Concrete Commands**: `DepositCommand`, `WithdrawCommand`, `TransferCommand`
   - **Benefit**: Decouples transaction invocation from execution, enabling audit logging and command replay.

7. **Factory Method Pattern (Account Creation & Repositories)**
   - **Factories**: `AccountFactory`, `RepositoryFactory`
   - **Interfaces**: `IAccount`, `AccountRepository`, `TransactionRepository`
   - **Benefit**: Centralizes creation logic driven by configuration.

8. **Singleton Pattern (Rules Engine)**
   - **Singleton**: `AccountRulesEngine`
   - **Benefit**: Centralized, thread-safe access to dynamic banking rules loaded from `.properties` files.

---

## 🛠️ Tech Stack & Prerequisites

- **Language**: Java 17+
- **Database / JDBC**: SQLite JDBC (`sqlite-jdbc-3.44.1.0.jar`)
- **Logging**: SLF4J Simple Runtime
- **Configuration**: Java Properties (`.properties`)
- **Persistence**: Relational SQLite (`gdb.db`), Binary Serialization (`.ser`), In-Memory Store
- **Build / Run Tool**: `javac`, `java`, PowerShell / Bash

---

## ⚙️ Compilation & Execution

### Windows (PowerShell)
```powershell
New-Item bin -ItemType Directory -Force | Out-Null
Copy-Item src\main\resources\* bin -Recurse -Force
javac -encoding UTF-8 -cp "lib/*;." -d bin (Get-ChildItem -Recurse -Filter *.java src).FullName
java "-Dfile.encoding=UTF-8" -cp "lib/*;bin" com.gdb.tests.TestJdbcConnection
```

### Linux & macOS (Bash)
```bash
mkdir -p bin && cp -r src/main/resources/* bin/
javac -encoding UTF-8 -cp "lib/*:." -d bin $(find src -name "*.java")
java -cp "lib/*:bin" com.gdb.tests.TestJdbcConnection
```
