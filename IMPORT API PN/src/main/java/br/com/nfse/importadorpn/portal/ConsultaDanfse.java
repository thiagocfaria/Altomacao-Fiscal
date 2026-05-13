package br.com.nfse.importadorpn.portal;

import java.util.Optional;

@FunctionalInterface
public interface ConsultaDanfse {
    Optional<ResultadoConsultaAdn> consultar(DocumentoDfeExtraido documento) throws Exception;
}
