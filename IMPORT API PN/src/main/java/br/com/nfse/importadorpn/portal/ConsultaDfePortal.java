package br.com.nfse.importadorpn.portal;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;

@FunctionalInterface
public interface ConsultaDfePortal {
    ResultadoConsultaAdn consultar(EmpresaImportacao empresa, String nsu) throws Exception;
}
