package com.gdb.file;

import com.gdb.domain.AccountFactory;
import com.gdb.domain.IAccount;
import com.gdb.domain.Transaction;
import com.gdb.domain.TransactionType;
import com.gdb.exceptions.DataAccessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts {@link IAccount} and {@link Transaction} to and from CSV records, and reads/writes the CSV text itself
 * (RFC 4180 quoting: fields containing commas, quotes or line breaks are quoted, quotes are doubled).
 * Empty fields stand for "none": no PIN, or from/to account 0.
 */
public final class CsvMapper {
    public static final List<String> ACCOUNT_HEADER = List.of(
        "account_number", "account_holder_name", "age", "balance", "account_type", "status", "pin", "tenure_years");

    public static final List<String> TRANSACTION_HEADER = List.of(
        "transaction_id", "timestamp", "account_number", "type", "amount", "balance_after", "status",
        "from_account", "to_account", "description");

    private CsvMapper() {
    }

    // ---- accounts ----

    public static List<String> toRow(IAccount a) {
        return List.of(
            String.valueOf(a.getAccountNumber()), a.getName(), String.valueOf(a.getAge()),
            String.valueOf(a.getBalance()), a.getAccountType(), a.getStatus(),
            a.getPin() == null ? "" : String.valueOf(a.getPin()), String.valueOf(a.getTenureYears()));
    }

    public static IAccount toAccount(List<String> row) {
        requireColumns(row, ACCOUNT_HEADER);
        try {
            String pin = row.get(6);
            return AccountFactory.restore(
                Integer.parseInt(row.get(0)), row.get(1), Integer.parseInt(row.get(2)),
                Double.parseDouble(row.get(3)), row.get(4), row.get(5),
                pin.isEmpty() ? null : Integer.valueOf(pin), Integer.parseInt(row.get(7)));
        } catch (IllegalArgumentException e) { // includes NumberFormatException and constructor rule violations
            throw new DataAccessException("Invalid account record " + row + ": " + e.getMessage(), e);
        }
    }

    // ---- transactions ----

    public static List<String> toRow(Transaction t) {
        return List.of(
            t.getTransactionId(), t.getTimestamp().toString(), String.valueOf(t.getAccountNumber()),
            t.getType().name(), String.valueOf(t.getAmount()), String.valueOf(t.getBalanceAfter()),
            nullToEmpty(t.getStatus()), t.getFromAccount() == 0 ? "" : String.valueOf(t.getFromAccount()),
            t.getToAccount() == 0 ? "" : String.valueOf(t.getToAccount()), nullToEmpty(t.getDescription()));
    }

    public static Transaction toTransaction(List<String> row) {
        requireColumns(row, TRANSACTION_HEADER);
        try {
            return new Transaction(
                row.get(0), LocalDateTime.parse(row.get(1)), Integer.parseInt(row.get(2)),
                TransactionType.valueOf(row.get(3)), Double.parseDouble(row.get(4)), Double.parseDouble(row.get(5)),
                row.get(6), row.get(9),
                row.get(7).isEmpty() ? 0 : Integer.parseInt(row.get(7)),
                row.get(8).isEmpty() ? 0 : Integer.parseInt(row.get(8)));
        } catch (RuntimeException e) { // NumberFormat, DateTimeParse, IllegalArgument (unknown type)
            throw new DataAccessException("Invalid transaction record " + row + ": " + e.getMessage(), e);
        }
    }

    // ---- CSV text ----

    /** Renders records as CSV text, one record per line. */
    public static String format(List<List<String>> records) {
        StringBuilder sb = new StringBuilder();
        for (List<String> record : records) {
            for (int i = 0; i < record.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(quote(record.get(i)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Parses CSV text into records. Blank lines are ignored; malformed quoting raises {@link DataAccessException}. */
    public static List<List<String>> parse(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean wasQuoted = false; // the current field began with a quote
        boolean recordHasContent = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        char next = i + 1 < text.length() ? text.charAt(i + 1) : '\n';
                        if (next != ',' && next != '\n' && next != '\r') {
                            throw new DataAccessException("Malformed CSV: unexpected text after closing quote in record " + (records.size() + 1));
                        }
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    if (field.length() > 0 || wasQuoted) {
                        throw new DataAccessException("Malformed CSV: stray quote in record " + (records.size() + 1));
                    }
                    inQuotes = true;
                    wasQuoted = true;
                    recordHasContent = true;
                }
                case ',' -> {
                    fields.add(field.toString());
                    field.setLength(0);
                    wasQuoted = false;
                    recordHasContent = true;
                }
                case '\r' -> { /* handled with the following \n (or ignored if alone) */ }
                case '\n' -> {
                    if (recordHasContent || field.length() > 0) {
                        fields.add(field.toString());
                        records.add(fields);
                        fields = new ArrayList<>();
                    }
                    field.setLength(0);
                    wasQuoted = false;
                    recordHasContent = false;
                }
                default -> {
                    field.append(c);
                    recordHasContent = true;
                }
            }
        }
        if (inQuotes) {
            throw new DataAccessException("Malformed CSV: unterminated quoted field in record " + (records.size() + 1));
        }
        if (recordHasContent || field.length() > 0) { // last line without trailing newline
            fields.add(field.toString());
            records.add(fields);
        }
        return records;
    }

    private static String quote(String value) {
        String v = value == null ? "" : value;
        boolean needsQuotes = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        return needsQuotes ? '"' + v.replace("\"", "\"\"") + '"' : v;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static void requireColumns(List<String> row, List<String> header) {
        if (row.size() != header.size()) {
            throw new DataAccessException("Expected " + header.size() + " columns but found " + row.size() + ": " + row);
        }
    }
}
