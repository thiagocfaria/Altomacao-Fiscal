package br.com.nfse.renomeador.ledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingLedgerTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsEntryAndFindsAlreadyProcessedFileAfterReopen() throws Exception {
        Path ledgerFile = tempDir.resolve("logs").resolve("processados.idx");
        LedgerEntry entry = new LedgerEntry(
                "empresa_a",
                Path.of("/entrada/nota.pdf"),
                123L,
                Instant.parse("2026-04-30T12:00:00Z"),
                "hash123",
                "OK",
                Path.of("/processados/NF 1.pdf"),
                Instant.parse("2026-04-30T12:01:00Z")
        );

        ProcessingLedger ledger = new ProcessingLedger(ledgerFile);
        ledger.record(entry);

        ProcessingLedger reopened = new ProcessingLedger(ledgerFile);
        assertThat(reopened.hasProcessed("empresa_a", Path.of("/entrada/nota.pdf"), 123L, Instant.parse("2026-04-30T12:00:00Z"), "hash123"))
                .isTrue();
        assertThat(reopened.hasProcessed("empresa_a", Path.of("/entrada/nota.pdf"), 123L, Instant.parse("2026-04-30T12:00:00Z"), "outrohash"))
                .isFalse();
    }
}
