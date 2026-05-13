package br.com.nfse.importadorpn.publicacao;

import java.nio.file.Path;

public record RestEntradaPublicada(Path caminho, int bytes, boolean jaExistia) {
}
