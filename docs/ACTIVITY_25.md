# Activity 25: Connection Pooling (NFR)

## Overview
Until now every repository call opened a brand-new database connection through `DriverManager`. This activity
adds **HikariCP** connection pooling: connections are opened once, lent out, and returned, which cuts latency and caps
how many connections the application can hold open.

## Architecture

```
JdbcAccountRepository / JdbcTransactionRepository / SchemaInitializer
                    │ getConnection()
                    ▼
            ConnectionProvider  (interface, unchanged)
              ├─ JdbcConnectionProvider      DriverManager, a new connection per call   (pool.enabled=false)
              └─ HikariConnectionProvider    HikariDataSource, connections reused       (pool.enabled=true)

RepositoryFactory.createConnectionProvider(props) chooses from persistence.properties.
```

The repositories were written against `ConnectionProvider`, so none of them changed: `connection.close()` inside their
try-with-resources now *returns the connection to the pool* instead of closing it.

## Files
| File | Purpose |
|---|---|
| `lib/HikariCP-5.0.1.jar` | The pool (SHA-1 verified against Maven Central) |
| `db/HikariConnectionProvider.java` | `ConnectionProvider` backed by `HikariDataSource`, with pool statistics |
| `config/persistence.properties` | `persistence.pool.*` settings |
| `repository/RepositoryFactory.java` | `createConnectionProvider(Properties)` selects the provider |
| `simplelogger.properties` | Keeps HikariCP's startup logging out of the console UI (warnings still appear) |
| `tests/TestConnectionPooling.java` | 25 checks: lifecycle, reuse, exhaustion, concurrency, factory, benchmark |

## Configuration
```properties
persistence.pool.enabled=true            # false = plain DriverManager connections
persistence.pool.maxSize=10              # hard cap on open connections
persistence.pool.minIdle=2               # connections kept warm
persistence.pool.connectionTimeoutMs=30000   # how long a caller waits when the pool is exhausted
persistence.pool.idleTimeoutMs=600000
persistence.pool.maxLifetimeMs=1800000
persistence.pool.busyTimeoutMs=5000      # SQLite: wait this long for another connection's write lock
```
A non-numeric value fails fast with the property name in the message.

## SQLite note
SQLite permits **one writer at a time**, so a bigger pool does not make writes faster; it lets reads and
waiting writers proceed concurrently. Without `busy_timeout`, a writer that hits a locked database fails immediately with
`SQLITE_BUSY`. The provider sets it on every pooled connection, which is why 200 concurrent transfers in the test all commit.
On a server database (PostgreSQL, MySQL) the same pool would also speed up concurrent writes.

## Benchmark (2000 open → query → close cycles, this machine)
| Provider | Total | Per operation |
|---|---|---|
| `DriverManager` | ~210 ms | ~0.105 ms |
| HikariCP | ~28 ms | ~0.014 ms |

Roughly **8-10x** faster across repeated runs. Absolute numbers will differ on your machine.

## Run
```bash
mkdir -p bin && cp -r src/main/resources/* bin/
javac -encoding UTF-8 -cp "lib/*:." -d bin $(find src -name "*.java")
java --enable-native-access=ALL-UNNAMED -cp "lib/*:bin" com.gdb.tests.TestConnectionPooling
```

## Exercises
1. Set `persistence.pool.maxSize=1` and run the concurrency test; explain what happens to throughput.
2. Remove the `busy_timeout` property in `HikariConnectionProvider` and watch the concurrent-transfer check fail.
3. Call `RepositoryFactory.reset()` at application exit so the pool is closed explicitly.
4. Expose `getActiveConnections()` / `getIdleConnections()` in a small status menu option.
