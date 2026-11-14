package com.gdb.repository;

import com.gdb.db.ConnectionProvider;
import com.gdb.domain.Account;
import com.gdb.domain.AccountFactory;
import com.gdb.domain.IAccount;
import com.gdb.exceptions.DataAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite/JDBC-backed {@link AccountRepository}. All SQL uses PreparedStatements. */
public class JdbcAccountRepository implements AccountRepository {
    private static final int FIRST_ACCOUNT_NUMBER = 1001;

    private static final String COLUMNS =
        "account_number, account_holder_name, age, balance, account_type, status, pin, tenure_years";

    private static final String INSERT_SQL =
        "INSERT INTO accounts (account_number, account_holder_name, age, balance, account_type, status, pin,"
        + " opening_date, overdraft_used, interest_earned, tenure_years) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)";

    private static final String UPDATE_SQL =
        "UPDATE accounts SET account_holder_name = ?, age = ?, balance = ?, account_type = ?, status = ?,"
        + " pin = ?, overdraft_used = ?, tenure_years = ? WHERE account_number = ?";

    private final ConnectionProvider provider;
    private int lastIssuedNumber = 0;

    public JdbcAccountRepository(ConnectionProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("ConnectionProvider is required");
        }
        this.provider = provider;
    }

    @Override
    public void save(IAccount account) {
        requireAccount(account);
        try (Connection conn = provider.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            ps.setInt(1, account.getAccountNumber());
            ps.setString(2, account.getName());
            ps.setInt(3, account.getAge());
            ps.setDouble(4, account.getBalance());
            ps.setString(5, account.getAccountType());
            ps.setString(6, account.getStatus());
            setNullableInt(ps, 7, account.getPin());
            ps.setString(8, LocalDate.now().toString());
            ps.setDouble(9, overdraftUsed(account));
            ps.setInt(10, account.getTenureYears());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save account #" + account.getAccountNumber() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<IAccount> findById(int accountNumber) {
        String sql = "SELECT " + COLUMNS + " FROM accounts WHERE account_number = ?";
        try (Connection conn = provider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load account #" + accountNumber + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<IAccount> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM accounts ORDER BY account_number";
        List<IAccount> accounts = new ArrayList<>();
        try (Connection conn = provider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                accounts.add(mapRow(rs));
            }
            return accounts;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list accounts: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(IAccount account) {
        requireAccount(account);
        try (Connection conn = provider.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            ps.setString(1, account.getName());
            ps.setInt(2, account.getAge());
            ps.setDouble(3, account.getBalance());
            ps.setString(4, account.getAccountType());
            ps.setString(5, account.getStatus());
            setNullableInt(ps, 6, account.getPin());
            ps.setDouble(7, overdraftUsed(account));
            ps.setInt(8, account.getTenureYears());
            ps.setInt(9, account.getAccountNumber());
            if (ps.executeUpdate() == 0) {
                throw new DataAccessException("Cannot update: account #" + account.getAccountNumber() + " does not exist");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update account #" + account.getAccountNumber() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(int accountNumber) {
        try (Connection conn = provider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM accounts WHERE account_number = ?")) {
            ps.setInt(1, accountNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete account #" + accountNumber + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(int accountNumber) {
        try (Connection conn = provider.getConnection()) {
            return exists(conn, accountNumber);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check account #" + accountNumber + ": " + e.getMessage(), e);
        }
    }

    /** Next free number: above both the highest stored number and any number already handed out by this instance. */
    @Override
    public synchronized int nextAccountNumber() {
        try (Connection conn = provider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT MAX(account_number) FROM accounts");
             ResultSet rs = ps.executeQuery()) {
            int max = rs.next() ? rs.getInt(1) : 0;
            lastIssuedNumber = Math.max(Math.max(max, lastIssuedNumber) + 1, FIRST_ACCOUNT_NUMBER);
            return lastIssuedNumber;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to compute next account number: " + e.getMessage(), e);
        }
    }

    @Override
    public void transfer(int fromAccountNumber, int toAccountNumber, double amount) {
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new DataAccessException("Transfer amount must be positive. Provided: " + amount);
        }
        if (fromAccountNumber == toAccountNumber) {
            throw new DataAccessException("Cannot transfer to the same account (#" + fromAccountNumber + ")");
        }

        try (Connection conn = provider.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // The balance/status checks live in the WHERE clause so check-and-debit is one atomic statement.
                int debited = adjustBalance(conn, fromAccountNumber, -amount, true);
                if (debited == 0) {
                    throw new DataAccessException(describeDebitFailure(conn, fromAccountNumber, amount));
                }
                int credited = adjustBalance(conn, toAccountNumber, amount, false);
                if (credited == 0) {
                    throw new DataAccessException(exists(conn, toAccountNumber)
                        ? "Destination account #" + toAccountNumber + " is not active"
                        : "Destination account #" + toAccountNumber + " not found");
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(conn);
                if (e instanceof DataAccessException dae) {
                    throw dae;
                }
                throw new DataAccessException("Transfer failed and was rolled back: " + e.getMessage(), e);
            } finally {
                try {
                    conn.setAutoCommit(previousAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Transfer failed: " + e.getMessage(), e);
        }
    }

    private int adjustBalance(Connection conn, int accountNumber, double delta, boolean requireFunds) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance + ? WHERE account_number = ? AND status = 'Active'"
            + (requireFunds ? " AND balance >= ?" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, delta);
            ps.setInt(2, accountNumber);
            if (requireFunds) {
                ps.setDouble(3, -delta);
            }
            return ps.executeUpdate();
        }
    }

    private String describeDebitFailure(Connection conn, int accountNumber, double amount) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT status, balance FROM accounts WHERE account_number = ?")) {
            ps.setInt(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "Source account #" + accountNumber + " not found";
                }
                if (!"Active".equals(rs.getString("status"))) {
                    return "Source account #" + accountNumber + " is not active";
                }
                return "Insufficient balance in account #" + accountNumber
                    + ". Available: " + rs.getDouble("balance") + ", Requested: " + amount;
            }
        }
    }

    private boolean exists(Connection conn, int accountNumber) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM accounts WHERE account_number = ?")) {
            ps.setInt(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
    }

    private IAccount mapRow(ResultSet rs) throws SQLException {
        int number = rs.getInt("account_number");
        double balance = rs.getDouble("balance");
        int tenure = rs.getInt("tenure_years");
        int pinValue = rs.getInt("pin");
        Integer pin = rs.wasNull() ? null : pinValue;

        // Constructors enforce opening-balance rules that a stored balance (e.g. an overdrawn
        // Current account) may legitimately violate, so build with a safe amount and restore the real state.
        IAccount account = AccountFactory.createAccount(
            number, rs.getString("account_holder_name"), rs.getInt("age"),
            Math.max(balance, 1000.0), rs.getString("account_type"), tenure);
        if (!(account instanceof Account concrete)) {
            throw new DataAccessException("Unsupported account implementation for #" + number);
        }
        concrete.restoreState(balance, rs.getString("status"), pin, tenure);
        return concrete;
    }

    private static double overdraftUsed(IAccount account) {
        return "Current".equalsIgnoreCase(account.getAccountType()) ? Math.max(0.0, -account.getBalance()) : 0.0;
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void requireAccount(IAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("Account must not be null");
        }
    }
}
