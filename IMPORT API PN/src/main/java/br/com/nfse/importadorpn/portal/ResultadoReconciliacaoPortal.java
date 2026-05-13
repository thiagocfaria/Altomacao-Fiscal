package br.com.nfse.importadorpn.portal;

import java.util.List;

public record ResultadoReconciliacaoPortal(List<ResumoEmpresaReconciliacao> empresas) {
    public ResultadoReconciliacaoPortal {
        empresas = List.copyOf(empresas);
    }

    public int empresasProcessadas() {
        return empresas.size();
    }

    public int totalLotesConsultados() {
        return empresas.stream().mapToInt(ResumoEmpresaReconciliacao::lotesConsultados).sum();
    }

    public int totalDocumentosPortal() {
        return empresas.stream().mapToInt(ResumoEmpresaReconciliacao::documentosPortal).sum();
    }

    public int totalDocumentosAfetados() {
        return empresas.stream().map(ResumoEmpresaReconciliacao::processamento)
                .mapToInt(ResultadoProcessamentoLote::documentosAfetados).sum();
    }

    public int totalDocumentosCompletos() {
        return empresas.stream().map(ResumoEmpresaReconciliacao::processamento)
                .mapToInt(ResultadoProcessamentoLote::documentosCompletos).sum();
    }

    public int totalXmlRestFaltantes() {
        return empresas.stream().map(ResumoEmpresaReconciliacao::processamento)
                .mapToInt(ResultadoProcessamentoLote::xmlRestFaltantes).sum();
    }

    public int totalPdfRestFaltantes() {
        return empresas.stream().map(ResumoEmpresaReconciliacao::processamento)
                .mapToInt(ResultadoProcessamentoLote::pdfRestFaltantes).sum();
    }

    public int totalDmsFaltantes() {
        return empresas.stream().map(ResumoEmpresaReconciliacao::processamento)
                .mapToInt(ResultadoProcessamentoLote::dmsFaltantes).sum();
    }

    public int totalForaDoMes() {
        return empresas.stream().map(ResumoEmpresaReconciliacao::processamento)
                .mapToInt(ResultadoProcessamentoLote::foraDoMes).sum();
    }

    public int totalSemDataEmissao() {
        return empresas.stream().map(ResumoEmpresaReconciliacao::processamento)
                .mapToInt(ResultadoProcessamentoLote::semDataEmissao).sum();
    }

    public int totalForaDaEmpresa() {
        return empresas.stream().map(ResumoEmpresaReconciliacao::processamento)
                .mapToInt(ResultadoProcessamentoLote::foraDaEmpresa).sum();
    }

    public int totalRotaDmsAusente() {
        return empresas.stream().map(ResumoEmpresaReconciliacao::processamento)
                .mapToInt(ResultadoProcessamentoLote::rotaDmsAusente).sum();
    }

    public List<DetalheRotaDmsAusente> rotasDmsAusentes() {
        return empresas.stream().map(ResumoEmpresaReconciliacao::processamento)
                .flatMap(processamento -> processamento.rotasDmsAusentes().stream())
                .toList();
    }

    public int totalErrosExternosPortal() {
        return (int) empresas.stream().filter(ResumoEmpresaReconciliacao::erroExternoPortal).count();
    }

    public boolean erroExternoPortal() {
        return empresas.stream().anyMatch(ResumoEmpresaReconciliacao::erroExternoPortal);
    }

    public boolean truncadoPorMaxLotes() {
        return empresas.stream().anyMatch(ResumoEmpresaReconciliacao::truncadoPorMaxLotes);
    }
}
