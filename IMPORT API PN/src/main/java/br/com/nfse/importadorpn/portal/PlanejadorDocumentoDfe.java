package br.com.nfse.importadorpn.portal;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.time.YearMonth;
import java.util.Optional;

public final class PlanejadorDocumentoDfe {
    public PlanoDocumentoDfe planejar(EmpresaImportacao empresa, DocumentoDfeExtraido documento,
                                      YearMonth mes, EstadoDestinoNotas estadoDestino,
                                      RoteadorDms roteadorDms) {
        String chave = documento.chaveAcesso();
        if (estadoDestino != null && !chave.isBlank() && estadoDestino.completo(chave)) {
            return new PlanoDocumentoDfe(documento.nsu(), chave, StatusPlanoDocumento.COMPLETO,
                    false, false, false, false, Optional.empty(), "Documento completo no destino");
        }

        DadosDocumentoDfe dados = DadosDocumentoDfe.fromXml(documento.xml());
        if (dados.mesEmissao().isEmpty()) {
            return new PlanoDocumentoDfe(documento.nsu(), chave, StatusPlanoDocumento.SEM_DATA_EMISSAO,
                    false, false, false, false, Optional.empty(),
                    "Documento ignorado: XML sem data de emissao para validar mes de trabalho " + mes);
        }
        YearMonth mesEmissao = dados.mesEmissao().orElseThrow();
        if (!mesEmissao.equals(mes)) {
            return new PlanoDocumentoDfe(documento.nsu(), chave, StatusPlanoDocumento.FORA_DO_MES,
                    false, false, false, false, Optional.empty(),
                    "Documento ignorado: emissao " + mesEmissao + " fora do mes de trabalho " + mes);
        }
        if (!dados.pertenceAoCnpj(empresa.cnpj())) {
            return new PlanoDocumentoDfe(documento.nsu(), chave, StatusPlanoDocumento.NAO_PERTENCE_EMPRESA,
                    false, false, false, false, Optional.empty(),
                    "Documento ignorado: CNPJ da linha ativa " + empresa.cnpj() + " nao aparece no XML");
        }

        boolean xmlRestFaltante = estadoDestino == null || chave.isBlank() || !estadoDestino.xmlRestPresente(chave);
        boolean pdfRestFaltante = estadoDestino == null || chave.isBlank() || !estadoDestino.pdfRestPresente(chave);
        boolean dmsFaltante = estadoDestino == null || chave.isBlank() || !estadoDestino.dmsPresente(chave);

        Optional<DestinoDmsResolvido> destinoDms = Optional.empty();
        boolean rotaDmsAusente = false;
        if (dmsFaltante) {
            destinoDms = resolverDms(empresa, documento, mes, roteadorDms);
            rotaDmsAusente = destinoDms.isEmpty();
        }

        if (!xmlRestFaltante && !pdfRestFaltante && !dmsFaltante) {
            return new PlanoDocumentoDfe(documento.nsu(), chave, StatusPlanoDocumento.COMPLETO,
                    false, false, false, false, destinoDms, "Documento completo no destino");
        }
        String mensagem = rotaDmsAusente
                ? "DMS sem rota para CNPJ/data de emissao do XML"
                : "Documento faltante no destino";
        return new PlanoDocumentoDfe(documento.nsu(), chave, StatusPlanoDocumento.IMPORTAR,
                xmlRestFaltante, pdfRestFaltante, dmsFaltante, rotaDmsAusente, destinoDms, mensagem);
    }

    private static Optional<DestinoDmsResolvido> resolverDms(EmpresaImportacao empresa, DocumentoDfeExtraido documento,
                                                            YearMonth mes, RoteadorDms roteadorDms) {
        if (roteadorDms != null) {
            return roteadorDms.resolver(empresa, documento, mes);
        }
        return empresa.caminhoDms().map(path -> new DestinoDmsResolvido(path, empresa.cnpj(), mes));
    }
}
