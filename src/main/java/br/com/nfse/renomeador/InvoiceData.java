package br.com.nfse.renomeador;

import br.com.nfse.renomeador.layout.LayoutType;

import java.math.BigDecimal;

public record InvoiceData(
        LayoutType layout,
        String number,
        String issueDate,
        String providerName,
        String providerTaxId,
        String customerName,
        String customerTaxId,
        BigDecimal serviceValue,
        BigDecimal netValue,
        boolean retained,
        boolean cancelled
) {
    public InvoiceData withRetained(boolean value) {
        return new InvoiceData(layout, number, issueDate, providerName, providerTaxId, customerName, customerTaxId,
                serviceValue, netValue, value, cancelled);
    }

    public InvoiceData withCancelled(boolean value) {
        return new InvoiceData(layout, number, issueDate, providerName, providerTaxId, customerName, customerTaxId,
                serviceValue, netValue, retained, value);
    }
}
