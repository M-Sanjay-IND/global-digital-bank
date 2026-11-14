package com.gdb.file;

import com.gdb.exceptions.DataAccessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes whole CSV files: validated header, atomic replacement, optional backup of the previous version. */
public final class CsvFile {
    private CsvFile() {
    }

    /** Data records of {@code file} (header excluded). A missing or empty file has no records. */
    public static List<List<String>> read(Path file, List<String> expectedHeader) {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        List<List<String>> records;
        try {
            records = CsvMapper.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DataAccessException("Failed to read " + file + ": " + e.getMessage(), e);
        }
        if (records.isEmpty()) {
            return records;
        }
        if (!records.get(0).equals(expectedHeader)) {
            throw new DataAccessException("Unexpected header in " + file + ": " + records.get(0));
        }
        return new ArrayList<>(records.subList(1, records.size()));
    }

    /** Backs up the current file (if a manager is given), then atomically replaces it with header + rows. */
    public static void write(Path file, List<String> header, List<List<String>> rows, BackupManager backups) {
        List<List<String>> all = new ArrayList<>(rows.size() + 1);
        all.add(header);
        all.addAll(rows);
        try {
            if (backups != null) {
                backups.backup(file);
            }
            AtomicFileWriter.writeString(file, CsvMapper.format(all));
        } catch (IOException e) {
            throw new DataAccessException("Failed to write " + file + ": " + e.getMessage(), e);
        }
    }
}
