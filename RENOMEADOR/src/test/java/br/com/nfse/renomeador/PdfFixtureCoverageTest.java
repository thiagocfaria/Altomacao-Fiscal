package br.com.nfse.renomeador;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PdfFixtureCoverageTest {
    @Test
    void keepsMinimumRealPdfRegressionSuiteWithExtremeCases() throws Exception {
        Path samples = Path.of("src/test/resources/nfse-modelos");

        try (var stream = Files.list(samples)) {
            assertThat(stream
                    .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
                    .map(path -> path.getFileName().toString())
                    .toList())
                    .hasSizeGreaterThanOrEqualTo(12)
                    .contains("NotasPdf.pdf")
                    .anyMatch(name -> name.contains("MODELO_NAO_SUPORTADO"));
        }
    }
}
