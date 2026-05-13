package br.com.nfse.importadorpn.portal;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public final class ConsultaDanfseComRetentativa implements ConsultaDanfse {
    private final ConsultaDanfse consulta;
    private final int tentativas;
    private final Duration intervaloBase;
    private final EsperaRetentativa espera;

    public ConsultaDanfseComRetentativa(ConsultaDanfse consulta, int tentativas, Duration intervaloBase) {
        this(consulta, tentativas, intervaloBase, duracao -> Thread.sleep(duracao.toMillis()));
    }

    ConsultaDanfseComRetentativa(ConsultaDanfse consulta, int tentativas, Duration intervaloBase,
            EsperaRetentativa espera) {
        this.consulta = Objects.requireNonNull(consulta, "consulta");
        this.tentativas = Math.max(1, tentativas);
        this.intervaloBase = Objects.requireNonNull(intervaloBase, "intervaloBase");
        this.espera = Objects.requireNonNull(espera, "espera");
    }

    @Override
    public Optional<ResultadoConsultaAdn> consultar(DocumentoDfeExtraido documento) throws Exception {
        Optional<ResultadoConsultaAdn> ultimoResultado = Optional.empty();
        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            if (tentativa > 1) {
                espera.pausar(intervaloBase.multipliedBy(tentativa - 1L));
            }
            ultimoResultado = consulta.consultar(documento);
            if (ultimoResultado.isEmpty() || !deveRetentar(ultimoResultado.orElseThrow())) {
                return ultimoResultado;
            }
        }
        return ultimoResultado;
    }

    private static boolean deveRetentar(ResultadoConsultaAdn resultado) {
        // Retenta em rate-limit, erros de servidor, falha de rede (statusCode 0),
        // e tambem quando recebemos 200 mas o content-type nao indica PDF — alguns
        // endpoints do ADN devolvem JSON/HTML de erro com status 200 nesses casos.
        int status = resultado.statusCode();
        if (status == 429 || status >= 500 || status == 0) {
            return true;
        }
        if (status == 200) {
            String ct = resultado.contentType();
            if (ct == null || !ct.toLowerCase().contains("pdf")) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    interface EsperaRetentativa {
        void pausar(Duration duracao) throws InterruptedException;
    }
}
