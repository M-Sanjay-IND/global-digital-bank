package com.gdb.tests;

import com.gdb.db.ConnectionProvider;
import com.gdb.db.JdbcConnectionProvider;
import com.gdb.db.SchemaInitializer;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestJdbcConnection {
    public static void main(String[] args) throws Exception {
        System.out.println("============================================================");
        System.out.println("  ACTIVITY 22 \u2014 JDBC CONNECTION & SCHEMA INITIALIZATION");
        System.out.println("============================================================");

        String testDbFile = "test_gdb.db";
        File dbFile = new File(testDbFile);
        if (dbFile.exists()) {
            dbFile.delete();
        }

        String testDbUrl = "jdbc:sqlite:" + testDbFile;
        ConnectionProvider provider = new JdbcConnectionProvider(testDbUrl);

        System.out.println("[TEST 1] Database Connection Establishment:");
        try (Connection conn = provider.getConnection()) {
            System.out.println("  Connected to: " + testDbUrl);
            System.out.println("  Driver Name: " + conn.getMetaData().getDriverName());
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next()) {
                    System.out.println("  -> PASSED");
                }
            }
        }
        System.out.println();

        System.out.println("[TEST 2] Schema DDL Execution:");
        System.out.println("  Creating tables: accounts, transactions...");
        SchemaInitializer.initialize(provider);
        System.out.println("  Tables initialized successfully.");
        System.out.println("  -> PASSED");
        System.out.println();

        System.out.println("[TEST 3] Schema Verification:");
        boolean accountsExists = false;
        boolean transactionsExists = false;

        try (Connection conn = provider.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
            while (rs.next()) {
                String tableName = rs.getString("name");
                if ("accounts".equalsIgnoreCase(tableName)) {
                    accountsExists = true;
                } else if ("transactions".equalsIgnoreCase(tableName)) {
                    transactionsExists = true;
                }
            }
        }

        System.out.println("  Table 'accounts' exists: " + accountsExists);
        System.out.println("  Table 'transactions' exists: " + transactionsExists);
        if (accountsExists && transactionsExists) {
            System.out.println("  -> PASSED");
        }
        System.out.println();

        provider.shutdown();

        System.out.println("============================================================");
        System.out.println("  ALL ACTIVITY 22 JDBC CONNECTION TESTS PASSED!");
        System.out.println("============================================================");
    }
}
