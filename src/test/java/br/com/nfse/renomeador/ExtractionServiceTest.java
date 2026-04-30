package br.com.nfse.renomeador;

import br.com.nfse.renomeador.extraction.ExtractionResult;
import br.com.nfse.renomeador.extraction.InvoiceExtractionService;
import br.com.nfse.renomeador.extraction.InvoiceSplitter;
import br.com.nfse.renomeador.layout.LayoutType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionServiceTest {
    private static final Path SAMPLES = Path.of("NF MODELO ABRASP E PORTAL NACIONAL");

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
}
