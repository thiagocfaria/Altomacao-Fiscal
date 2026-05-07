package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.ledger.DuplicateInvoiceIndex;
import br.com.nfse.renomeador.ledger.ProcessingLedger;

import java.nio.file.Path;
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

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
