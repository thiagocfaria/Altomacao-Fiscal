package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConsultaDanfseComRetentativaTest {
    @Test
    void retentaHttp429Ou5xxAntesDeRegistrarFalha() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        List<Duration> pausas = new ArrayList<>();
        ConsultaDanfse consulta = new ConsultaDanfseComRetentativa(documento -> {
            if (chamadas.incrementAndGet() == 1) {
                return Optional.of(new ResultadoConsultaAdn(502, "text/html", "<html/>".getBytes()));
            }
            return Optional.of(new ResultadoConsultaAdn(200, "application/pdf", "%PDF-1.4".getBytes()));
        }, 3, Duration.ofSeconds(2), pausas::add);

        Optional<ResultadoConsultaAdn> resultado = consulta.consultar(documento());

        assertThat(resultado).get().extracting(ResultadoConsultaAdn::statusCode).isEqualTo(200);
        assertThat(chamadas).hasValue(2);
        assertThat(pausas).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void naoRetentaErroDefinitivo() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        List<Duration> pausas = new ArrayList<>();
        ConsultaDanfse consulta = new ConsultaDanfseComRetentativa(documento -> {
            chamadas.incrementAndGet();
            return Optional.of(new ResultadoConsultaAdn(404, "text/html", "<html/>".getBytes()));
        }, 3, Duration.ofSeconds(2), pausas::add);

        Optional<ResultadoConsultaAdn> resultado = consulta.consultar(documento());

        assertThat(resultado).get().extracting(ResultadoConsultaAdn::statusCode).isEqualTo(404);
        assertThat(chamadas).hasValue(1);
        assertThat(pausas).isEmpty();
    }

    private static DocumentoDfeExtraido documento() {
        return new DocumentoDfeExtraido("123", "CHAVE123", "NFSE", "<NFSe/>".getBytes());
    }
}
