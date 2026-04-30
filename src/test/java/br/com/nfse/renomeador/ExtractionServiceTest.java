package br.com.nfse.renomeador;

import br.com.nfse.renomeador.extraction.ExtractionResult;
import br.com.nfse.renomeador.extraction.InvoiceExtractionService;
import br.com.nfse.renomeador.extraction.InvoiceSplitter;
import br.com.nfse.renomeador.layout.LayoutType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionServiceTest {
    private static final Path SAMPLES = Path.of("NF MODELO ABRASP E PORTAL NACIONAL");

    @TempDir
    Path tempDir;

    @Test
    void extractsPortalNacionalPdfThroughSingleEntryPoint() throws Exception {
        ExtractionResult result = new InvoiceExtractionService().extract(SAMPLES.resolve("NF 9 OK.pdf"));

        assertThat(result.layout()).isEqualTo(LayoutType.PORTAL_NACIONAL);
        assertThat(result.invoice()).isPresent();
        assertThat(result.invoice().orElseThrow().number()).isEqualTo("9");
    }

    @Test
    void routesImageOnlyPdfToNoTextReview() throws Exception {
        ExtractionResult result = new InvoiceExtractionService().extract(SAMPLES.resolve("NF 55034 OK.pdf"));

        assertThat(result.layout()).isEqualTo(LayoutType.NO_TEXT);
        assertThat(result.invoice()).isEmpty();
        assertThat(result.reason()).isEqualTo("PDF sem texto selecionavel suficiente");
    }

    @Test
    void splitsGroupedAbrasfPdfByPageWhenEachPageHasASupportedLayout() throws Exception {
        var notes = new InvoiceSplitter().splitSupportedPages(SAMPLES.resolve("NotasPdf.pdf"));

        assertThat(notes).hasSize(7);
        assertThat(notes).allSatisfy(note -> assertThat(note.layout()).isEqualTo(LayoutType.ABRASF_ISSNET));
    }

    @Test
    void writesOnePhysicalPdfPerSupportedPageFromGroupedAbrasfPdf() throws Exception {
        Path outputDir = tempDir.resolve("split");

        List<Path> files = new InvoiceSplitter().splitSupportedPagesToFiles(SAMPLES.resolve("NotasPdf.pdf"), outputDir);

        assertThat(files).hasSize(7);
        assertThat(files).allSatisfy(file -> {
            assertThat(file).exists();
            assertThat(new br.com.nfse.renomeador.pdf.PdfTextExtractor().extractPages(file)).hasSize(1);
        });
        assertThat(new InvoiceExtractionService().extract(files.get(0)).invoice().orElseThrow().number()).isEqualTo("346928");
    }
}
