package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Optional;

import static br.com.nfse.renomeador.TestSamples.samplePdf;
import static org.assertj.core.api.Assertions.assertThat;

class BatchModeRunnerIT {
    @TempDir
    Path tempDir;

    @Test
    void processesRealPortalPdfEndToEndFromYamlConfig() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("entrada"));
        Files.copy(samplePdf("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Path config = writeConfig();

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(tempDir.resolve("PDF").resolve("processados"))
                .isDirectoryContaining(path -> path.getFileName().toString()
                        .equals("NFSE_9_63.216.712_ERNANE_FLAUZINO_CAMPOS_02.04.2026_140,00.pdf"));
        Path backendCompany = tempDir.resolve("backend").resolve("empresas").resolve("empresa_piloto");
        assertThat(backendCompany.resolve("originais")).doesNotExist();
        assertThat(Files.readString(backendCompany.resolve("execucao-" + YearMonth.now() + ".tsv")))
                .contains("OK")
                .contains("duracaoMs=");
        assertThat(tempDir.resolve("logs")).doesNotExist();
        assertThat(tempDir.resolve("originais")).doesNotExist();
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
                      canceladas: "canceladas"
                """.formatted(tempDir.toString().replace("\\", "/")));
        return config;
    }
}
