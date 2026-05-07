package br.com.nfse.renomeador.processing;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.text.TextNormalizer;

public final class CompanyValidator {
    private final String expectedTaxIdDigits;

    public CompanyValidator(String expectedTaxId) {
        this.expectedTaxIdDigits = TextNormalizer.digitsOnly(expectedTaxId);
    }

    public boolean matches(InvoiceData invoice) {
        return !expectedTaxIdDigits.isBlank()
                && expectedTaxIdDigits.equals(TextNormalizer.digitsOnly(invoice.customerTaxId()));
    }
}
