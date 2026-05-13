package br.com.nfse.importadorpn.portal;

import java.net.URI;

public enum AmbienteAdn {
    PRODUCAO_RESTRITA(
            "https://adn.producaorestrita.nfse.gov.br/contribuintes",
            "https://adn.producaorestrita.nfse.gov.br"),
    PRODUCAO(
            "https://adn.nfse.gov.br/contribuintes",
            "https://adn.nfse.gov.br");

    private final URI baseUri;
    private final URI baseDanfseUri;

    AmbienteAdn(String baseUri, String baseDanfseUri) {
        this.baseUri = URI.create(baseUri);
        this.baseDanfseUri = URI.create(baseDanfseUri);
    }

    public URI baseUri() {
        return baseUri;
    }

    public URI baseDanfseUri() {
        return baseDanfseUri;
    }
}
