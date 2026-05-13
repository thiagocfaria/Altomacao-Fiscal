package br.com.nfse.importadorpn.portal;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Locale;

public final class PoliticaSomenteLeitura {
    private static final Duration TIMEOUT_PADRAO = Duration.ofSeconds(30);
    private final Duration timeout;

    public PoliticaSomenteLeitura() {
        this(TIMEOUT_PADRAO);
    }

    public PoliticaSomenteLeitura(Duration timeout) {
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? TIMEOUT_PADRAO
                : timeout;
    }

    public HttpRequest criarGet(URI uri) {
        validarMetodo("GET");
        return HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .GET()
                .header("Accept", "application/json, application/xml, text/xml, application/pdf, */*")
                .build();
    }

    public void validarMetodo(String metodo) {
        String normalizado = metodo == null ? "" : metodo.trim().toUpperCase(Locale.ROOT);
        if (!"GET".equals(normalizado)) {
            throw new OperacaoFiscalBloqueadaException(
                    "Operacao fiscal bloqueada em MODO_SOMENTE_LEITURA: metodo " + normalizado);
        }
    }
}
