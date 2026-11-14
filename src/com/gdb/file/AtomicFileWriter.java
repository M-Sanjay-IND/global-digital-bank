package com.gdb.file;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Crash-safe file replacement: content is written to a temporary file in the same directory, forced to disk,
 * then atomically renamed over the target. Readers see either the complete old file or the complete new one,
 * never a half-written file, and a failure part-way leaves the target untouched.
 */
public final class AtomicFileWriter {

    /** Produces the file content; may fail part-way, in which case the target is left unchanged. */
    @FunctionalInterface
    public interface Action {
        void writeTo(OutputStream out) throws IOException;
    }

    private AtomicFileWriter() {
    }

    public static void write(Path target, Action action) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path dir = absolute.getParent();
        Files.createDirectories(dir);
        // Same directory as the target so the final rename never crosses a filesystem boundary.
        Path temp = Files.createTempFile(dir, absolute.getFileName() + ".", ".tmp");
        try {
            try (FileOutputStream fos = new FileOutputStream(temp.toFile());
                 BufferedOutputStream out = new BufferedOutputStream(fos)) {
                action.writeTo(out);
                out.flush();
                fos.getFD().sync();
            }
            try {
                Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    public static void write(Path target, byte[] data) throws IOException {
        write(target, out -> out.write(data));
    }

    public static void writeString(Path target, String content) throws IOException {
        write(target, content.getBytes(StandardCharsets.UTF_8));
    }
}
