package br.com.nfse.renomeador.ledger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ProcessingLedger {
    private final Path ledgerFile;

    public ProcessingLedger(Path ledgerFile) {
        this.ledgerFile = ledgerFile;
    }

    public void record(LedgerEntry entry) throws IOException {
        if (ledgerFile.getParent() != null) {
            Files.createDirectories(ledgerFile.getParent());
        }
        Files.writeString(
                ledgerFile,
                serialize(entry) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    public boolean hasProcessed(String companyId, Path sourcePath, long size, Instant lastModified, String sha256) throws IOException {
        for (LedgerEntry entry : entries()) {
            if (!entry.companyId().equals(companyId)) {
                continue;
            }
            if (!sha256.isBlank() && entry.sha256().equals(sha256)) {
                return true;
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

    public List<LedgerEntry> entries() throws IOException {
        if (!Files.exists(ledgerFile)) {
            return List.of();
        }
        List<LedgerEntry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(ledgerFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                entries.add(deserialize(line));
            }
        }
        return List.copyOf(entries);
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
