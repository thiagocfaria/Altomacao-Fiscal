package br.com.nfse.importadorpn.portal;

import java.net.URI;

public record ConsultaAdnPlanejada(String empresa, String cnpj, String metodo, URI uri) {
}
