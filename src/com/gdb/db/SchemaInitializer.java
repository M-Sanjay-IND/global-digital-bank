package com.gdb.db;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SchemaInitializer {

    public static void initialize(ConnectionProvider provider) {
        String sql = readSchemaSql();
        if (sql == null || sql.trim().isEmpty()) {
            return;
        }

        try (Connection conn = provider.getConnection();
             Statement stmt = conn.createStatement()) {
            String[] statements = sql.split(";");
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
            migrateAccountsTable(stmt);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema: " + e.getMessage(), e);
        }
    }

    /** Databases created by Activity 22 lack tenure_years; add it in place. */
    private static void migrateAccountsTable(Statement stmt) throws SQLException {
        boolean hasTenure = false;
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(accounts)")) {
            while (rs.next()) {
                if ("tenure_years".equalsIgnoreCase(rs.getString("name"))) {
                    hasTenure = true;
                }
            }
        }
        if (!hasTenure) {
            stmt.execute("ALTER TABLE accounts ADD COLUMN tenure_years INTEGER NOT NULL DEFAULT 0");
        }
    }

    public static String readSchemaSql() {
        String[] paths = {
            "src/main/resources/schema.sql",
            "schema.sql",
            "bin/schema.sql"
        };

        for (String path : paths) {
            File file = new File(path);
            if (file.exists()) {
                try (InputStream is = new FileInputStream(file)) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ignored) {
                }
            }
        }

        try (InputStream is = SchemaInitializer.class.getResourceAsStream("/schema.sql")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
        }

        return "";
    }
}
