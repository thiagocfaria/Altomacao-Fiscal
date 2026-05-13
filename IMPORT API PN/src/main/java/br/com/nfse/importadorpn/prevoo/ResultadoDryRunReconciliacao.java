package br.com.nfse.importadorpn.prevoo;

import br.com.nfse.importadorpn.portal.ResultadoReconciliacaoPortal;
import br.com.nfse.importadorpn.portal.ResumoEmpresaReconciliacao;
import br.com.nfse.importadorpn.portal.DetalheRotaDmsAusente;
import java.util.List;

public record ResultadoDryRunReconciliacao(ResultadoReconciliacaoPortal reconciliacao, NivelPrevoo nivel) {
    public List<ResumoEmpresaReconciliacao> empresas() {
        return reconciliacao.empresas();
    }

    public int totalLotesConsultados() {
        return reconciliacao.totalLotesConsultados();
    }

    public int totalDocumentosPortal() {
        return reconciliacao.totalDocumentosPortal();
    }

    public int totalDocumentosAfetados() {
        return reconciliacao.totalDocumentosAfetados();
    }

    public int totalDocumentosCompletos() {
        return reconciliacao.totalDocumentosCompletos();
    }

    public int totalXmlRestFaltantes() {
        return reconciliacao.totalXmlRestFaltantes();
    }

    public int totalPdfRestFaltantes() {
        return reconciliacao.totalPdfRestFaltantes();
    }

    public int totalDmsFaltantes() {
        return reconciliacao.totalDmsFaltantes();
    }

    public int totalForaDoMes() {
        return reconciliacao.totalForaDoMes();
    }

    public int totalSemDataEmissao() {
        return reconciliacao.totalSemDataEmissao();
    }

    public int totalForaDaEmpresa() {
        return reconciliacao.totalForaDaEmpresa();
    }

    public int totalRotaDmsAusente() {
        return reconciliacao.totalRotaDmsAusente();
    }

    public List<DetalheRotaDmsAusente> rotasDmsAusentes() {
        return reconciliacao.rotasDmsAusentes();
    }

    public int totalErrosExternosPortal() {
        return reconciliacao.totalErrosExternosPortal();
    }

    public boolean truncadoPorMaxLotes() {
        return reconciliacao.truncadoPorMaxLotes();
    }
}
