package br.com.nfse.renomeador.extraction;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.layout.LayoutType;

import java.util.Optional;

public record ExtractionResult(LayoutType layout, Optional<InvoiceData> invoice, String reason) {
    public static ExtractionResult parsed(InvoiceData invoice) {
        return new ExtractionResult(invoice.layout(), Optional.of(invoice), "Layout homologado");
    }

    public static ExtractionResult review(LayoutType layout, String reason) {
        return new ExtractionResult(layout, Optional.empty(), reason);
    }
}
