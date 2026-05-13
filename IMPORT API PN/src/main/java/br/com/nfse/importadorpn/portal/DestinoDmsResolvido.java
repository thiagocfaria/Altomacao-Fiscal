package br.com.nfse.importadorpn.portal;

import java.nio.file.Path;
import java.time.YearMonth;

public record DestinoDmsResolvido(Path caminhoDms, String cnpj, YearMonth mes) {
}
