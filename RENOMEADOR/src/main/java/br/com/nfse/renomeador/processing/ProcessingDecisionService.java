package br.com.nfse.renomeador.processing;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.ProcessingDecision;
import br.com.nfse.renomeador.layout.LayoutType;
import br.com.nfse.renomeador.text.TextNormalizer;

public final class ProcessingDecisionService {
    private final CompanyValidator companyValidator;
    private final String expectedTaxIdDigits;
    private final boolean acceptProviderOrCustomer;

    public ProcessingDecisionService(String expectedCustomerTaxId) {
        this(expectedCustomerTaxId, false);
    }

    public ProcessingDecisionService(String expectedCustomerTaxId, boolean acceptProviderOrCustomer) {
        this.companyValidator = new CompanyValidator(expectedCustomerTaxId);
        this.expectedTaxIdDigits = TextNormalizer.digitsOnly(expectedCustomerTaxId);
        this.acceptProviderOrCustomer = acceptProviderOrCustomer;
    }

    public ProcessingDecision decide(InvoiceData invoice) {
        if (invoice.cancelled()) {
            return new ProcessingDecision(ProcessingStatus.CANCELLED, true, "Nota cancelada");
        }
        if (invoice.layout() == LayoutType.UNSUPPORTED || invoice.layout() == LayoutType.NO_TEXT) {
            return new ProcessingDecision(ProcessingStatus.UNSUPPORTED, true, "Modelo nao suportado");
        }
        if (hasComparableTaxId(invoice) && !matchesExpectedCompany(invoice)) {
            return new ProcessingDecision(ProcessingStatus.WRONG_COMPANY, true, "CNPJ incorreto para repositorio");
        }
        if (missingRequiredData(invoice)) {
            return new ProcessingDecision(ProcessingStatus.MISSING_REQUIRED, true, "Dados obrigatorios ausentes");
        }
        if (invoice.retentionConflict()) {
            return new ProcessingDecision(ProcessingStatus.RETENTION_CONFLICT, true, "Evidencia conflitante de retencao");
        }
        return new ProcessingDecision(ProcessingStatus.OK, false, "Processamento automatico aprovado");
    }

    private static boolean missingRequiredData(InvoiceData invoice) {
        return isBlank(invoice.number())
                || isBlank(invoice.issueDate())
                || isBlank(invoice.providerName())
                || isBlank(invoice.customerTaxId())
                || invoice.serviceValue() == null
                || invoice.netValue() == null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hasComparableTaxId(InvoiceData invoice) {
        if (expectedTaxIdDigits.isBlank()) {
            return false;
        }
        if (acceptProviderOrCustomer) {
            return !TextNormalizer.digitsOnly(invoice.providerTaxId()).isBlank()
                    || !TextNormalizer.digitsOnly(invoice.customerTaxId()).isBlank();
        }
        return !TextNormalizer.digitsOnly(invoice.customerTaxId()).isBlank();
    }

    private boolean matchesExpectedCompany(InvoiceData invoice) {
        if (acceptProviderOrCustomer) {
            return expectedTaxIdDigits.equals(TextNormalizer.digitsOnly(invoice.providerTaxId()))
                    || expectedTaxIdDigits.equals(TextNormalizer.digitsOnly(invoice.customerTaxId()));
        }
        return companyValidator.matches(invoice);
    }
}
