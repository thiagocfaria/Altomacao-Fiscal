package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.CompanyConfig;
import br.com.nfse.renomeador.config.CompanyFolders;
import br.com.nfse.renomeador.config.MonthStrategy;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class InputScannerTest {
    @TempDir
    Path tempDir;

    @Test
    void scansOnlyPdfFilesFromConfiguredInputDirectory() throws Exception {
        ResolvedCompanyPath companyPath = companyPath();
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.writeString(input.resolve("nota.pdf"), "pdf");
        Files.writeString(input.resolve("outra.PDF"), "pdf");
        Files.writeString(input.resolve("texto.txt"), "txt");
        Files.createDirectories(input.resolve("sub"));
        Files.writeString(input.resolve("sub").resolve("ignorado.pdf"), "pdf");

        List<InputCandidate> candidates = new InputScanner().scan(List.of(companyPath));

        assertThat(candidates).extracting(candidate -> candidate.source().getFileName().toString())
                .containsExactly("nota.pdf", "outra.PDF");
    }

    @Test
    void emptyOrMissingInputDirectoryReturnsNoCandidates() throws Exception {
        assertThat(new InputScanner().scan(List.of(companyPath()))).isEmpty();
    }

    private ResolvedCompanyPath companyPath() {
        CompanyConfig company = new CompanyConfig(
                "empresa_a",
                true,
                "25.014.360/0001-73",
                MonthStrategy.DIRECT,
                List.of(),
                tempDir,
                "{AAAA}/{MM}",
                new CompanyFolders("entrada", "processados", "revisar", "originais", "logs", "revisar/canceladas", "logs/processados.idx")
        );
        return new ResolvedCompanyPath(company, tempDir, Optional.empty());
    }
}
