package br.com.nfse.renomeador.extraction;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.layout.LayoutDetector;
import br.com.nfse.renomeador.layout.LayoutType;
import br.com.nfse.renomeador.parser.AbrasfIssnetParser;
import br.com.nfse.renomeador.parser.PortalNacionalParser;
import br.com.nfse.renomeador.pdf.PdfTextExtractor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.nio.file.Files;
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

    public List<Path> splitSupportedPagesToFiles(Path pdf, Path outputDirectory) throws IOException {
        List<String> pages = extractor.extractPages(pdf);
        LayoutType documentLayout = detector.detect(extractor.extractText(pdf));
        for (int index = 0; index < pages.size(); index++) {
            LayoutType layout = supportedLayoutForPage(pages.get(index), documentLayout);
            if (layout != LayoutType.PORTAL_NACIONAL && layout != LayoutType.ABRASF_ISSNET) {
                throw new IllegalArgumentException("Pagina sem layout homologado para separacao segura: " + (index + 1));
            }
        }

        Files.createDirectories(outputDirectory);
        List<Path> writtenFiles = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            List<PDDocument> splitDocuments = new Splitter().split(document);
            try {
                for (int index = 0; index < splitDocuments.size(); index++) {
                    Path destination = nextAvailable(outputDirectory.resolve(fileNameForPage(pdf, index + 1)));
                    splitDocuments.get(index).save(destination.toFile());
                    writtenFiles.add(destination);
                }
            } finally {
                for (PDDocument splitDocument : splitDocuments) {
                    splitDocument.close();
                }
            }
        }
        return List.copyOf(writtenFiles);
    }

    private LayoutType supportedLayoutForPage(String page, LayoutType documentLayout) {
        LayoutType layout = detector.detect(page);
        if ((layout == LayoutType.NO_TEXT || layout == LayoutType.UNSUPPORTED)
                && (documentLayout == LayoutType.PORTAL_NACIONAL || documentLayout == LayoutType.ABRASF_ISSNET)) {
            return documentLayout;
        }
        return layout;
    }

    private static String fileNameForPage(Path pdf, int page) {
        String fileName = pdf.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return "%s_p%02d.pdf".formatted(base, page);
    }

    private static Path nextAvailable(Path desired) {
        if (!Files.exists(desired)) {
            return desired;
        }
        String fileName = desired.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        Path parent = desired.getParent();
        for (int counter = 1; counter < 10_000; counter++) {
            Path candidate = parent.resolve("%s_%02d%s".formatted(base, counter, extension));
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Nao foi possivel gerar nome unico para PDF separado: " + desired);
    }
}
