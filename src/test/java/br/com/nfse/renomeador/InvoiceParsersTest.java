package br.com.nfse.renomeador;

import br.com.nfse.renomeador.layout.LayoutType;
import br.com.nfse.renomeador.parser.AbrasfIssnetParser;
import br.com.nfse.renomeador.parser.PortalNacionalParser;
import br.com.nfse.renomeador.pdf.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceParsersTest {
    private static final Path SAMPLES = Path.of("NF MODELO ABRASP E PORTAL NACIONAL");
    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void portalNacionalParserExtractsRequiredFieldsFromRealPdf() throws Exception {
        String text = extractor.extractText(SAMPLES.resolve("NF 9 OK.pdf"));

        InvoiceData invoice = new PortalNacionalParser().parse(text);

        assertThat(invoice.layout()).isEqualTo(LayoutType.PORTAL_NACIONAL);
        assertThat(invoice.number()).isEqualTo("9");
        assertThat(invoice.issueDate()).isEqualTo("02/04/2026");
        assertThat(invoice.providerName()).isEqualTo("63.216.712 ERNANE FLAUZINO CAMPOS");
        assertThat(invoice.providerTaxId()).isEqualTo("63.216.712/0001-62");
        assertThat(invoice.customerName()).isEqualTo("DGA ENERGIA E AUTOMACAO LTDA");
        assertThat(invoice.customerTaxId()).isEqualTo("25.014.360/0001-73");
        assertThat(invoice.serviceValue()).isEqualByComparingTo(new BigDecimal("140.00"));
        assertThat(invoice.netValue()).isEqualByComparingTo(new BigDecimal("140.00"));
        assertThat(invoice.retained()).isFalse();
        assertThat(invoice.cancelled()).isFalse();
    }

    @Test
    void abrasfParserExtractsRequiredFieldsFromFirstRealPdfPage() throws Exception {
        String page = extractor.extractPages(SAMPLES.resolve("NotasPdf.pdf")).get(0);

        InvoiceData invoice = new AbrasfIssnetParser().parse(page);

        assertThat(invoice.layout()).isEqualTo(LayoutType.ABRASF_ISSNET);
        assertThat(invoice.number()).isEqualTo("346928");
        assertThat(invoice.issueDate()).isEqualTo("02/03/2026");
        assertThat(invoice.providerName()).isEqualTo("Unimed Anapolis Cooperativa de Trabalho Medico");
        assertThat(invoice.providerTaxId()).isEqualTo("26.629.238/0001-74");
        assertThat(invoice.customerName()).isEqualTo("CSA EDUCACIONAL");
        assertThat(invoice.customerTaxId()).isEqualTo("33.265.761/0001-24");
        assertThat(invoice.serviceValue()).isEqualByComparingTo(new BigDecimal("1288.52"));
        assertThat(invoice.netValue()).isEqualByComparingTo(new BigDecimal("1288.52"));
        assertThat(invoice.retained()).isFalse();
        assertThat(invoice.cancelled()).isFalse();
    }

    @Test
    void abrasfParserDetectsCancelledNoteFromRealPdfPage() throws Exception {
        String page = extractor.extractPages(SAMPLES.resolve("NotasPdf.pdf")).get(4);

        InvoiceData invoice = new AbrasfIssnetParser().parse(page);

        assertThat(invoice.number()).isEqualTo("48");
        assertThat(invoice.cancelled()).isTrue();
    }
}
