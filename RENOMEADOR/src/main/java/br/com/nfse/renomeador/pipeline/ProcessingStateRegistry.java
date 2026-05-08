package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.ledger.DuplicateInvoiceIndex;
import br.com.nfse.renomeador.ledger.ProcessingLedger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ProcessingStateRegistry {
    private final Map<Path, ProcessingLedger> ledgers = new ConcurrentHashMap<>();
    private final Map<Path, DuplicateInvoiceIndex> duplicateIndexes = new ConcurrentHashMap<>();

    ProcessingLedger ledger(Path path) {
        return ledgers.computeIfAbsent(normalize(path), ProcessingLedger::new);
    }

    DuplicateInvoiceIndex duplicateIndex(Path path) {
        return duplicateIndexes.computeIfAbsent(normalize(path), DuplicateInvoiceIndex::new);
    }

    List<ProcessingLedger> monthlyLedgers(Path companyRoot) throws IOException {
        if (!Files.isDirectory(companyRoot)) {
            return List.of();
        }
        try (var stream = Files.list(companyRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(ProcessingStateRegistry::isYearMonthDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .map(path -> ledger(path.resolve("processados.idx")))
                    .toList();
        }
    }

    List<DuplicateInvoiceIndex> monthlyDuplicateIndexes(Path companyRoot) throws IOException {
        if (!Files.isDirectory(companyRoot)) {
            return List.of();
        }
        try (var stream = Files.list(companyRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(ProcessingStateRegistry::isYearMonthDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .map(path -> duplicateIndex(path.resolve("duplicadas.idx")))
                    .toList();
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static boolean isYearMonthDirectory(Path path) {
        return path.getFileName().toString().matches("\\d{4}-\\d{2}");
    }
}
