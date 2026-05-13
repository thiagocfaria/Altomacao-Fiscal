package br.com.nfse.importadorpn.manutencao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManutencaoRetencaoTest {
    @TempDir
    Path tempDir;

    @Test
    void compactaLogsAntigosERemoveLedgersForaDaRetencao() throws Exception {
        Path logs = Files.createDirectories(tempDir.resolve("logs"));
        Path ledger = Files.createDirectories(tempDir.resolve("ledger"));
        Files.writeString(logs.resolve("2026-03-01.jsonl"), "{\"evento\":\"teste\"}\n");
        Files.writeString(logs.resolve("2026-05-08.jsonl"), "{\"evento\":\"recente\"}\n");
        Files.writeString(ledger.resolve("2025-12.tsv"), "cabecalho\n");
        Files.writeString(ledger.resolve("2026-04.tsv"), "cabecalho\n");

        ResultadoManutencao resultado = new ManutencaoRetencao(tempDir)
                .executar(LocalDate.of(2026, 5, 8), 30, 2);

        assertThat(resultado.logsCompactados()).isEqualTo(1);
        assertThat(resultado.ledgersRemovidos()).isEqualTo(1);
        assertThat(logs.resolve("2026-03-01.jsonl")).doesNotExist();
        assertThat(logs.resolve("2026-03-01.jsonl.gz")).exists();
        assertThat(logs.resolve("2026-05-08.jsonl")).exists();
        assertThat(ledger.resolve("2025-12.tsv")).doesNotExist();
        assertThat(ledger.resolve("2026-04.tsv")).exists();
    }
}
