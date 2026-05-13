package br.com.nfse.importadorpn.portal;

import java.util.ArrayList;
import java.util.List;

public record ResultadoProcessamentoLote(
        int documentosAfetados,
        int documentosCompletos,
        int xmlRestFaltantes,
        int pdfRestFaltantes,
        int dmsFaltantes,
        int foraDoMes,
        int foraDaEmpresa,
        int semDataEmissao,
        int rotaDmsAusente,
        List<DetalheRotaDmsAusente> rotasDmsAusentes) {

    private static final int LIMITE_DETALHES_ROTA_DMS = 50;

    public ResultadoProcessamentoLote {
        rotasDmsAusentes = rotasDmsAusentes == null ? List.of() : List.copyOf(rotasDmsAusentes);
    }

    public ResultadoProcessamentoLote(
            int documentosAfetados,
            int documentosCompletos,
            int xmlRestFaltantes,
            int pdfRestFaltantes,
            int dmsFaltantes,
            int foraDoMes,
            int semDataEmissao,
            int rotaDmsAusente) {
        this(documentosAfetados, documentosCompletos, xmlRestFaltantes, pdfRestFaltantes,
                dmsFaltantes, foraDoMes, 0, semDataEmissao, rotaDmsAusente, List.of());
    }

    public ResultadoProcessamentoLote(
            int documentosAfetados,
            int documentosCompletos,
            int xmlRestFaltantes,
            int pdfRestFaltantes,
            int dmsFaltantes,
            int foraDoMes,
            int foraDaEmpresa,
            int semDataEmissao,
            int rotaDmsAusente) {
        this(documentosAfetados, documentosCompletos, xmlRestFaltantes, pdfRestFaltantes,
                dmsFaltantes, foraDoMes, foraDaEmpresa, semDataEmissao, rotaDmsAusente, List.of());
    }

    public static ResultadoProcessamentoLote vazio() {
        return new ResultadoProcessamentoLote(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static ResultadoProcessamentoLote importados(int documentosAfetados) {
        return new ResultadoProcessamentoLote(documentosAfetados, 0, 0, 0, 0, 0, 0, 0);
    }

    public ResultadoProcessamentoLote somar(ResultadoProcessamentoLote outro) {
        return new ResultadoProcessamentoLote(
                documentosAfetados + outro.documentosAfetados,
                documentosCompletos + outro.documentosCompletos,
                xmlRestFaltantes + outro.xmlRestFaltantes,
                pdfRestFaltantes + outro.pdfRestFaltantes,
                dmsFaltantes + outro.dmsFaltantes,
                foraDoMes + outro.foraDoMes,
                foraDaEmpresa + outro.foraDaEmpresa,
                semDataEmissao + outro.semDataEmissao,
                rotaDmsAusente + outro.rotaDmsAusente,
                juntarDetalhes(rotasDmsAusentes, outro.rotasDmsAusentes));
    }

    private static List<DetalheRotaDmsAusente> juntarDetalhes(List<DetalheRotaDmsAusente> atual,
                                                              List<DetalheRotaDmsAusente> outro) {
        if (atual.isEmpty() && outro.isEmpty()) {
            return List.of();
        }
        List<DetalheRotaDmsAusente> resultado = new ArrayList<>(Math.min(LIMITE_DETALHES_ROTA_DMS,
                atual.size() + outro.size()));
        for (DetalheRotaDmsAusente detalhe : atual) {
            if (resultado.size() >= LIMITE_DETALHES_ROTA_DMS) {
                return List.copyOf(resultado);
            }
            resultado.add(detalhe);
        }
        for (DetalheRotaDmsAusente detalhe : outro) {
            if (resultado.size() >= LIMITE_DETALHES_ROTA_DMS) {
                return List.copyOf(resultado);
            }
            resultado.add(detalhe);
        }
        return List.copyOf(resultado);
    }
}
