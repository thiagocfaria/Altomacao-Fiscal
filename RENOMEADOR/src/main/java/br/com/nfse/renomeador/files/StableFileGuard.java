package br.com.nfse.renomeador.files;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;

public final class StableFileGuard {
    public boolean isStable(Path file, Duration interval, int checks) {
        if (checks < 2) {
            throw new IllegalArgumentException("checks deve ser no minimo 2");
        }
        try {
            Snapshot previous = snapshot(file);
            if (previous == null) {
                return false;
            }
            for (int i = 1; i < checks; i++) {
                sleep(interval);
                Snapshot current = snapshot(file);
                if (current == null || !current.equals(previous)) {
                    return false;
                }
                previous = current;
            }
            return canOpenForRead(file);
        } catch (IOException exception) {
            return false;
        }
    }

    private static Snapshot snapshot(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return new Snapshot(Files.size(file), Files.getLastModifiedTime(file).toInstant());
    }

    private static boolean canOpenForRead(Path file) {
        try (FileChannel ignored = FileChannel.open(file, StandardOpenOption.READ)) {
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void sleep(Duration interval) {
        if (interval.isZero() || interval.isNegative()) {
            return;
        }
        try {
            Thread.sleep(interval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record Snapshot(long size, Instant lastModified) {
    }
}
