package br.com.nfse.importadorpn.certificado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ResolvedorSenhaCertificadoTest {
    @Test
    void resolveSenhaPorAliasNormalizadoSemExporValor() {
        ResolvedorSenhaCertificado resolvedor = new ResolvedorSenhaCertificado(Map.of(
                "IMPORT_API_PN_CERT_CLIENTE_A", "segredo"));

        ResultadoSenhaCertificado resultado = resolvedor.resolver("Cliente A");

        assertThat(resultado.encontrada()).isTrue();
        assertThat(resultado.senha()).contains("segredo");
        assertThat(resultado.origem()).isEqualTo("IMPORT_API_PN_CERT_CLIENTE_A");
        assertThat(resultado.toString()).doesNotContain("segredo");
    }

    @Test
    void informaVariavelEsperadaQuandoSenhaNaoExiste() {
        ResolvedorSenhaCertificado resolvedor = new ResolvedorSenhaCertificado(Map.of());

        ResultadoSenhaCertificado resultado = resolvedor.resolver("cliente-a");

        assertThat(resultado.encontrada()).isFalse();
        assertThat(resultado.origem()).isEqualTo("IMPORT_API_PN_CERT_CLIENTE_A");
        assertThat(resultado.senha()).isEmpty();
    }

    @Test
    void usaSenhaDaPlanilhaComoFallbackSemExporValor() {
        ResolvedorSenhaCertificado resolvedor = new ResolvedorSenhaCertificado(Map.of());

        ResultadoSenhaCertificado resultado = resolvedor.resolver("Cliente A", "senha-planilha");

        assertThat(resultado.encontrada()).isTrue();
        assertThat(resultado.senha()).contains("senha-planilha");
        assertThat(resultado.origem()).isEqualTo("PLANILHA_FISCAL");
        assertThat(resultado.toString()).doesNotContain("senha-planilha");
    }
}
