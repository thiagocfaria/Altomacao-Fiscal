package br.com.nfse.importadorpn.portal;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.time.YearMonth;
import java.util.Optional;

@FunctionalInterface
public interface RoteadorDms {
    Optional<DestinoDmsResolvido> resolver(EmpresaImportacao empresa, DocumentoDfeExtraido documento, YearMonth mesComando);
}
