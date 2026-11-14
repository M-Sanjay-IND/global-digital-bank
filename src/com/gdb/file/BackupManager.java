package com.gdb.file;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Keeps timestamped copies of a file next to it ({@code accounts.csv.bak.20261114-093000123}) and prunes the
 * oldest ones. The timestamp is fixed-width, so sorting names sorts backups chronologically.
 */
public class BackupManager {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");
    private static final String INFIX = ".bak.";

    private final int keep;

    /** @param keep how many backups to retain per file (at least 1) */
    public BackupManager(int keep) {
        if (keep < 1) {
            throw new IllegalArgumentException("Must keep at least one backup. Provided: " + keep);
        }
        this.keep = keep;
    }

    public int getKeep() {
        return keep;
    }

    /** Copies {@code file} to a new timestamped backup and prunes old ones. Empty if there is nothing to back up. */
    public Optional<Path> backup(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Path dir = file.toAbsolutePath().getParent();
        LocalDateTime stamp = LocalDateTime.now();
        Path backup = dir.resolve(file.getFileName() + INFIX + TIMESTAMP.format(stamp));
        while (Files.exists(backup)) { // two backups in the same millisecond must not overwrite each other
            stamp = stamp.plusNanos(1_000_000);
            backup = dir.resolve(file.getFileName() + INFIX + TIMESTAMP.format(stamp));
        }
        AtomicFileWriter.write(backup, Files.readAllBytes(file)); // atomic, so a crash never leaves a partial "latest" backup
        prune(file);
        return Optional.of(backup);
    }

    /** All backups of {@code file}, oldest first. */
    public List<Path> list(Path file) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        String prefix = file.getFileName() + INFIX;
        List<Path> backups = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return backups;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                if (p.getFileName().toString().startsWith(prefix)) {
                    backups.add(p);
                }
            }
        }
        Collections.sort(backups);
        return backups;
    }

    /** Deletes the oldest backups so that at most {@code keep} remain. */
    public void prune(Path file) throws IOException {
        List<Path> backups = list(file);
        for (int i = 0; i < backups.size() - keep; i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }

    /** Replaces {@code file} with its newest backup. Empty if there is no backup. */
    public Optional<Path> restoreLatest(Path file) throws IOException {
        List<Path> backups = list(file);
        if (backups.isEmpty()) {
            return Optional.empty();
        }
        Path latest = backups.get(backups.size() - 1);
        AtomicFileWriter.write(file, Files.readAllBytes(latest));
        return Optional.of(latest);
    }
}
