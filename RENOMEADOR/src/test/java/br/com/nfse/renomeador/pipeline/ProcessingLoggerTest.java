package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.CompanyConfig;
import br.com.nfse.renomeador.config.CompanyFolders;
import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.MonthStrategy;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingLoggerTest {
    @TempDir
    Path tempDir;

    @Test
    void escapesTabsAndLineBreaksInOperationLog() throws Exception {
        ResolvedCompanyPath companyPath = companyPath();
        CompanyRouteDirectory routes = new CompanyRouteDirectory(
                List.of(companyPath), List.of(companyPath), Set.of("25014360000173"), tempDir.resolve("backend"));
        FileProcessingResult result = FileProcessingResult.processed(
                "empresa_a",
                tempDir.resolve("entrada").resolve("nota\tcom\nquebra.pdf"),
                ProcessingStatus.UNSUPPORTED,
                "motivo\tcom\nquebra",
                tempDir.resolve("backend").resolve("revisar").resolve("destino.pdf")
        );

        new ProcessingLogger().record(routes, companyPath, result);

        String log = Files.readString(TechnicalPaths.log(routes, companyPath));
        assertThat(log)
                .doesNotContain("nota\tcom")
                .doesNotContain("motivo\tcom")
                .doesNotContain("quebra.pdf" + System.lineSeparator())
                .contains("\\t")
                .contains("\\n");
    }

    private ResolvedCompanyPath companyPath() {
        CompanyConfig company = new CompanyConfig(
                "empresa_a",
                true,
                "25.014.360/0001-73",
                MonthStrategy.DIRECT,
                List.of(),
                tempDir,
                "",
                new CompanyFolders("entrada", "processados", "revisar", "originais", "logs",
                        "canceladas", "logs/processados.idx")
        );
        return new ResolvedCompanyPath(company, tempDir, Optional.empty());
    }
}
