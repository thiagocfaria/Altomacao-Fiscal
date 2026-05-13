package br.com.nfse.importadorpn.portal;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ClienteAdn {
    private final AmbienteAdn ambiente;
    private final PoliticaSomenteLeitura politica;

    public ClienteAdn(AmbienteAdn ambiente, PoliticaSomenteLeitura politica) {
        this.ambiente = Objects.requireNonNull(ambiente, "ambiente");
        this.politica = Objects.requireNonNull(politica, "politica");
    }

    public HttpRequest consultarDfePorNsu(String nsu, String cnpjConsulta) {
        String caminho = "/DFe/" + codificar(nsu) + "?cnpj=" + codificar(cnpjConsulta);
        return politica.criarGet(resolver(caminho));
    }

    public HttpRequest consultarEventosPorChave(String chaveAcesso) {
        String caminho = "/NFSe/" + codificar(chaveAcesso) + "/Eventos";
        return politica.criarGet(resolver(caminho));
    }

    public HttpRequest consultarDanfsePorChave(String chaveAcesso) {
        String caminho = "/danfse/" + codificar(chaveAcesso);
        return politica.criarGet(resolverDanfse(caminho));
    }

    private URI resolver(String caminho) {
        return URI.create(ambiente.baseUri().toString() + caminho);
    }

    private URI resolverDanfse(String caminho) {
        return URI.create(ambiente.baseDanfseUri().toString() + caminho);
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(Objects.requireNonNull(valor, "valor"), StandardCharsets.UTF_8);
    }
}
