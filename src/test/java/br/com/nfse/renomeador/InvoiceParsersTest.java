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

    @Test
    void portalParserMarksExplicitFederalRetentionAsRetained() {
        String text = """
                EMITENTE DA NFS-e
                NOME / NOME EMPRESARIAL
                PRESTADOR TESTE
                CNPJ / CPF / NIF
                11.111.111/0001-11
                TOMADOR DO SERVICO
                NOME / NOME EMPRESARIAL
                TOMADOR TESTE
                CNPJ / CPF / NIF
                25.014.360/0001-73
                INTERMEDIARIO
                Numero da NFS-e
                123
                Data e Hora da emissao da NFS-e
                02/04/2026
                VALOR TOTAL DA NFS-E
                Valor do Servico R$ 100,00
                Valor Liquido da NFS-e R$ 95,00
                TOTAIS APROXIMADOS
                Total das Retencoes Federais R$ 5,00
                """;

        InvoiceData invoice = new PortalNacionalParser().parse(text);

        assertThat(invoice.retained()).isTrue();
        assertThat(invoice.retentionConflict()).isFalse();
    }

    @Test
    void abrasfParserMarksRetentionWhenNetValueIsLowerThanServiceValue() {
        String text = """
                Dados do Prestador
                PRESTADOR TESTE LTDA
                CPF/CNPJ
                11.111.111/0001-11
                Identificacao da Nota Fiscal
                Numero da Nota Fiscal
                123
                Data de Geracao da NFS-e
                02/04/2026
                Dados do Tomador de Servicos
                Razao Social : TOMADOR TESTE
                CNPJ/CPF : 25.014.360/0001-73
                Dados do Intermediario
                Vl. Total dos Servicos
                R$ 100,00
                Vl. Liquido da NotaFiscal
                R$ 95,00
                Informacoes Adicionais
                """;

        InvoiceData invoice = new AbrasfIssnetParser().parse(text);

        assertThat(invoice.serviceValue()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(invoice.netValue()).isEqualByComparingTo(new BigDecimal("95.00"));
        assertThat(invoice.retained()).isTrue();
        assertThat(invoice.retentionConflict()).isFalse();
    }

    @Test
    void portalParserMarksConflictingRetentionEvidenceForReviewDecision() {
        String text = """
                EMITENTE DA NFS-e
                NOME / NOME EMPRESARIAL
                PRESTADOR TESTE
                CNPJ / CPF / NIF
                11.111.111/0001-11
                TOMADOR DO SERVICO
                NOME / NOME EMPRESARIAL
                TOMADOR TESTE
                CNPJ / CPF / NIF
                25.014.360/0001-73
                INTERMEDIARIO
                Numero da NFS-e
                123
                Data e Hora da emissao da NFS-e
                02/04/2026
                VALOR TOTAL DA NFS-E
                Valor do Servico R$ 100,00
                Valor Liquido da NFS-e R$ 95,00
                TOTAIS APROXIMADOS
                ISSQN Retido Nao Retido
                """;

        InvoiceData invoice = new PortalNacionalParser().parse(text);

        assertThat(invoice.retained()).isFalse();
        assertThat(invoice.retentionConflict()).isTrue();
    }
}
