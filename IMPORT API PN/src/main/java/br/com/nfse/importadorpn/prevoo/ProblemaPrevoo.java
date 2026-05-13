package br.com.nfse.importadorpn.prevoo;

public record ProblemaPrevoo(
        NivelPrevoo nivel,
        String empresa,
        String mensagem,
        String ondeCorrigir,
        String acao
) {
}
