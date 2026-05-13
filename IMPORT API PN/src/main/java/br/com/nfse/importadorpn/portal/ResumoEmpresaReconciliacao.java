package br.com.nfse.importadorpn.portal;

public record ResumoEmpresaReconciliacao(
        String empresa,
        String cnpj,
        int chavesCompletasDestino,
        int chavesXmlRest,
        int chavesPdfRest,
        int chavesDms,
        int lotesConsultados,
        int documentosPortal,
        ResultadoProcessamentoLote processamento,
        boolean truncadoPorMaxLotes,
        boolean erroExternoPortal,
        String erroExternoPortalMensagem) {
    public ResumoEmpresaReconciliacao(
            String empresa,
            String cnpj,
            int chavesCompletasDestino,
            int chavesXmlRest,
            int chavesPdfRest,
            int chavesDms,
            int lotesConsultados,
            int documentosPortal,
            ResultadoProcessamentoLote processamento,
            boolean truncadoPorMaxLotes) {
        this(empresa, cnpj, chavesCompletasDestino, chavesXmlRest, chavesPdfRest, chavesDms,
                lotesConsultados, documentosPortal, processamento, truncadoPorMaxLotes, false, "");
    }

    public ResumoEmpresaReconciliacao {
        erroExternoPortalMensagem = erroExternoPortalMensagem == null ? "" : erroExternoPortalMensagem;
    }
}
