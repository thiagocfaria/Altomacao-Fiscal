package br.com.nfse.importadorpn.portal;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.net.ssl.SSLContext;

public final class ExecutorConsultaAdn {
    public ResultadoConsultaAdn executar(HttpRequest request, SSLContext sslContext) throws Exception {
        if (!"GET".equals(request.method())) {
            throw new OperacaoFiscalBloqueadaException(
                    "Operacao fiscal bloqueada em MODO_SOMENTE_LEITURA: metodo " + request.method());
        }
        HttpClient client = HttpClient.newBuilder()
                .sslContext(sslContext)
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String contentType = response.headers().firstValue("content-type").orElse("NAO_INFORMADO");
        return new ResultadoConsultaAdn(response.statusCode(), contentType, response.body());
    }
}
