package br.com.nfse.renomeador.ledger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProcessingLedger {
    private final Path ledgerFile;
    private final Object lock = new Object();
    private List<LedgerEntry> loadedEntries;
    private Set<String> loadedHashKeys;

    public ProcessingLedger(Path ledgerFile) {
        this.ledgerFile = ledgerFile;
    }

    public void record(LedgerEntry entry) throws IOException {
        synchronized (lock) {
            if (ledgerFile.getParent() != null) {
                Files.createDirectories(ledgerFile.getParent());
            }
            appendLineWithFileLock(entry);
            if (loadedEntries != null) {
                List<LedgerEntry> updatedEntries = new ArrayList<>(loadedEntries);
                updatedEntries.add(entry);
                loadedEntries = List.copyOf(updatedEntries);
            }
            if (loadedHashKeys != null && !entry.sha256().isBlank()) {
                loadedHashKeys.add(hashKey(entry.companyId(), entry.sha256()));
            }
        }
    }

    public boolean hasProcessed(String companyId, Path sourcePath, long size, Instant lastModified, String sha256) throws IOException {
        synchronized (lock) {
            ensureLoaded();
            if (!sha256.isBlank() && loadedHashKeys.contains(hashKey(companyId, sha256))) {
                return true;
            }
            for (LedgerEntry entry : loadedEntries) {
                if (!entry.companyId().equals(companyId)) {
                    continue;
                }
                if (entry.companyId().equals(companyId)
                        && entry.sourcePath().equals(sourcePath)
                        && entry.size() == size
                        && entry.lastModified().equals(lastModified)
                        && entry.sha256().equals(sha256)) {
                    return true;
                }
            }
            return false;
        }
    }

    public List<LedgerEntry> entries() throws IOException {
        synchronized (lock) {
            ensureLoaded();
            return loadedEntries;
        }
    }

    private void ensureLoaded() throws IOException {
        if (loadedEntries != null && loadedHashKeys != null) {
            return;
        }
        List<LedgerEntry> entries = loadEntriesFromDisk();
        Set<String> hashKeys = new HashSet<>();
        for (LedgerEntry entry : entries) {
            if (!entry.sha256().isBlank()) {
                hashKeys.add(hashKey(entry.companyId(), entry.sha256()));
            }
        }
        loadedEntries = List.copyOf(entries);
        loadedHashKeys = hashKeys;
    }

    private List<LedgerEntry> loadEntriesFromDisk() throws IOException {
        if (!Files.exists(ledgerFile)) {
            return List.of();
        }
        List<LedgerEntry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(ledgerFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                entries.add(deserialize(line));
            }
        }
        return entries;
    }

    private void appendLineWithFileLock(LedgerEntry entry) throws IOException {
        try (FileChannel channel = FileChannel.open(ledgerFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            byte[] bytes = (serialize(entry) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            channel.write(ByteBuffer.wrap(bytes));
        }
    }

    private static String hashKey(String companyId, String sha256) {
        return companyId + "\t" + sha256;
    }

    private static String serialize(LedgerEntry entry) {
        return String.join("\t",
                entry.companyId(),
                entry.sourcePath().toString(),
                Long.toString(entry.size()),
                entry.lastModified().toString(),
                entry.sha256(),
                entry.finalStatus(),
                entry.finalDestination().toString(),
                entry.processedAt().toString()
        );
    }

    private static LedgerEntry deserialize(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 8) {
            throw new IllegalArgumentException("Linha invalida no ledger: " + line);
        }
        return new LedgerEntry(
                parts[0],
                Path.of(parts[1]),
                Long.parseLong(parts[2]),
                Instant.parse(parts[3]),
                parts[4],
                parts[5],
                Path.of(parts[6]),
                Instant.parse(parts[7])
        );
    }
}
