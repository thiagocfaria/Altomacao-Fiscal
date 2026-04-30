package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BatchModeRunnerTest {
    private static final Path SAMPLES = Path.of("NF MODELO ABRASP E PORTAL NACIONAL");

    @TempDir
    Path tempDir;

    @Test
    void batchProcessesMixedLotInHomologationModeAndPreservesInput() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Files.copy(SAMPLES.resolve("NF 55034 OK.pdf"), input.resolve("NF 55034 OK.pdf"));
        Path config = writeConfig("25.014.360/0001-73");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(summary.count(ProcessingStatus.UNSUPPORTED)).isEqualTo(1);
        assertThat(input.resolve("NF 9 OK.pdf")).exists();
        assertThat(input.resolve("NF 55034 OK.pdf")).exists();
        assertThat(Files.list(tempDir.resolve("processados")).map(path -> path.getFileName().toString()))
                .anyMatch(name -> name.startsWith("NFSE_9_") && name.endsWith(".pdf"));
        assertThat(Files.list(tempDir.resolve("revisar")).map(path -> path.getFileName().toString()))
                .anyMatch(name -> name.startsWith("NFSE_DESCONHECIDA_MODELO_NAO_SUPORTADO_") && name.endsWith(".pdf"));
        assertThat(tempDir.resolve("logs").resolve("processados.idx")).exists();
        assertThat(tempDir.resolve("logs").resolve("execucao.log")).exists();
    }

    @Test
    void batchUsesLedgerToSkipSecondRun() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Path config = writeConfig("25.014.360/0001-73");

        new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);
        var secondRun = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);

        assertThat(secondRun.skipped()).isEqualTo(1);
        assertThat(secondRun.total()).isZero();
    }

    @Test
    void batchRoutesWrongCompanyToReview() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Path config = writeConfig("00.000.000/0001-00");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);

        assertThat(summary.count(ProcessingStatus.WRONG_COMPANY)).isEqualTo(1);
        assertThat(Files.list(tempDir.resolve("revisar")).map(path -> path.getFileName().toString()))
                .anyMatch(name -> name.startsWith("NFSE_9_CNPJ_INCORRETO_") && name.endsWith(".pdf"));
    }

    private Path writeConfig(String taxId) throws Exception {
        Path config = tempDir.resolve("empresas.yaml");
        Files.writeString(config, """
                empresas:
                  - id: empresa_piloto
                    habilitada: true
                    cnpjTomador: "%s"
                    estrategiaMes: "direto"
                    pastaBase: "%s"
                    pastas:
                      entrada: "entrada"
                      processados: "processados"
                      revisar: "revisar"
                      originais: "originais"
                      logs: "logs"
                      canceladas: "revisar/canceladas"
                      ledger: "logs/processados.idx"
                """.formatted(taxId, tempDir.toString().replace("\\", "/")));
        return config;
    }
}
