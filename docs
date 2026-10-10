# Activity 22: JDBC – Connection Management and Schema Initialization

## 📌 Overview
Enterprise banking systems rely on relational database management systems (RDBMS) such as PostgreSQL, Oracle, or SQLite for mission-critical transactional persistence with ACID compliance. 

In this activity, you establish the foundational database infrastructure for Global Digital Bank using Java Database Connectivity (JDBC). You define a `ConnectionProvider` abstraction to isolate connection acquisition, implement `JdbcConnectionProvider` to connect to SQLite databases, build `SchemaInitializer` to execute SQL DDL scripts that create the `accounts` and `transactions` tables, and configure `RepositoryFactory` to wire the database provider.

---

## 🏗️ Architecture Diagram

```
┌────────────────────────────────────────────────────────┐
│                   RepositoryFactory                    │  ← Infrastructure Factory
└──────────────┬──────────────────────────┬──────────────┘
               │                          │
               │ initializes              │ provides
               ▼                          ▼
┌──────────────────────────────┐ ┌──────────────────────────────┐
│      SchemaInitializer       │ │      ConnectionProvider      │  ← Connection Abstraction
│  (executes schema.sql DDL)   │ └──────────────┬───────────────┘
└──────────────────────────────┘                │
                                                ▼
                                 ┌──────────────────────────────┐
                                 │    JdbcConnectionProvider    │  ← SQLite Driver Manager
                                 └──────────────┬───────────────┘
                                                │
                                                ▼
                                         ┌──────────────┐
                                         │ SQLite DB    │
                                         │ (gdb.db)     │
                                         └──────────────┘
```

---

## 📋 Core Components

1. **`ConnectionProvider` Interface**
   - Declares the connection acquisition and lifecycle contract: `getConnection()`, `shutdown()`, and `getProviderName()`.

2. **`JdbcConnectionProvider`**
   - Direct JDBC connection provider dynamically loading SQLite JDBC driver (`org.sqlite.JDBC`) and managing connections via `DriverManager.getConnection(url)`.

3. **`schema.sql` DDL Script**
   - Idempotent table creation script for:
     - `accounts`: `account_number`, `account_holder_name`, `age`, `balance`, `account_type`, `status`, `pin`, `opening_date`, `overdraft_used`, `interest_earned`.
     - `transactions`: `transaction_id`, `timestamp`, `account_number`, `type`, `amount`, `balance_after`, `status`, `from_account`, `to_account`, `description`.

4. **`SchemaInitializer`**
   - Automated startup migration helper executing `schema.sql` statements via JDBC `Statement.execute()`.

5. **`persistence.properties`**
   - Declares:
     ```properties
     persistence.mode=jdbc
     persistence.db.url=jdbc:sqlite:gdb.db
     persistence.db.driver=org.sqlite.JDBC
     ```

---

## ⚙️ Compilation & Execution

### Windows (PowerShell)
```powershell
New-Item bin -ItemType Directory -Force | Out-Null
Copy-Item src\main\resources\* bin -Recurse -Force
javac -encoding UTF-8 -cp "lib/*;." -d bin (Get-ChildItem -Recurse -Filter *.java src).FullName
java -cp "lib/*;bin" com.gdb.tests.TestJdbcConnection
```

### Linux & macOS (Bash)
```bash
mkdir -p bin && cp -r src/main/resources/* bin/
javac -encoding UTF-8 -cp "lib/*:." -d bin $(find src -name "*.java")
java -cp "lib/*:bin" com.gdb.tests.TestJdbcConnection
```
