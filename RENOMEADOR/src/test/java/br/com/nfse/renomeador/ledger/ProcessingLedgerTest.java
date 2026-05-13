package br.com.nfse.renomeador.ledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingLedgerTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsEntryAndFindsAlreadyProcessedFileAfterReopen() throws Exception {
        Path ledgerFile = tempDir.resolve("logs").resolve("processados.idx");
        Path destination = tempDir.resolve("processados").resolve("NF 1.pdf");
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, "pdf");
        LedgerEntry entry = new LedgerEntry(
                "empresa_a",
                Path.of("/entrada/nota.pdf"),
                123L,
                Instant.parse("2026-04-30T12:00:00Z"),
                "hash123",
                "OK",
                destination,
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

    @Test
    void findsAlreadyProcessedFileByHashEvenWhenPathChanged() throws Exception {
        Path ledgerFile = tempDir.resolve("logs").resolve("processados.idx");
        ProcessingLedger ledger = new ProcessingLedger(ledgerFile);
        Path destination = tempDir.resolve("processados").resolve("NF 1.pdf");
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, "pdf");
        ledger.record(new LedgerEntry(
                "empresa_a",
                Path.of("/entrada/nota-original.pdf"),
                123L,
                Instant.parse("2026-04-30T12:00:00Z"),
                "hash123",
                "OK",
                destination,
                Instant.parse("2026-04-30T12:01:00Z")
        ));

        ProcessingLedger reopened = new ProcessingLedger(ledgerFile);

        assertThat(reopened.hasProcessed("empresa_a", Path.of("/entrada/nota-renomeada.pdf"), 456L,
                Instant.parse("2026-05-01T12:00:00Z"), "hash123"))
                .isTrue();
    }

    @Test
    void doesNotBlockReprocessingWhenFinalDestinationWasDeleted() throws Exception {
        Path ledgerFile = tempDir.resolve("logs").resolve("processados.idx");
        ProcessingLedger ledger = new ProcessingLedger(ledgerFile);
        Path destination = tempDir.resolve("processados").resolve("NF 1.pdf");
        ledger.record(new LedgerEntry(
                "empresa_a",
                Path.of("/entrada/nota-original.pdf"),
                123L,
                Instant.parse("2026-04-30T12:00:00Z"),
                "hash123",
                "OK",
                destination,
                Instant.parse("2026-04-30T12:01:00Z")
        ));

        ProcessingLedger reopened = new ProcessingLedger(ledgerFile);

        assertThat(reopened.hasProcessed("empresa_a", Path.of("/entrada/nota-renomeada.pdf"), 456L,
                Instant.parse("2026-05-01T12:00:00Z"), "hash123"))
                .isFalse();
    }

    @Test
    void doesNotTreatTransientTechnicalErrorAsAlreadyProcessed() throws Exception {
        Path ledgerFile = tempDir.resolve("logs").resolve("processados.idx");
        ProcessingLedger ledger = new ProcessingLedger(ledgerFile);
        ledger.record(new LedgerEntry(
                "empresa_a",
                Path.of("/entrada/nota.xml"),
                123L,
                Instant.parse("2026-04-30T12:00:00Z"),
                "hash123",
                "ERROR",
                Path.of("/revisar/ERRO_PROCESSAMENTO_nota.xml"),
                Instant.parse("2026-04-30T12:01:00Z")
        ));

        ProcessingLedger reopened = new ProcessingLedger(ledgerFile);

        assertThat(reopened.hasProcessed("empresa_a", Path.of("/entrada/nota.xml"), 123L,
                Instant.parse("2026-04-30T12:00:00Z"), "hash123"))
                .isFalse();
        assertThat(reopened.hasProcessed("empresa_a", Path.of("/entrada/nota-renomeada.xml"), 456L,
                Instant.parse("2026-05-01T12:00:00Z"), "hash123"))
                .isFalse();
    }

    @Test
    void treatsTerminalReviewErrorAsAlreadyProcessed() throws Exception {
        Path ledgerFile = tempDir.resolve("logs").resolve("processados.idx");
        ProcessingLedger ledger = new ProcessingLedger(ledgerFile);
        Path destination = tempDir.resolve("revisar").resolve("ARQUIVO_MUITO_GRANDE_nota.pdf");
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, "pdf");
        ledger.record(new LedgerEntry(
                "empresa_a",
                Path.of("/entrada/nota.pdf"),
                123L,
                Instant.parse("2026-04-30T12:00:00Z"),
                "hash123",
                "ERROR",
                destination,
                Instant.parse("2026-04-30T12:01:00Z")
        ));

        ProcessingLedger reopened = new ProcessingLedger(ledgerFile);

        assertThat(reopened.hasProcessed("empresa_a", Path.of("/entrada/nota.pdf"), 123L,
                Instant.parse("2026-04-30T12:00:00Z"), "hash123"))
                .isTrue();
        assertThat(reopened.hasProcessed("empresa_a", Path.of("/entrada/nota-renomeada.pdf"), 456L,
                Instant.parse("2026-05-01T12:00:00Z"), "hash123"))
                .isTrue();
    }

    @Test
    void ignoresMalformedLinesAndKeepsThemForAudit() throws Exception {
        Path ledgerFile = tempDir.resolve("logs").resolve("processados.idx");
        Path destination = tempDir.resolve("processados").resolve("NF 1.pdf");
        Files.createDirectories(destination.getParent());
        Files.writeString(destination, "pdf");
        Files.createDirectories(ledgerFile.getParent());
        Files.writeString(ledgerFile, String.join(System.lineSeparator(),
                "linha-quebrada",
                "empresa_a\t/entrada/nota.pdf\t123\t2026-04-30T12:00:00Z\thash123\tOK\t" + destination + "\t2026-04-30T12:01:00Z"
        ));

        ProcessingLedger ledger = new ProcessingLedger(ledgerFile);

        assertThat(ledger.hasProcessed("empresa_a", Path.of("/entrada/nota.pdf"), 123L,
                Instant.parse("2026-04-30T12:00:00Z"), "hash123"))
                .isTrue();
        assertThat(ledgerFile.resolveSibling("processados.idx.corrompidas"))
                .content()
                .contains("linha-quebrada");
        assertThat(ledgerFile)
                .content()
                .doesNotContain("linha-quebrada");
    }

    @Test
    void escapesTabsAndLineBreaksInLedgerFields() throws Exception {
        Path ledgerFile = tempDir.resolve("logs").resolve("processados.idx");
        ProcessingLedger ledger = new ProcessingLedger(ledgerFile);
        Path source = Path.of("/entrada/nota\tcom\nquebra.pdf");
        Path destination = Path.of("/processados/NF\t1.pdf");

        ledger.record(new LedgerEntry(
                "empresa_a",
                source,
                123L,
                Instant.parse("2026-04-30T12:00:00Z"),
                "hash123",
                "OK",
                destination,
                Instant.parse("2026-04-30T12:01:00Z")
        ));

        assertThat(Files.readString(ledgerFile))
                .doesNotContain("nota\tcom")
                .doesNotContain("quebra.pdf" + System.lineSeparator());
        assertThat(new ProcessingLedger(ledgerFile).entries())
                .extracting(LedgerEntry::sourcePath)
                .contains(source);
    }
}
