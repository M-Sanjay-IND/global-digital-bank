# Activity 24: JdbcTransactionRepository & Data Migration

## Overview
Accounts have been persisted since Activity 23. This activity persists the **transaction history** in SQLite with
`JdbcTransactionRepository`, and adds `DataMigrator` to move legacy history (the serialized command log in
`data/transactions.ser`, or any in-memory log) into any `TransactionRepository`.

## Architecture

```
RepositoryFactory.getTransactionRepository()
        ├─ InMemoryTransactionRepository           (persistence.mode=memory)
        └─ JdbcTransactionRepository               (persistence.mode=jdbc) ──► ConnectionProvider ──► SQLite

DataMigrator ── reads ──► LogDestination (File / Memory)  ── Transaction ──► TransactionRepository.saveAll(...)
```

## Files
| File | Purpose |
|---|---|
| `repository/JdbcTransactionRepository.java` | `save`, `saveAll` (atomic batch), `findByAccount`, `findAll`, `clear` |
| `repository/TransactionRepository.java` | Adds `saveAll(List)` (default: loops over `save`) |
| `db/DataMigrator.java` | Idempotent legacy-log → repository migration, plus a command-line `main` |
| `repository/RepositoryFactory.java` | Returns `JdbcTransactionRepository` when `persistence.mode=jdbc` |
| `tests/TestJdbcTransactionRepository.java` | 20 checks on queries, batch atomicity and migration (temp DB + temp log) |

## Key ideas

**Mapping.** `LocalDateTime` is stored as ISO text, `TransactionType` by name. The domain uses `0` for "no
from/to account"; that becomes SQL `NULL` and maps back to `0`.

**Ordering.** Reads return `ORDER BY timestamp, rowid`, so history is chronological even if rows were inserted out of order
(for example by a migration).

**`findByAccount`** matches the owning account **and** transfer sender/receiver, so both parties see a transfer.

**Atomic batch.** `saveAll` inserts with one JDBC batch inside a database transaction; one bad row rolls back the entire batch.

**Idempotent migration.** `DataMigrator` loads the ids already in the target and skips them (and duplicates within the
source), so it can be re-run safely. Commands that never produced a transaction are counted as `skippedEmpty`.
The result record reports `read / migrated / skippedDuplicate / skippedEmpty`.

**Foreign keys.** SQLite does not enforce them by default, so migrated transactions for accounts that only ever
existed in an old in-memory run still load.

## Run
```bash
mkdir -p bin && cp -r src/main/resources/* bin/
javac -encoding UTF-8 -cp "lib/*:." -d bin $(find src -name "*.java")
java --enable-native-access=ALL-UNNAMED -cp "lib/*:bin" com.gdb.tests.TestJdbcTransactionRepository

# migrate the legacy file log into the configured database (safe to repeat)
java --enable-native-access=ALL-UNNAMED -cp "lib/*:bin" com.gdb.db.DataMigrator data/transactions.ser
```

## Exercises
1. Have `AccountService.getTransactionHistory()` stop falling back to the legacy file log once the database is the source of truth.
2. Write the account update and its transaction in one database transaction, so a balance can never change without a history row.
3. Add paging (`LIMIT/OFFSET`) or a date-range query to `JdbcTransactionRepository`.
4. Store amounts as integer paise instead of `REAL`.
