package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;
import org.junit.jupiter.api.Test;

class ClienteAdnTest {
    @Test
    void montaConsultaDfePorNsuEmModoSomenteLeitura() {
        ClienteAdn cliente = new ClienteAdn(AmbienteAdn.PRODUCAO_RESTRITA, new PoliticaSomenteLeitura());

        HttpRequest request = cliente.consultarDfePorNsu("123", "11222333000181");

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri().toString())
                .isEqualTo("https://adn.producaorestrita.nfse.gov.br/contribuintes/DFe/123?cnpj=11222333000181");
    }

    @Test
    void montaConsultaEventosPorChaveEmModoSomenteLeitura() {
        ClienteAdn cliente = new ClienteAdn(AmbienteAdn.PRODUCAO, new PoliticaSomenteLeitura());

        HttpRequest request = cliente.consultarEventosPorChave("NFS123");

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri().toString())
                .isEqualTo("https://adn.nfse.gov.br/contribuintes/NFSe/NFS123/Eventos");
    }

    @Test
    void montaConsultaDanfsePorChaveEmModoSomenteLeitura() {
        ClienteAdn cliente = new ClienteAdn(AmbienteAdn.PRODUCAO, new PoliticaSomenteLeitura());

        HttpRequest request = cliente.consultarDanfsePorChave("NFS123");

        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.uri().toString())
                .isEqualTo("https://adn.nfse.gov.br/danfse/NFS123");
        assertThat(request.headers().firstValue("Accept").orElse(""))
                .contains("application/pdf");
    }
}
