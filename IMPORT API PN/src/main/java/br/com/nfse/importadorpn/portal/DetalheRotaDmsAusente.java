package br.com.nfse.importadorpn.portal;

import java.time.YearMonth;

public record DetalheRotaDmsAusente(
        String empresaConsulta,
        String cnpjConsulta,
        String cnpjEncontradoNoXml,
        YearMonth mesEmissao,
        String nsu,
        String chaveAcesso) {

    public DetalheRotaDmsAusente {
        empresaConsulta = empresaConsulta == null ? "" : empresaConsulta;
        cnpjConsulta = cnpjConsulta == null ? "" : cnpjConsulta;
        cnpjEncontradoNoXml = cnpjEncontradoNoXml == null ? "" : cnpjEncontradoNoXml;
        nsu = nsu == null ? "" : nsu;
        chaveAcesso = chaveAcesso == null ? "" : chaveAcesso;
    }

    public String resumo() {
        return "DMS sem rota: empresa consulta=" + empresaConsulta
                + ", CNPJ consulta=" + cnpjConsulta
                + ", CNPJ encontrado no XML=" + cnpjEncontradoNoXml
                + ", mes emissao=" + mesEmissao
                + ", NSU=" + nsu
                + ", chave=" + chaveAcesso;
    }
}
