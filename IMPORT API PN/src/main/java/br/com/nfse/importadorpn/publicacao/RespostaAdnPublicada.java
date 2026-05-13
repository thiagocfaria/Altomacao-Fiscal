package br.com.nfse.importadorpn.publicacao;

import java.nio.file.Path;

public record RespostaAdnPublicada(Path caminho, int bytes) {
}
