package br.com.nfse.renomeador.parser;

import br.com.nfse.renomeador.InvoiceData;

public interface InvoiceParser {
    InvoiceData parse(String text);
}
