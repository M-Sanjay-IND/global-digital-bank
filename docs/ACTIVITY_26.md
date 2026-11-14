# Activity 26: File Repository (CSV + Atomic Writes + Backup)

## Overview
Activities 23-25 stored data in SQLite. This activity adds a third storage backend, **plain CSV files**, selected with
`persistence.mode=file`. Because a half-written file means lost money, writes are **atomic** (temp file + rename) and the
previous version is **backed up** before every change.

## Architecture

```
AccountService ──► AccountRepository ──┬─ InMemoryAccountRepository   persistence.mode=memory
               └─► TransactionRepository ├─ Jdbc*Repository             persistence.mode=jdbc
                                         └─ File*Repository             persistence.mode=file
                                                │
                       CsvFile ─ read/validate header ─ CsvMapper (model ⇄ CSV record ⇄ CSV text)
                          │ write
                          ├─► BackupManager     copy current file to  <file>.bak.<timestamp>, prune old ones
                          └─► AtomicFileWriter  write <file>.<random>.tmp → fsync → rename over <file>
```

## Files
| File | Purpose |
|---|---|
| `file/CsvMapper.java` | `Account` / `Transaction` ⇄ CSV records; RFC 4180 quoting and parsing |
| `file/CsvFile.java` | Reads a CSV file (header check) and writes it (backup, then atomic replace) |
| `file/AtomicFileWriter.java` | Temp file + `fsync` + atomic rename; cleans up on failure |
| `file/BackupManager.java` | `<file>.bak.<yyyyMMdd-HHmmssSSS>` copies, `list`, `prune`, `restoreLatest` |
| `repository/FileAccountRepository.java` | CRUD + atomic `transfer` over `accounts.csv` |
| `repository/FileTransactionRepository.java` | `save`, atomic `saveAll`, queries over `transactions.csv` |
| `repository/RepositoryFactory.java` | `persistence.mode=file` support |
| `tests/TestFileRepository.java` | 73 checks, run in a throwaway temp directory |

Supporting change: `AccountFactory.restore(...)` now holds the "rebuild a saved account" logic that
`JdbcAccountRepository` used to keep privately; both backends share it.

## Configuration
```properties
persistence.mode=file
persistence.file.dir=data
persistence.file.accounts=accounts.csv
persistence.file.transactions=transactions.csv
persistence.file.backups.keep=5      # 0 disables backups
```

## File format
```csv
account_number,account_holder_name,age,balance,account_type,status,pin,tenure_years
1001,"Asha, K",30,4250.0,SALARY,Active,1234,0
```
Fields with commas, quotes or line breaks are quoted (quotes doubled). An empty field means "none": no PIN, or
from/to account `0`. A wrong header, wrong column count or unparseable value raises a `DataAccessException`
naming the data row; bad data is **reported, never silently skipped**.

## Why the writes are crash-safe
1. Write the full new content to a temp file **in the same directory** (so the rename cannot cross filesystems).
2. `fsync` the temp file so the bytes are on disk.
3. `rename` over the target with `ATOMIC_MOVE`: readers see the complete old file or the complete new file.
4. If anything fails before step 3, the temp file is deleted and the target is untouched
   (`TestFileRepository` simulates a crash mid-write to prove it).

`transfer` relies on the same property: both balances change in memory and one file replacement commits them together.

## Limitations (good discussion points)
- **One writer.** Operations are synchronized within a repository instance; two instances or processes writing the same file are not coordinated.
- **Whole-file rewrite.** Every change rewrites the file, so cost grows with file size (fine for this scale, wrong for millions of rows).
- **A backup per write**, pruned to `keep`. Cheap insurance here; a real system would back up on a schedule.
- **Plain-text PINs** in `accounts.csv`, as in the database. Files are created owner-only (`rw-------`) because they start life as temp files.
- The directory is not `fsync`-ed after the rename; a power cut in that instant could lose the rename on some filesystems.

## Run
```bash
mkdir -p bin && cp -r src/main/resources/* bin/
javac -encoding UTF-8 -cp "lib/*:." -d bin $(find src -name "*.java")
java --enable-native-access=ALL-UNNAMED -cp "lib/*:bin" com.gdb.tests.TestFileRepository
```
To run the app on CSV files, set `persistence.mode=file` in `src/main/resources/config/persistence.properties`
(the loader prefers that copy over `bin/`), then start `com.gdb.Main`.

## Exercises
1. Restore a corrupted `accounts.csv` from the newest backup with `BackupManager.restoreLatest`, and add a menu option for it.
2. Make `FileAccountRepository` cache parsed accounts and reload only when the file's modification time changes.
3. Add a file lock (`FileChannel.lock`) so two processes cannot write at once.
4. Migrate accounts between backends (JDBC → CSV) the way `DataMigrator` moves transactions.
