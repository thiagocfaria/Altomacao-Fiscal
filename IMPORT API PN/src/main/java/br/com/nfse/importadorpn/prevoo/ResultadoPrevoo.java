package br.com.nfse.importadorpn.prevoo;

import java.util.ArrayList;
import java.util.List;

public record ResultadoPrevoo(NivelPrevoo nivel, List<ProblemaPrevoo> problemas, List<String> informacoes) {
    public ResultadoPrevoo {
        problemas = List.copyOf(problemas);
        informacoes = List.copyOf(informacoes);
    }

    public static ResultadoPrevoo ok(List<String> informacoes) {
        return new ResultadoPrevoo(NivelPrevoo.OK, List.of(), informacoes);
    }

    public boolean podeLigar() {
        return nivel == NivelPrevoo.OK || nivel == NivelPrevoo.ATENCAO;
    }

    public ResultadoPrevoo combinar(ResultadoPrevoo outro) {
        List<ProblemaPrevoo> novosProblemas = new ArrayList<>(problemas);
        novosProblemas.addAll(outro.problemas);
        List<String> novasInformacoes = new ArrayList<>(informacoes);
        novasInformacoes.addAll(outro.informacoes);
        return new ResultadoPrevoo(pior(nivel, outro.nivel), novosProblemas, novasInformacoes);
    }

    private static NivelPrevoo pior(NivelPrevoo a, NivelPrevoo b) {
        return peso(b) > peso(a) ? b : a;
    }

    private static int peso(NivelPrevoo nivel) {
        return switch (nivel) {
            case OK -> 0;
            case ATENCAO -> 1;
            case BLOQUEADO -> 2;
            case ERRO_EXTERNO -> 3;
        };
    }
}
