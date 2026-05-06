package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BatchModeRunnerIT {
    private static final Path SAMPLES = Path.of("NF MODELO ABRASP E PORTAL NACIONAL");

    @TempDir
    Path tempDir;

    @Test
    void processesRealPortalPdfEndToEndFromYamlConfig() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("entrada"));
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Path config = writeConfig();

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(tempDir.resolve("processados"))
                .isDirectoryContaining(path -> path.getFileName().toString()
                        .equals("NFSE_9_63.216.712_ERNANE_FLAUZINO_CAMPOS_02.04.2026_140,00.pdf"));
        assertThat(tempDir.resolve("originais")).isDirectoryContaining(path ->
                path.getFileName().toString().equals("NF 9 OK.pdf"));
        assertThat(Files.readString(tempDir.resolve("logs").resolve("execucao.log")))
                .contains("OK")
                .contains("duracaoMs=");
    }

    private Path writeConfig() throws Exception {
        Path config = tempDir.resolve("empresas.yaml");
        Files.writeString(config, """
                empresas:
                  - id: empresa_piloto
                    habilitada: true
                    cnpjTomador: "25.014.360/0001-73"
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
                """.formatted(tempDir.toString().replace("\\", "/")));
        return config;
    }
}
