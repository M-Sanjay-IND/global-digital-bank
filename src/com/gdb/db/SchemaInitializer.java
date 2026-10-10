package com.gdb.db;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema: " + e.getMessage(), e);
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
