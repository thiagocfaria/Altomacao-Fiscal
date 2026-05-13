package br.com.nfse.importadorpn.portal;

import java.util.ArrayList;
import java.util.List;

public record FaixaNsu(long inicio, long fim) {
    private static final long LIMITE_MAXIMO = 50;

    public FaixaNsu {
        if (inicio < 0 || fim < 0) {
            throw new IllegalArgumentException("inicio e fim devem ser maiores ou iguais a zero");
        }
        if (fim < inicio) {
            throw new IllegalArgumentException("fim deve ser maior ou igual ao inicio");
        }
        if ((fim - inicio + 1) > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("faixa excede limite maximo de " + LIMITE_MAXIMO + " NSUs por rodada");
        }
    }

    public List<Long> valores() {
        List<Long> valores = new ArrayList<>();
        for (long atual = inicio; atual <= fim; atual++) {
            valores.add(atual);
        }
        return List.copyOf(valores);
    }
}
