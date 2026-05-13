package br.com.nfse.importadorpn.portal;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import javax.net.ssl.SSLException;

public final class ConsultaDfePortalComRetentativa implements ConsultaDfePortal {
    private final ConsultaDfePortal consulta;
    private final int tentativas;
    private final Duration intervaloBase;
    private final EsperaRetentativa espera;

    public ConsultaDfePortalComRetentativa(ConsultaDfePortal consulta, int tentativas, Duration intervaloBase) {
        this(consulta, tentativas, intervaloBase, duracao -> Thread.sleep(duracao.toMillis()));
    }

    ConsultaDfePortalComRetentativa(ConsultaDfePortal consulta, int tentativas, Duration intervaloBase,
            EsperaRetentativa espera) {
        this.consulta = Objects.requireNonNull(consulta, "consulta");
        this.tentativas = Math.max(1, tentativas);
        this.intervaloBase = Objects.requireNonNull(intervaloBase, "intervaloBase");
        this.espera = Objects.requireNonNull(espera, "espera");
    }

    @Override
    public ResultadoConsultaAdn consultar(EmpresaImportacao empresa, String nsu) throws Exception {
        ResultadoConsultaAdn ultimoResultado = null;
        Exception ultimaExcecao = null;
        for (int tentativa = 1; tentativa <= tentativas; tentativa++) {
            if (tentativa > 1) {
                espera.pausar(intervaloBase.multipliedBy(tentativa - 1L));
            }
            try {
                ultimoResultado = consulta.consultar(empresa, nsu);
                ultimaExcecao = null;
                if (!deveRetentar(ultimoResultado)) {
                    return ultimoResultado;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (Exception exception) {
                if (!deveRetentar(exception)) {
                    throw exception;
                }
                ultimaExcecao = exception;
            }
        }
        if (ultimoResultado != null) {
            if (respostaDfeInvalida(ultimoResultado)) {
                return new ResultadoConsultaAdn(0, "erro-resposta-invalida-DFe", ultimoResultado.corpo());
            }
            return ultimoResultado;
        }
        if (ultimaExcecao != null) {
            return new ResultadoConsultaAdn(0, "erro-rede-" + ultimaExcecao.getClass().getSimpleName(), new byte[0]);
        }
        return new ResultadoConsultaAdn(0, "erro-rede-sem-resultado", new byte[0]);
    }

    private static boolean deveRetentar(ResultadoConsultaAdn resultado) {
        int status = resultado.statusCode();
        if (status == 0 || status == 408 || status == 425 || status == 429 || status >= 500) {
            return true;
        }
        return respostaDfeInvalida(resultado);
    }

    private static boolean deveRetentar(Exception exception) {
        if (exception instanceof SSLException) {
            return false;
        }
        return exception instanceof HttpTimeoutException || exception instanceof IOException;
    }

    private static boolean respostaDfeInvalida(ResultadoConsultaAdn resultado) {
        if (resultado.statusCode() != 200) {
            return false;
        }
        String contentType = resultado.contentType();
        return resultado.bytes() == 0 || contentType == null || !contentType.toLowerCase().contains("json");
    }

    @FunctionalInterface
    interface EsperaRetentativa {
        void pausar(Duration duracao) throws InterruptedException;
    }
}
