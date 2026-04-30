package br.com.nfse.renomeador.extraction;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.layout.LayoutDetector;
import br.com.nfse.renomeador.layout.LayoutType;
import br.com.nfse.renomeador.parser.AbrasfIssnetParser;
import br.com.nfse.renomeador.parser.PortalNacionalParser;
import br.com.nfse.renomeador.pdf.PdfTextExtractor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class InvoiceSplitter {
    private final PdfTextExtractor extractor = new PdfTextExtractor();
    private final LayoutDetector detector = new LayoutDetector();

    public List<InvoiceData> splitSupportedPages(Path pdf) throws IOException {
        LayoutType documentLayout = detector.detect(extractor.extractText(pdf));
        List<InvoiceData> invoices = new ArrayList<>();
        for (String page : extractor.extractPages(pdf)) {
            LayoutType layout = detector.detect(page);
            if ((layout == LayoutType.NO_TEXT || layout == LayoutType.UNSUPPORTED)
                    && (documentLayout == LayoutType.PORTAL_NACIONAL || documentLayout == LayoutType.ABRASF_ISSNET)) {
                layout = documentLayout;
            }
            if (layout == LayoutType.PORTAL_NACIONAL) {
                invoices.add(new PortalNacionalParser().parse(page));
            } else if (layout == LayoutType.ABRASF_ISSNET) {
                invoices.add(new AbrasfIssnetParser().parse(page));
            }
        }
        return invoices;
    }
}
