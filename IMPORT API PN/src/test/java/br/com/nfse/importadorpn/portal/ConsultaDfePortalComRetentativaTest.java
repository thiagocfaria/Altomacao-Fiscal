package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConsultaDfePortalComRetentativaTest {
    @Test
    void retentaHttpTemporarioAntesDeDevolverSucesso() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        List<Duration> pausas = new ArrayList<>();
        ConsultaDfePortal consulta = new ConsultaDfePortalComRetentativa((empresa, nsu) -> {
            int chamada = chamadas.incrementAndGet();
            if (chamada == 1) {
                return new ResultadoConsultaAdn(503, "text/html", "<html/>".getBytes());
            }
            if (chamada == 2) {
                return new ResultadoConsultaAdn(429, "application/json", "{}".getBytes());
            }
            return new ResultadoConsultaAdn(200, "application/json", "{\"LoteDFe\":[]}".getBytes());
        }, 5, Duration.ofSeconds(2), pausas::add);

        ResultadoConsultaAdn resultado = consulta.consultar(empresa(), "1");

        assertThat(resultado.statusCode()).isEqualTo(200);
        assertThat(chamadas).hasValue(3);
        assertThat(pausas).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void naoRetentaHttpDefinitivo() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        List<Duration> pausas = new ArrayList<>();
        ConsultaDfePortal consulta = new ConsultaDfePortalComRetentativa((empresa, nsu) -> {
            chamadas.incrementAndGet();
            return new ResultadoConsultaAdn(403, "application/json", "{}".getBytes());
        }, 5, Duration.ofSeconds(2), pausas::add);

        ResultadoConsultaAdn resultado = consulta.consultar(empresa(), "1");

        assertThat(resultado.statusCode()).isEqualTo(403);
        assertThat(chamadas).hasValue(1);
        assertThat(pausas).isEmpty();
    }

    @Test
    void timeoutPersistenteViraResultadoControladoDepoisDasTentativas() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        List<Duration> pausas = new ArrayList<>();
        ConsultaDfePortal consulta = new ConsultaDfePortalComRetentativa((empresa, nsu) -> {
            chamadas.incrementAndGet();
            throw new HttpTimeoutException("tempo esgotado");
        }, 3, Duration.ofSeconds(1), pausas::add);

        ResultadoConsultaAdn resultado = consulta.consultar(empresa(), "1");

        assertThat(resultado.statusCode()).isZero();
        assertThat(resultado.contentType()).isEqualTo("erro-rede-HttpTimeoutException");
        assertThat(chamadas).hasValue(3);
        assertThat(pausas).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Test
    void resposta200SemJsonViraResultadoControladoDepoisDasTentativas() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        List<Duration> pausas = new ArrayList<>();
        ConsultaDfePortal consulta = new ConsultaDfePortalComRetentativa((empresa, nsu) -> {
            chamadas.incrementAndGet();
            return new ResultadoConsultaAdn(200, "text/html", "<html/>".getBytes());
        }, 2, Duration.ofSeconds(1), pausas::add);

        ResultadoConsultaAdn resultado = consulta.consultar(empresa(), "1");

        assertThat(resultado.statusCode()).isZero();
        assertThat(resultado.contentType()).isEqualTo("erro-resposta-invalida-DFe");
        assertThat(chamadas).hasValue(2);
        assertThat(pausas).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    void naoRetentaErroDeConfiguracao() {
        AtomicInteger chamadas = new AtomicInteger();
        List<Duration> pausas = new ArrayList<>();
        ConsultaDfePortal consulta = new ConsultaDfePortalComRetentativa((empresa, nsu) -> {
            chamadas.incrementAndGet();
            throw new IllegalStateException("senha do certificado nao encontrada");
        }, 5, Duration.ofSeconds(2), pausas::add);

        assertThatThrownBy(() -> consulta.consultar(empresa(), "1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("senha do certificado");
        assertThat(chamadas).hasValue(1);
        assertThat(pausas).isEmpty();
    }

    private static EmpresaImportacao empresa() {
        return new EmpresaImportacao(
                "EMPRESA TESTE",
                "12345678000190",
                Optional.<Path>empty(),
                Optional.<Path>empty(),
                Optional.<Path>empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.<LocalDate>empty(),
                "CADASTRO TESTE",
                2);
    }
}
