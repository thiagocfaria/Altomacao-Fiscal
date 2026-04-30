package br.com.nfse.renomeador;

import br.com.nfse.renomeador.layout.LayoutType;
import br.com.nfse.renomeador.naming.FileNameBuilder;
import br.com.nfse.renomeador.processing.CompanyValidator;
import br.com.nfse.renomeador.processing.ProcessingDecisionService;
import br.com.nfse.renomeador.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessingRulesTest {
    @Test
    void companyValidatorNormalizesCnpjBeforeComparing() {
        CompanyValidator validator = new CompanyValidator("25014360000173");

        assertThat(validator.matches(invoice())).isTrue();
    }

    @Test
    void processingDecisionPrioritizesCancelledOverNormalStatus() {
        InvoiceData invoice = invoice().withCancelled(true);

        ProcessingDecision decision = new ProcessingDecisionService("25.014.360/0001-73").decide(invoice);

        assertThat(decision.status()).isEqualTo(ProcessingStatus.CANCELLED);
        assertThat(decision.reviewRequired()).isTrue();
    }

    @Test
    void processingDecisionDetectsWrongCompanyTaxId() {
        ProcessingDecision decision = new ProcessingDecisionService("00.000.000/0001-00").decide(invoice());

        assertThat(decision.status()).isEqualTo(ProcessingStatus.WRONG_COMPANY);
        assertThat(decision.reviewRequired()).isTrue();
    }

    @Test
    void processingDecisionPrioritizesWrongCompanyWhenOtherRequiredDataIsMissing() {
        InvoiceData invoice = new InvoiceData(
                LayoutType.PORTAL_NACIONAL,
                "9",
                "02/04/2026",
                "",
                "63.216.712/0001-62",
                "DGA ENERGIA E AUTOMACAO LTDA",
                "25.014.360/0001-73",
                new BigDecimal("140.00"),
                new BigDecimal("140.00"),
                false,
                false
        );

        ProcessingDecision decision = new ProcessingDecisionService("00.000.000/0001-00").decide(invoice);

        assertThat(decision.status()).isEqualTo(ProcessingStatus.WRONG_COMPANY);
        assertThat(decision.reviewRequired()).isTrue();
    }

    @Test
    void processingDecisionSendsRetentionConflictToReview() {
        InvoiceData invoice = invoice().withRetentionConflict(true);

        ProcessingDecision decision = new ProcessingDecisionService("25.014.360/0001-73").decide(invoice);

        assertThat(decision.status()).isEqualTo(ProcessingStatus.RETENTION_CONFLICT);
        assertThat(decision.reviewRequired()).isTrue();
    }

    @Test
    void fileNameBuilderUsesOperationalPatternAndRetentionMarker() {
        InvoiceData invoice = invoice().withRetained(true);

        String name = new FileNameBuilder().build(invoice, ProcessingStatus.OK);

        assertThat(name).isEqualTo("NFSE_9_63.216.712_ERNANE_FLAUZINO_CAMPOS_20260402_140,00_##RETIDO##.pdf");
    }

    @Test
    void fileNameBuilderOkWithoutRetentionIncludesValue() {
        String name = new FileNameBuilder().build(invoice(), ProcessingStatus.OK);

        assertThat(name).isEqualTo("NFSE_9_63.216.712_ERNANE_FLAUZINO_CAMPOS_20260402_140,00.pdf");
    }

    @Test
    void fileNameBuilderPlacesUnsupportedReasonBeforeDate() {
        String name = new FileNameBuilder().build(invoice(), ProcessingStatus.UNSUPPORTED);

        assertThat(name).isEqualTo("NFSE_9_MODELO_NAO_SUPORTADO_20260402.pdf");
    }

    @Test
    void fileNameBuilderPlacesWrongCompanyReasonWithProvider() {
        String name = new FileNameBuilder().build(invoice(), ProcessingStatus.WRONG_COMPANY);

        assertThat(name).isEqualTo("NFSE_9_CNPJ_INCORRETO_63.216.712_ERNANE_FLAUZINO_CAMPOS_20260402.pdf");
    }

    @Test
    void fileNameBuilderCancelledUsesMarker() {
        InvoiceData invoice = invoice().withCancelled(true);

        String name = new FileNameBuilder().build(invoice, ProcessingStatus.CANCELLED);

        assertThat(name).isEqualTo("NFSE_9_63.216.712_ERNANE_FLAUZINO_CAMPOS_20260402_##CANCELADA##.pdf");
    }

    private static InvoiceData invoice() {
        return new InvoiceData(
                LayoutType.PORTAL_NACIONAL,
                "9",
                "02/04/2026",
                "63.216.712 ERNANE FLAUZINO CAMPOS",
                "63.216.712/0001-62",
                "DGA ENERGIA E AUTOMACAO LTDA",
                "25.014.360/0001-73",
                new BigDecimal("140.00"),
                new BigDecimal("140.00"),
                false,
                false
        );
    }
}
