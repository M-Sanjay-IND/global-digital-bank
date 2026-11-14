package com.gdb.repository;

import com.gdb.db.ConnectionProvider;
import com.gdb.domain.Transaction;
import com.gdb.domain.TransactionType;
import com.gdb.exceptions.DataAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite/JDBC-backed {@link TransactionRepository}.
 *
 * <p>The domain model uses 0 for "no from/to account"; this is stored as SQL NULL and mapped back to 0.
 */
public class JdbcTransactionRepository implements TransactionRepository {
    private static final String INSERT_SQL =
        "INSERT INTO transactions (transaction_id, timestamp, account_number, type, amount, balance_after,"
        + " status, from_account, to_account, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_SQL =
        "SELECT transaction_id, timestamp, account_number, type, amount, balance_after, status,"
        + " from_account, to_account, description FROM transactions";

    private static final String ORDER_BY = " ORDER BY timestamp, rowid";

    private final ConnectionProvider provider;

    public JdbcTransactionRepository(ConnectionProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("ConnectionProvider is required");
        }
        this.provider = provider;
    }

    @Override
    public void save(Transaction transaction) {
        requireTransaction(transaction);
        try (Connection conn = provider.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            bind(ps, transaction);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save transaction " + transaction.getTransactionId() + ": " + e.getMessage(), e);
        }
    }

    /** Inserts the whole batch in one database transaction: if any row fails, none are stored. */
    @Override
    public void saveAll(List<Transaction> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (Transaction t : batch) {
            requireTransaction(t);
        }
        try (Connection conn = provider.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                for (Transaction t : batch) {
                    bind(ps, t);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                throw new DataAccessException("Batch save of " + batch.size() + " transactions failed and was rolled back: " + e.getMessage(), e);
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Batch save failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Transaction> findByAccount(int accountNumber) {
        String sql = SELECT_SQL + " WHERE account_number = ? OR from_account = ? OR to_account = ?" + ORDER_BY;
        try (Connection conn = provider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountNumber);
            ps.setInt(2, accountNumber);
            ps.setInt(3, accountNumber);
            return readAll(ps);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load transactions for account #" + accountNumber + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<Transaction> findAll() {
        try (Connection conn = provider.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL + ORDER_BY)) {
            return readAll(ps);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list transactions: " + e.getMessage(), e);
        }
    }

    @Override
    public void clear() {
        try (Connection conn = provider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM transactions")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to clear transactions: " + e.getMessage(), e);
        }
    }

    private List<Transaction> readAll(PreparedStatement ps) throws SQLException {
        List<Transaction> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    private static void bind(PreparedStatement ps, Transaction t) throws SQLException {
        ps.setString(1, t.getTransactionId());
        ps.setString(2, t.getTimestamp().toString());
        ps.setInt(3, t.getAccountNumber());
        ps.setString(4, t.getType().name());
        ps.setDouble(5, t.getAmount());
        ps.setDouble(6, t.getBalanceAfter());
        ps.setString(7, t.getStatus());
        setNullableAccount(ps, 8, t.getFromAccount());
        setNullableAccount(ps, 9, t.getToAccount());
        ps.setString(10, t.getDescription());
    }

    private static Transaction mapRow(ResultSet rs) throws SQLException {
        return new Transaction(
            rs.getString("transaction_id"),
            LocalDateTime.parse(rs.getString("timestamp")),
            rs.getInt("account_number"),
            TransactionType.valueOf(rs.getString("type")),
            rs.getDouble("amount"),
            rs.getDouble("balance_after"),
            rs.getString("status"),
            rs.getString("description"),
            rs.getInt("from_account"),   // NULL reads as 0, the domain's "none"
            rs.getInt("to_account"));
    }

    private static void setNullableAccount(PreparedStatement ps, int index, int accountNumber) throws SQLException {
        if (accountNumber == 0) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, accountNumber);
        }
    }

    private static void requireTransaction(Transaction t) {
        if (t == null) {
            throw new IllegalArgumentException("Transaction must not be null");
        }
        if (t.getTransactionId() == null || t.getTimestamp() == null || t.getType() == null) {
            throw new IllegalArgumentException("Transaction needs an id, timestamp and type");
        }
    }
}
