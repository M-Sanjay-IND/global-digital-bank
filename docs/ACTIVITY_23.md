# Activity 23: JdbcAccountRepository (CRUD + Atomic Transfer)

## Overview
Activity 22 gave us a `ConnectionProvider` and the `accounts` / `transactions` schema. This activity builds
`JdbcAccountRepository`: full CRUD over JDBC **PreparedStatements**, `ResultSet` → domain mapping, and an
**atomic transfer** that commits or rolls back as one unit.

## Architecture

```
AccountService ──► AccountRepository (interface)
                     ├─ InMemoryAccountRepository     (persistence.mode=memory)
                     └─ JdbcAccountRepository         (persistence.mode=jdbc)
                           │ uses
                           ▼
                     ConnectionProvider ──► JdbcConnectionProvider ──► SQLite (gdb.db)

RepositoryFactory.getAccountRepository() picks the implementation from persistence.properties.
```

## Files
| File | Purpose |
|---|---|
| `exceptions/DataAccessException.java` | Unchecked wrapper for SQL errors and rejected operations |
| `repository/AccountRepository.java` | Adds `transfer(from, to, amount)` (default: `UnsupportedOperationException`) |
| `repository/JdbcAccountRepository.java` | CRUD, row mapping, `nextAccountNumber()`, atomic `transfer` |
| `repository/RepositoryFactory.java` | Returns `JdbcAccountRepository` when `persistence.mode=jdbc` |
| `tests/TestJdbcAccountRepository.java` | 28 checks on CRUD, transfer and rollback (uses a throwaway temp DB) |

Supporting changes: `schema.sql` gains `tenure_years` (existing databases are upgraded in place by
`SchemaInitializer`), `Account.restoreState(...)` rebuilds persisted state, `IAccount.getPin()` exposes the PIN
to the repository, and `AccountService.openAccount(..., pin)` sets the PIN **before** saving.

## Key ideas

**Row mapping.** `AccountFactory` rebuilds the right subclass from `account_type`. Constructors enforce
*opening* rules (e.g. minimum balance) that a stored balance may legitimately violate (an overdrawn Current
account), so the repository constructs with a safe amount and then calls `restoreState(balance, status, pin, tenure)`.

**Atomic transfer.** Auto-commit is switched off, then:
1. `UPDATE ... SET balance = balance - ? WHERE account_number = ? AND status = 'Active' AND balance >= ?`
   (the funds check and the debit are one statement, so there is no check-then-act race)
2. `UPDATE ... SET balance = balance + ? WHERE account_number = ? AND status = 'Active'`
3. `commit()`; any failure, including "0 rows credited", triggers `rollback()` so the debit disappears.

**Scope of `transfer`.** It is a storage-level primitive: existence, active status, positive amount and funds.
PIN, minimum-balance and daily-limit rules stay in the domain layer (`TransferService`).

## Run
```bash
mkdir -p bin && cp -r src/main/resources/* bin/
javac -encoding UTF-8 -cp "lib/*:." -d bin $(find src -name "*.java")
java --enable-native-access=ALL-UNNAMED -cp "lib/*:bin" com.gdb.tests.TestJdbcAccountRepository
```

## Exercises
1. Make `AccountService.transfer` persist through `AccountRepository.transfer` while keeping PIN and daily-limit checks.
2. Persist `dailyTransferTotal` / `lastTransferDate` so limits survive a restart.
3. Make `InMemoryAccountRepository.transfer` atomic (lock both accounts in a fixed order).
4. Store a hash of the PIN rather than the plain number.
