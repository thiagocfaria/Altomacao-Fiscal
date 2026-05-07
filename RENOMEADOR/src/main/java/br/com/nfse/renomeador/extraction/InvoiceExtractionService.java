package br.com.nfse.renomeador.extraction;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.layout.LayoutDetector;
import br.com.nfse.renomeador.layout.LayoutType;
import br.com.nfse.renomeador.parser.AbrasfIssnetParser;
import br.com.nfse.renomeador.parser.PortalNacionalParser;
import br.com.nfse.renomeador.pdf.PdfTextExtractor;

import java.io.IOException;
import java.nio.file.Path;

public final class InvoiceExtractionService {
    private final PdfTextExtractor textExtractor;
    private final LayoutDetector layoutDetector;

    public InvoiceExtractionService() {
        this(new PdfTextExtractor(), new LayoutDetector());
    }

    InvoiceExtractionService(PdfTextExtractor textExtractor, LayoutDetector layoutDetector) {
        this.textExtractor = textExtractor;
        this.layoutDetector = layoutDetector;
    }

    public ExtractionResult extract(Path pdf) throws IOException {
        String text = textExtractor.extractText(pdf);
        LayoutType layout = layoutDetector.detect(text);
        return switch (layout) {
            case PORTAL_NACIONAL -> ExtractionResult.parsed(new PortalNacionalParser().parse(text));
            case ABRASF_ISSNET -> ExtractionResult.parsed(new AbrasfIssnetParser().parse(text));
            case NO_TEXT -> ExtractionResult.review(layout, "PDF sem texto selecionavel suficiente");
            case UNSUPPORTED -> ExtractionResult.review(layout, "Modelo nao suportado");
        };
    }
}
