package br.com.nfse.importadorpn.publicacao;

import java.nio.file.Path;

public record DmsDiretoPublicado(Path caminho, int bytes, boolean jaExistia) {
}
