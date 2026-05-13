package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResultadoConsultaAdnTest {
    @Test
    void resumoNaoIncluiCorpoDaResposta() {
        ResultadoConsultaAdn resultado = new ResultadoConsultaAdn(200, "application/xml", "<xml/>".getBytes());

        assertThat(resultado.resumo()).isEqualTo("HTTP 200, content-type=application/xml, bytes=6");
    }

    @Test
    void protegeCorpoContraAlteracaoExterna() {
        byte[] corpo = "<xml/>".getBytes();
        ResultadoConsultaAdn resultado = new ResultadoConsultaAdn(200, "application/xml", corpo);

        corpo[0] = 'x';
        byte[] copia = resultado.corpo();
        copia[1] = 'x';

        assertThat(new String(resultado.corpo())).isEqualTo("<xml/>");
    }
}
