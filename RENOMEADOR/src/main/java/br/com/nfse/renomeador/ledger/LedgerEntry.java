package br.com.nfse.renomeador.ledger;

import java.nio.file.Path;
import java.time.Instant;

public record LedgerEntry(
        String companyId,
        Path sourcePath,
        long size,
        Instant lastModified,
        String sha256,
        String finalStatus,
        Path finalDestination,
        Instant processedAt
) {
}
