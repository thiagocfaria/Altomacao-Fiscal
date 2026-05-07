package br.com.nfse.renomeador.ledger;

import br.com.nfse.renomeador.layout.LayoutType;

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
import java.util.List;
import java.util.Optional;

public final class DuplicateInvoiceIndex {
    private final Path indexFile;
    private final Object lock = new Object();
    private List<Entry> loadedEntries;

    public DuplicateInvoiceIndex(Path indexFile) {
        this.indexFile = indexFile;
    }

    public void record(String companyId, String fiscalKey, LayoutType layout, Path destination) throws IOException {
        synchronized (lock) {
            if (indexFile.getParent() != null) {
                Files.createDirectories(indexFile.getParent());
            }
            Entry entry = new Entry(companyId, fiscalKey, layout, destination, Instant.now());
            appendLineWithFileLock(entry);
            if (loadedEntries != null) {
                List<Entry> updatedEntries = new ArrayList<>(loadedEntries);
                updatedEntries.add(entry);
                loadedEntries = List.copyOf(updatedEntries);
            }
        }
    }

    public Optional<Entry> find(String companyId, String fiscalKey, LayoutType layout) throws IOException {
        synchronized (lock) {
            List<Entry> entries = entries();
            for (int index = entries.size() - 1; index >= 0; index--) {
                Entry entry = entries.get(index);
                if (entry.companyId().equals(companyId)
                        && entry.fiscalKey().equals(fiscalKey)
                        && entry.layout() == layout) {
                    return Optional.of(entry);
                }
            }
            return Optional.empty();
        }
    }

    private List<Entry> entries() throws IOException {
        if (loadedEntries != null) {
            return loadedEntries;
        }
        if (!Files.exists(indexFile)) {
            loadedEntries = List.of();
            return loadedEntries;
        }
        List<Entry> entries = new ArrayList<>();
        for (String line : Files.readAllLines(indexFile, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                entries.add(deserialize(line));
            }
        }
        loadedEntries = List.copyOf(entries);
        return loadedEntries;
    }

    private void appendLineWithFileLock(Entry entry) throws IOException {
        try (FileChannel channel = FileChannel.open(indexFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            byte[] bytes = (serialize(entry) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            channel.write(ByteBuffer.wrap(bytes));
        }
    }

    private static String serialize(Entry entry) {
        return String.join("\t",
                entry.companyId(),
                entry.fiscalKey(),
                entry.layout().name(),
                entry.destination().toString(),
                entry.recordedAt().toString()
        );
    }

    private static Entry deserialize(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 5) {
            throw new IllegalArgumentException("Linha invalida no indice de duplicidade: " + line);
        }
        return new Entry(
                parts[0],
                parts[1],
                LayoutType.valueOf(parts[2]),
                Path.of(parts[3]),
                Instant.parse(parts[4])
        );
    }

    public record Entry(String companyId, String fiscalKey, LayoutType layout, Path destination, Instant recordedAt) {
    }
}
