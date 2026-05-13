package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.Test;

class PoliticaSomenteLeituraTest {
    @Test
    void permiteSomenteGetDeConsulta() {
        PoliticaSomenteLeitura politica = new PoliticaSomenteLeitura();

        HttpRequest request = politica.criarGet(URI.create("https://adn.nfse.gov.br/contribuintes/DFe/123"));

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri().toString()).endsWith("/DFe/123");
        assertThat(request.headers().firstValue("Accept").orElse("")).contains("application/pdf");
    }

    @Test
    void bloqueiaMetodosQuePodemAlterarEstadoFiscal() {
        PoliticaSomenteLeitura politica = new PoliticaSomenteLeitura();

        assertThatThrownBy(() -> politica.validarMetodo("POST"))
                .isInstanceOf(OperacaoFiscalBloqueadaException.class)
                .hasMessageContaining("SOMENTE_LEITURA");
        assertThatThrownBy(() -> politica.validarMetodo("PUT"))
                .isInstanceOf(OperacaoFiscalBloqueadaException.class);
        assertThatThrownBy(() -> politica.validarMetodo("PATCH"))
                .isInstanceOf(OperacaoFiscalBloqueadaException.class);
        assertThatThrownBy(() -> politica.validarMetodo("DELETE"))
                .isInstanceOf(OperacaoFiscalBloqueadaException.class);
    }
}
