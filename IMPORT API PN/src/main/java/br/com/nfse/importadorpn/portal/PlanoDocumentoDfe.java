package br.com.nfse.importadorpn.portal;

import java.util.Optional;

public record PlanoDocumentoDfe(
        String nsu,
        String chaveAcesso,
        StatusPlanoDocumento status,
        boolean xmlRestFaltante,
        boolean pdfRestFaltante,
        boolean dmsFaltante,
        boolean rotaDmsAusente,
        Optional<DestinoDmsResolvido> destinoDms,
        String mensagem) {

    public PlanoDocumentoDfe {
        nsu = nsu == null ? "" : nsu;
        chaveAcesso = chaveAcesso == null ? "" : chaveAcesso;
        destinoDms = destinoDms == null ? Optional.empty() : destinoDms;
        mensagem = mensagem == null ? "" : mensagem;
    }

    public boolean deveImportar() {
        return status == StatusPlanoDocumento.IMPORTAR;
    }
}
